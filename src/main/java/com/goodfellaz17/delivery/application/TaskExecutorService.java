package com.goodfellaz17.delivery.application;

import com.goodfellaz17.infrastructure.persistence.entity.ExecutionResultEntity;
import com.goodfellaz17.infrastructure.persistence.entity.OrderEntity;
import com.goodfellaz17.infrastructure.persistence.entity.OrderTaskEntity;
import com.goodfellaz17.infrastructure.persistence.repository.ExecutionResultRepository;
import com.goodfellaz17.infrastructure.persistence.repository.OrderRepository;
import com.goodfellaz17.infrastructure.persistence.repository.OrderTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;

/**
 * TaskExecutorService — Reactive task execution loop for Slice A.
 * 
 * Polls PENDING tasks, executes them (dummy 90/10 success/fail simulation),
 * records ExecutionResults, and updates task + order statuses.
 * 
 * No retries in this version; failed tasks remain terminal.
 */
@Service
public class TaskExecutorService {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutorService.class);
    private static final int BATCH_SIZE = 10;
    private static final Random RANDOM = new Random();
    private static final double SUCCESS_RATE = 0.9;

    private final OrderTaskRepository orderTaskRepository;
    private final ExecutionResultRepository executionResultRepository;
    private final OrderRepository orderRepository;

    public TaskExecutorService(OrderTaskRepository orderTaskRepository,
                               ExecutionResultRepository executionResultRepository,
                               OrderRepository orderRepository) {
        this.orderTaskRepository = orderTaskRepository;
        this.executionResultRepository = executionResultRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * One complete poll-and-execute cycle.
     * Called by @Scheduled; can be unit-tested independently.
     */
    public Mono<Void> runOnce() {
        return orderTaskRepository.findPendingTasks()
                .take(BATCH_SIZE)
                .flatMap(this::executeTask)
                .then()
                .doOnError(e -> log.error("TaskExecutor error", e));
    }

    /**
     * Scheduled entry point: runs every 1.5 seconds.
     * Uses a non-blocking scheduler to avoid blocking the main thread.
     */
    @Scheduled(fixedRate = 1500)
    public void executeScheduled() {
        log.debug("TaskExecutor poll cycle starting");
        runOnce().subscribe(
                unused -> log.debug("TaskExecutor poll cycle complete"),
                e -> log.error("TaskExecutor poll cycle failed", e)
        );
    }

    /**
     * Execute a single task:
     * 1. Mark EXECUTING with startedAt
     * 2. Simulate play (100–500 ms delay, 90% success)
     * 3. Record ExecutionResult
     * 4. Mark COMPLETED/FAILED with completedAt
     * 5. Aggregate order status if all tasks are terminal
     */
    private Mono<Void> executeTask(OrderTaskEntity task) {
        log.info("Executing task: {} for order: {}", task.getId(), task.getOrderId());
        Instant executionStart = Instant.now();

        return markTaskExecuting(task)
                .then(simulateExecution(task))
                .flatMap(executionOutcome -> {
                    Instant executionEnd = Instant.now();
                    long durationMs = Duration.between(executionStart, executionEnd).toMillis();
                    log.debug("Task {} execution outcome: success={}, duration={}ms", 
                            task.getId(), executionOutcome.getSuccessFlag(), durationMs);

                    // Create ExecutionResult
                    ExecutionResultEntity result = new ExecutionResultEntity(
                            UUID.randomUUID(),
                            task.getId(),
                            task.getOrderId(),
                            task.getTenantId(),
                            task.getAccountId(),
                            task.getProxyNodeId(),
                            1, // attemptNum = 1 (no retries yet)
                            executionOutcome.getSuccessFlag(),
                            executionOutcome.getFailureReason(),
                            durationMs
                    );
                    result.setStartedAt(executionStart);
                    result.setCompletedAt(executionEnd);

                    return recordExecutionResult(result)
                            .then(markTaskComplete(task, executionOutcome));
                })
                .then(aggregateOrderStatus(task.getOrderId()))
                .onErrorResume(e -> {
                    log.error("Error executing task: {}", task.getId(), e);
                    return Mono.empty();
                });
    }

    /**
     * Mark task as EXECUTING with startAt timestamp.
     */
    private Mono<Void> markTaskExecuting(OrderTaskEntity task) {
        task.setStatus("EXECUTING");
        task.setStartedAt(Instant.now());
        return orderTaskRepository.save(task)
                .doOnNext(__ -> log.debug("Task {} marked as EXECUTING", task.getId()))
                .then();
    }

    /**
     * Simulate execution: reactive delay + random success/failure.
     * Returns ExecutionOutcome DTO with successFlag and optional failureReason.
     */
    private Mono<ExecutionOutcome> simulateExecution(OrderTaskEntity task) {
        long delayMs = 100 + RANDOM.nextInt(400); // 100–500 ms
        return Mono.delay(Duration.ofMillis(delayMs))
                .map(__ -> {
                    if (RANDOM.nextDouble() < SUCCESS_RATE) {
                        return new ExecutionOutcome(true, null);
                    } else {
                        return new ExecutionOutcome(false, "simulated_failure");
                    }
                });
    }

    /**
     * Persist the ExecutionResult to the database.
     */
    private Mono<Void> recordExecutionResult(ExecutionResultEntity result) {
        return executionResultRepository.save(result)
                .doOnNext(__ -> log.debug("ExecutionResult recorded for task: {}", result.getOrderTaskId()))
                .then();
    }

    /**
     * Mark task as COMPLETED or FAILED with completedAt timestamp and optionally lastError.
     */
    private Mono<Void> markTaskComplete(OrderTaskEntity task, ExecutionOutcome outcome) {
        task.setStatus(outcome.getSuccessFlag() ? "COMPLETED" : "FAILED");
        task.setCompletedAt(Instant.now());
        if (!outcome.getSuccessFlag()) {
            task.setLastError(outcome.getFailureReason());
        }
        return orderTaskRepository.save(task)
                .doOnNext(__ -> log.info("Task {} marked as {}", task.getId(), task.getStatus()))
                .then();
    }

    /**
     * Aggregate order status:
     * 1. If order is PENDING and tasks are executing, move to ACTIVE.
     * 2. If all tasks are terminal (COMPLETED or FAILED), move order to COMPLETED.
     * 3. Aggregate playsDelivered and playsFailed from task counts.
     */
    private Mono<Void> aggregateOrderStatus(UUID orderId) {
        return orderRepository.findById(orderId)
                .flatMap(order -> {
                    // Step 1: Transition PENDING → ACTIVE if not already started
                    if ("PENDING".equals(order.getStatus())) {
                        order.setStatus("ACTIVE");
                        order.setStartedAt(Instant.now());
                        log.info("Order {} transitioned to ACTIVE", orderId);
                        return orderRepository.save(order).then();
                    }
                    return Mono.empty();
                })
                .then(
                    // Step 2: Check if all tasks are terminal
                    orderTaskRepository.countByOrderIdAndStatus(orderId, "COMPLETED")
                            .zipWith(orderTaskRepository.countByOrderIdAndStatus(orderId, "FAILED"))
                            .flatMap(counts -> {
                                long completed = counts.getT1();
                                long failed = counts.getT2();
                                return orderTaskRepository.countByOrderId(orderId)
                                        .flatMap(total -> {
                                            if (completed + failed == total && total > 0) {
                                                // All tasks are terminal
                                                log.info("Order {} all tasks terminal: {} completed, {} failed", orderId, completed, failed);
                                                return orderRepository.findById(orderId)
                                                        .flatMap(order -> {
                                                            order.setStatus("COMPLETED");
                                                            order.setCompletedAt(Instant.now());
                                                            order.setPlaysDelivered(Math.toIntExact(completed));
                                                            order.setPlaysFailed(Math.toIntExact(failed));
                                                            log.info("Order {} marked COMPLETED", orderId);
                                                            return orderRepository.save(order).then();
                                                        });
                                            }
                                            return Mono.empty();
                                        });
                            })
                )
                .onErrorResume(e -> {
                    log.error("Error aggregating order {}", orderId, e);
                    return Mono.empty();
                });
    }

    /**
     * Simple DTO for execution outcome.
     */
    public static class ExecutionOutcome {
        private final Boolean successFlag;
        private final String failureReason;

        public ExecutionOutcome(Boolean successFlag, String failureReason) {
            this.successFlag = successFlag;
            this.failureReason = failureReason;
        }

        public Boolean getSuccessFlag() { return successFlag; }
        public String getFailureReason() { return failureReason; }
    }
}
