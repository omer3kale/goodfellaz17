package com.goodfellaz17.order.service;

import com.goodfellaz17.order.domain.OrderTask;
import com.goodfellaz17.order.repository.PlayOrderTaskRepository;
import com.goodfellaz17.order.config.TaskExecutorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background executor that polls the database for PENDING tasks and executes them
 * automatically using TaskExecutionService. Runs on a configurable schedule.
 *
 * Invariants preserved:
 * - INV-2: Task state transitions enforced by TaskExecutionService
 * - INV-4: startedAt and assignedProxyNode set by TaskExecutionService
 * - INV-5: completedAt set by TaskExecutionService
 * - INV-6: Single-threaded polling prevents duplicate execution
 */
@Service
@ConditionalOnProperty(
    name = "app.task-executor.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class TaskExecutorWorker {

    private final PlayOrderTaskRepository taskRepository;
    private final TaskExecutionService taskExecutionService;
    private final int pollIntervalMs;
    private final int maxConcurrentTasks;

    // Metrics (for observability)
    private final AtomicInteger tasksPolled = new AtomicInteger(0);
    private final AtomicInteger tasksExecuted = new AtomicInteger(0);
    private final AtomicInteger tasksFailed = new AtomicInteger(0);

    public TaskExecutorWorker(
        PlayOrderTaskRepository taskRepository,
        TaskExecutionService taskExecutionService,
        TaskExecutorProperties config
    ) {
        this.taskRepository = taskRepository;
        this.taskExecutionService = taskExecutionService;
        this.pollIntervalMs = config.getPollIntervalMs();
        this.maxConcurrentTasks = config.getMaxConcurrentTasks();
    }

    /**
     * Main poll-and-execute loop. Runs on schedule defined by
     * app.task-executor.poll-interval-ms (default 100ms).
     *
     * Single-threaded: each invocation is atomic (no concurrent polls).
     * Execution is reactive and non-blocking within the poll cycle.
     */
    @Scheduled(fixedDelayString = "${app.task-executor.poll-interval-ms:100}")
    public void pollAndExecuteTasks() {
        taskRepository
            .findPendingTasks(maxConcurrentTasks)
            .collectList()
            .flatMapMany(tasks -> {
                if (tasks.isEmpty()) {
                    return Mono.empty();
                }

                tasksPolled.addAndGet(tasks.size());

                // Execute all found tasks in parallel (non-blocking Reactor)
                return Flux.fromIterable(tasks)
                    .flatMap(this::executeTaskAndTrackMetrics);
            })
            .onErrorResume(error -> {
                // Log error, don't crash the scheduler
                System.err.println("TaskExecutorWorker error: " + error.getMessage());
                error.printStackTrace();
                return Mono.empty();
            })
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    /**
     * Execute a single task via TaskExecutionService, tracking metrics.
     */
    private Mono<OrderTask> executeTaskAndTrackMetrics(OrderTask task) {
        return taskExecutionService
            .executeTask(task)
            .doOnNext(result -> {
                tasksExecuted.incrementAndGet();
                System.out.println("TaskExecutor: Completed task " + task.getId());
            })
            .doOnError(error -> {
                tasksFailed.incrementAndGet();
                System.err.println("TaskExecutor: Task " + task.getId() + " failed: " + error.getMessage());
            });
    }

    // Metrics accessors (for testing and observability)
    public int getTasksPolled() {
        return tasksPolled.get();
    }

    public int getTasksExecuted() {
        return tasksExecuted.get();
    }

    public int getTasksFailed() {
        return tasksFailed.get();
    }

    public void resetMetrics() {
        tasksPolled.set(0);
        tasksExecuted.set(0);
        tasksFailed.set(0);
    }
}
