package com.goodfellaz17.application.scheduler;

import com.goodfellaz17.application.proxy.ProxyTaskDispatchService;
import com.goodfellaz17.domain.model.generated.OrderTaskEntity;
import com.goodfellaz17.domain.model.generated.OrderEntity;
import com.goodfellaz17.domain.model.generated.TaskStatus;
import com.goodfellaz17.infrastructure.persistence.generated.OrderTaskRepository;
import com.goodfellaz17.infrastructure.persistence.generated.GeneratedOrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TaskExecutionScheduler - Polls for PENDING/RETRYING tasks and executes them.
 *
 * Path A: Background Executor (unblocks thesis data collection)
 *
 * Workflow:
 * 1. Poll every 100ms (dev) / 5s (prod) for PENDING → EXECUTING → COMPLETED/FAILED
 * 2. Batch pull (default: 10 tasks)
 * 3. Execute concurrently via ProxyTaskDispatchService (default: 5 concurrent)
 * 4. Handle failures with exponential backoff (retry_after)
 * 5. Mark COMPLETED when all tasks done
 *
 * Guarantees:
 * - No duplicate execution (status transitions prevent re-processing)
 * - No task loss (orphaned tasks recovered by scheduler)
 * - Metrics tracked for every execution path
 *
 * @author goodfellaz17
 * @since 1.0.0
 */
@Component
public class TaskExecutionScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionScheduler.class);

    // === Dependencies ===
    private final OrderTaskRepository taskRepository;
    private final GeneratedOrderRepository orderRepository;
    private final ProxyTaskDispatchService proxyDispatch;
    private final MeterRegistry meterRegistry;

    // === Configuration ===
    @Value("${app.scheduler.poll-ms:100}")
    private long pollIntervalMs;

    @Value("${app.scheduler.batch-size:10}")
    private int batchSize;

    @Value("${app.scheduler.concurrency:5}")
    private int maxConcurrency;

    @Value("${app.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    // === Metrics ===
    private final Counter tasksPolled;
    private final Counter tasksExecuted;
    private final Counter tasksFailed;
    private final Counter tasksCompleted;
    private final Counter ordersCompleted;

    public TaskExecutionScheduler(
            OrderTaskRepository taskRepository,
            GeneratedOrderRepository orderRepository,
            ProxyTaskDispatchService proxyDispatch,
            MeterRegistry meterRegistry) {
        this.taskRepository = taskRepository;
        this.orderRepository = orderRepository;
        this.proxyDispatch = proxyDispatch;
        this.meterRegistry = meterRegistry;

        // Initialize metrics
        this.tasksPolled = Counter.builder("scheduler.tasks.polled")
            .description("Total tasks polled from queue")
            .register(meterRegistry);
        this.tasksExecuted = Counter.builder("scheduler.tasks.executed")
            .description("Tasks executed successfully")
            .register(meterRegistry);
        this.tasksFailed = Counter.builder("scheduler.tasks.failed")
            .description("Tasks failed after retries")
            .register(meterRegistry);
        this.tasksCompleted = Counter.builder("scheduler.tasks.completed")
            .description("Total completed tasks")
            .register(meterRegistry);
        this.ordersCompleted = Counter.builder("scheduler.orders.completed")
            .description("Total completed orders")
            .register(meterRegistry);

        log.info("TaskExecutionScheduler initialized | pollMs={} | batchSize={} | concurrency={}",
            pollIntervalMs, batchSize, maxConcurrency);
    }

    /**
     * Main scheduler loop - runs at configurable interval.
     * Poll and execute PENDING/FAILED_RETRYING tasks.
     */
    @Scheduled(fixedDelayString = "${app.scheduler.poll-ms:100}")
    public void pollAndExecutePendingTasks() {
        if (!schedulerEnabled) {
            return;
        }

        Instant now = Instant.now();
        Instant orphanThreshold = now.minus(Duration.ofSeconds(30));

        // Find all ready tasks (PENDING, FAILED_RETRYING, orphaned EXECUTING)
        taskRepository.findTasksReadyForWorker(now, orphanThreshold, batchSize)
            .doOnNext(task -> {
                tasksPolled.increment();
                log.debug("Task polled | taskId={} | status={}", task.getId(), task.getStatus());
            })
            .publishOn(Schedulers.boundedElastic())
            .flatMap(this::executeTaskWithRetry, maxConcurrency)
            .onErrorContinue((error, obj) -> {
                log.error("Error during task execution: {}", error.getMessage(), error);
                tasksFailed.increment();
            })
            .subscribe(
                result -> {
                    tasksExecuted.increment();
                    tasksCompleted.increment();
                    log.info("Task execution result | taskId={} | success={}",
                        result.taskId(), result.success());
                },
                error -> log.error("Scheduler error: {}", error.getMessage(), error),
                () -> log.debug("Scheduler cycle complete")
            );
    }

    /**
     * Execute a single task with automatic retry and fallback.
     *
     * Flow:
     * 1. Claim task (mark EXECUTING, set execution_started_at)
     * 2. Dispatch to proxy machine
     * 3. Mark COMPLETED or FAILED_RETRYING based on result
     * 4. Check if order is complete (all tasks COMPLETED)
     */
    private reactor.core.publisher.Mono<TaskExecutionResult> executeTaskWithRetry(OrderTaskEntity task) {
        return claimTask(task)
            .flatMap(claimed -> {
                log.debug("Task claimed | taskId={} | orderId={}", claimed.getId(), claimed.getOrderId());

                return orderRepository.findById(claimed.getOrderId())
                    .flatMap(order -> dispatchAndUpdateTask(claimed, order))
                    .doOnError(e -> {
                        log.error("Task execution failed | taskId={} | error={}", task.getId(), e.getMessage());
                        tasksFailed.increment();
                    });
            })
            .retryWhen(Retry.backoff(2, Duration.ofSeconds(1)))
            .onErrorResume(e -> {
                log.error("Task failed permanently after retries | taskId={}", task.getId(), e);
                return markTaskFailed(task, "Max retries exceeded: " + e.getMessage());
            });
    }

    /**
     * Claim a task for execution - mark as EXECUTING.
     * Optimistic locking prevents duplicate execution.
     */
    private reactor.core.publisher.Mono<OrderTaskEntity> claimTask(OrderTaskEntity task) {
        OrderTaskEntity executing = new OrderTaskEntity();
        executing.setId(task.getId());
        executing.setStatus(TaskStatus.EXECUTING.name());
        executing.setExecutionStartedAt(Instant.now());
        executing.setAttempts(task.getAttempts() + 1);

        return taskRepository.save(executing);
    }

    /**
     * Dispatch task to proxy machine and update status.
     */
    private reactor.core.publisher.Mono<TaskExecutionResult> dispatchAndUpdateTask(
            OrderTaskEntity task, OrderEntity order) {

        // Dispatch to proxy machine (or mock in dev mode)
        return proxyDispatch.dispatchTask(task, order.getTargetUrl())
            .flatMap(dispatchResult -> {
                if (dispatchResult.success()) {
                    // Mark task COMPLETED
                    log.info("Task completed | taskId={} | proxyId={}",
                        task.getId(), dispatchResult.proxy().getId());

                    return markTaskCompleted(task)
                        .then(checkOrderCompletion(order))
                        .map(orderComplete -> new TaskExecutionResult(
                            task.getId(), true, "Task completed successfully"
                        ));
                } else {
                    // Mark task FAILED_RETRYING with backoff
                    log.warn("Task dispatch failed | taskId={} | error={}",
                        task.getId(), dispatchResult.errorMessage());

                    return markTaskForRetry(task, dispatchResult.errorMessage())
                        .map(retryTask -> new TaskExecutionResult(
                            task.getId(), false, dispatchResult.errorMessage()
                        ));
                }
            });
    }

    /**
     * Mark task as COMPLETED.
     */
    private reactor.core.publisher.Mono<OrderTaskEntity> markTaskCompleted(OrderTaskEntity task) {
        OrderTaskEntity completed = new OrderTaskEntity();
        completed.setId(task.getId());
        completed.setStatus(TaskStatus.COMPLETED.name());
        completed.setExecutedAt(Instant.now());

        return taskRepository.save(completed);
    }

    /**
     * Mark task as FAILED_RETRYING with exponential backoff.
     */
    private reactor.core.publisher.Mono<OrderTaskEntity> markTaskForRetry(
            OrderTaskEntity task, String errorMsg) {

        int attempts = task.getAttempts() != null ? task.getAttempts() : 0;
        long backoffSeconds = (long) Math.pow(2, Math.min(attempts, 4)); // 2s, 4s, 8s, 16s cap

        OrderTaskEntity retrying = new OrderTaskEntity();
        retrying.setId(task.getId());
        retrying.setStatus(TaskStatus.FAILED_RETRYING.name());
        retrying.setLastError(errorMsg);
        retrying.setRetryAfter(Instant.now().plus(Duration.ofSeconds(backoffSeconds)));
        retrying.setAttempts(attempts + 1);

        log.info("Task scheduled for retry | taskId={} | backoffSeconds={} | attempts={}",
            task.getId(), backoffSeconds, attempts + 1);

        return taskRepository.save(retrying);
    }

    /**
     * Mark task as FAILED_PERMANENT after max retries.
     */
    private reactor.core.publisher.Mono<TaskExecutionResult> markTaskFailed(
            OrderTaskEntity task, String reason) {

        OrderTaskEntity failed = new OrderTaskEntity();
        failed.setId(task.getId());
        failed.setStatus(TaskStatus.FAILED_PERMANENT.name());
        failed.setLastError(reason);

        log.error("Task marked FAILED_PERMANENT | taskId={} | reason={}", task.getId(), reason);

        return taskRepository.save(failed)
            .map(savedTask -> new TaskExecutionResult(task.getId(), false, reason));
    }

    /**
     * Check if order is complete (all tasks COMPLETED or FAILED_PERMANENT).
     * If complete, mark order as COMPLETED and increment counter.
     */
    private reactor.core.publisher.Mono<Boolean> checkOrderCompletion(OrderEntity order) {
        return taskRepository.findByOrderId(order.getId())
            .collectList()
            .flatMap(tasks -> {
                boolean allDone = tasks.stream()
                    .allMatch(t -> TaskStatus.COMPLETED.name().equals(t.getStatus()) ||
                                   TaskStatus.FAILED_PERMANENT.name().equals(t.getStatus()));

                if (allDone) {
                    log.info("Order complete | orderId={} | totalTasks={}", order.getId(), tasks.size());
                    ordersCompleted.increment();

                    OrderEntity completed = new OrderEntity();
                    completed.setId(order.getId());
                    completed.setStatus("COMPLETED");
                    completed.setCompletedAt(Instant.now());

                    return orderRepository.save(completed)
                        .map(savedOrder -> true);
                }

                return reactor.core.publisher.Mono.just(false);
            });
    }

    /**
     * Result of a single task execution.
     */
    public record TaskExecutionResult(
        UUID taskId,
        boolean success,
        String message
    ) {}
}
