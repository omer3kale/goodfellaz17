package com.goodfellaz17.application.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * DeliveryMetrics - Micrometer metrics for 15k order delivery system.
 *
 * COUNTERS (monotonically increasing):
 *   - delivery_tasks_total{status} - Total tasks by final status
 *   - delivery_retries_total - Total retry attempts
 *   - delivery_orphans_recovered_total - Orphan recoveries
 *   - delivery_plays_total{outcome} - Plays by outcome (delivered/failed)
 *
 * GAUGES (current state):
 *   - delivery_tasks_pending - Current pending tasks
 *   - delivery_tasks_executing - Currently executing tasks
 *   - delivery_dead_letter_queue - Tasks in dead letter queue
 *   - delivery_worker_active - Is worker running (1/0)
 *
 * TIMERS:
 *   - delivery_task_duration - Time to complete a task
 *   - delivery_batch_duration - Time to process a batch
 *
 * @author goodfellaz17
 * @since 1.0.0
 */
@Component
public class DeliveryMetrics_Legacy {

    private final MeterRegistry registry;

    // Gauges - current state
    private final AtomicInteger pendingTasks = new AtomicInteger(0);
    private final AtomicInteger executingTasks = new AtomicInteger(0);
    private final AtomicInteger deadLetterCount = new AtomicInteger(0);
    private final AtomicInteger workerActive = new AtomicInteger(0);

    // Counters
    private final Counter tasksCompleted;
    private final Counter tasksFailed;
    private final Counter tasksRetried;
    private final Counter orphansRecovered;
    private final Counter playsDelivered;
    private final Counter playsFailed;

    // Timers
    private final Timer taskDuration;
    private final Timer batchDuration;

    public DeliveryMetrics_Legacy(MeterRegistry registry) {
        this.registry = registry;

        // Register gauges
        Gauge.builder("delivery_tasks_pending", pendingTasks, AtomicInteger::get)
            .description("Number of tasks waiting to be executed")
            .register(registry);

        Gauge.builder("delivery_tasks_executing", executingTasks, AtomicInteger::get)
            .description("Number of tasks currently being executed")
            .register(registry);

        Gauge.builder("delivery_dead_letter_queue", deadLetterCount, AtomicInteger::get)
            .description("Number of permanently failed tasks")
            .register(registry);

        Gauge.builder("delivery_worker_active", workerActive, AtomicInteger::get)
            .description("Whether the delivery worker is active (1) or not (0)")
            .register(registry);

        // Register counters
        tasksCompleted = Counter.builder("delivery_tasks_total")
            .tag("status", "completed")
            .description("Total tasks completed successfully")
            .register(registry);

        tasksFailed = Counter.builder("delivery_tasks_total")
            .tag("status", "failed_permanent")
            .description("Total tasks failed permanently")
            .register(registry);

        tasksRetried = Counter.builder("delivery_retries_total")
            .description("Total retry attempts")
            .register(registry);

        orphansRecovered = Counter.builder("delivery_orphans_recovered_total")
            .description("Total orphaned tasks recovered")
            .register(registry);

        playsDelivered = Counter.builder("delivery_plays_total")
            .tag("outcome", "delivered")
            .description("Total plays delivered successfully")
            .register(registry);

        playsFailed = Counter.builder("delivery_plays_total")
            .tag("outcome", "failed")
            .description("Total plays that failed permanently")
            .register(registry);

        // Register timers
        taskDuration = Timer.builder("delivery_task_duration")
            .description("Time to complete a single task")
            .register(registry);

        batchDuration = Timer.builder("delivery_batch_duration")
            .description("Time to process a batch of tasks")
            .register(registry);
    }

    // =========================================================================
    // GAUGE UPDATES
    // =========================================================================

    public void setPendingTasks(int count) {
        pendingTasks.set(count);
    }

    public void setExecutingTasks(int count) {
        executingTasks.set(count);
    }

    public void setDeadLetterCount(int count) {
        deadLetterCount.set(count);
    }

    public void setWorkerActive(boolean active) {
        workerActive.set(active ? 1 : 0);
    }

    // =========================================================================
    // COUNTER INCREMENTS
    // =========================================================================

    public void incrementTasksCompleted() {
        tasksCompleted.increment();
    }

    public void incrementTasksFailed() {
        tasksFailed.increment();
    }

    public void incrementRetries() {
        tasksRetried.increment();
    }

    public void incrementOrphansRecovered() {
        orphansRecovered.increment();
    }

    public void incrementPlaysDelivered(int quantity) {
        playsDelivered.increment(quantity);
    }

    public void incrementPlaysFailed(int quantity) {
        playsFailed.increment(quantity);
    }

    // =========================================================================
    // TIMER RECORDING
    // =========================================================================

    public Timer.Sample startTaskTimer() {
        return Timer.start(registry);
    }

    public void recordTaskDuration(Timer.Sample sample) {
        sample.stop(taskDuration);
    }

    public Timer.Sample startBatchTimer() {
        return Timer.start(registry);
    }

    public void recordBatchDuration(Timer.Sample sample) {
        sample.stop(batchDuration);
    }

    // =========================================================================
    // BULK UPDATES (from worker status)
    // =========================================================================

    public void updateFromWorkerStatus(int pending, int executing, int deadLetter, boolean active) {
        pendingTasks.set(pending);
        executingTasks.set(executing);
        deadLetterCount.set(deadLetter);
        workerActive.set(active ? 1 : 0);
    }
}
