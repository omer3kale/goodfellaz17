package com.goodfellaz17.infrastructure.metrics;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DeliveryMetrics - Prometheus-compatible metrics for 15k order system.
 *
 * Tracks:
 * - orders.total: 15k orders started
 * - orders.completed: Orders reaching COMPLETED status
 * - orders.failed: Orders marked FAILED_PERMANENT
 * - tasks.completed: Individual tasks delivered
 * - tasks.retried: Tasks that entered FAILED_RETRYING
 * - tasks.orphans.recovered: Tasks recovered from orphan state
 * - delivery.latency: End-to-end delivery duration
 * - proxy.health: Current healthy proxy count
 * - worker.status: 0=stopped, 1=running
 *
 * @author goodfellaz17
 * @since 1.0.0
 */
@Component
public class DeliveryMetrics {

    private static final Logger log = LoggerFactory.getLogger(DeliveryMetrics.class);

    // ==========================================================================
    // COUNTERS
    // ==========================================================================

    private final Counter ordersTotal;
    private final Counter ordersCompleted;
    private final Counter ordersFailed;
    private final Counter tasksCompleted;
    private final Counter tasksRetried;
    private final Counter tasksOrphansRecovered;
    private final Counter proxyBanEvents;

    // ==========================================================================
    // TIMERS
    // ==========================================================================

    private final Timer deliveryLatency;
    private final Timer taskExecutionTime;

    // ==========================================================================
    // GAUGES
    // ==========================================================================

    private final AtomicInteger proxyHealthyCount;
    private final AtomicInteger workerStatus; // 0=stopped, 1=running

    // ==========================================================================
    // CONSTRUCTOR
    // ==========================================================================

    public DeliveryMetrics(MeterRegistry registry) {
        // Counters
        this.ordersTotal = Counter.builder("orders.total")
            .description("Total 15k orders created")
            .register(registry);

        this.ordersCompleted = Counter.builder("orders.completed")
            .description("Orders fully delivered (COMPLETED status)")
            .register(registry);

        this.ordersFailed = Counter.builder("orders.failed")
            .description("Orders failed (FAILED_PERMANENT status)")
            .register(registry);

        this.tasksCompleted = Counter.builder("tasks.completed")
            .description("Individual delivery tasks completed")
            .register(registry);

        this.tasksRetried = Counter.builder("tasks.retried")
            .description("Tasks that required retry (entered FAILED_RETRYING)")
            .register(registry);

        this.tasksOrphansRecovered = Counter.builder("tasks.orphans.recovered")
            .description("Orphaned tasks recovered by worker")
            .register(registry);

        this.proxyBanEvents = Counter.builder("proxy.ban.events")
            .description("Proxies banned due to failures")
            .register(registry);

        // Timers
        this.deliveryLatency = Timer.builder("delivery.latency")
            .description("End-to-end order delivery time (order created → completed)")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);

        this.taskExecutionTime = Timer.builder("task.execution.time")
            .description("Individual task execution duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);

        // Gauges (mutable state)
        this.proxyHealthyCount = new AtomicInteger(0);
        Gauge.builder("proxy.healthy", proxyHealthyCount, AtomicInteger::get)
            .description("Current count of healthy proxies")
            .register(registry);

        this.workerStatus = new AtomicInteger(0);
        Gauge.builder("worker.status", workerStatus, AtomicInteger::get)
            .description("Worker thread status (0=stopped, 1=running)")
            .register(registry);

        log.info("DeliveryMetrics initialized with Prometheus registry");
    }

    // ==========================================================================
    // RECORDING METHODS
    // ==========================================================================

    public void recordOrderCreated() {
        ordersTotal.increment();
        log.debug("METRIC: Order created | total={}", ordersTotal.count());
    }

    public void recordOrderCompleted(Duration deliveryDuration) {
        ordersCompleted.increment();
        deliveryLatency.record(deliveryDuration);
        log.info("METRIC: Order completed | duration={}ms", deliveryDuration.toMillis());
    }

    public void recordOrderFailed() {
        ordersFailed.increment();
        log.warn("METRIC: Order failed | total_failed={}", ordersFailed.count());
    }

    public void recordTaskCompleted(Duration executionDuration) {
        tasksCompleted.increment();
        taskExecutionTime.record(executionDuration);
        log.debug("METRIC: Task completed | execution_time={}ms", executionDuration.toMillis());
    }

    public void recordTaskRetried() {
        tasksRetried.increment();
        log.warn("METRIC: Task retried | total_retries={}", tasksRetried.count());
    }

    public void recordOrphanRecovered() {
        tasksOrphansRecovered.increment();
        log.warn("METRIC: Orphan recovered | total_recovered={}", tasksOrphansRecovered.count());
    }

    public void recordProxyBanned() {
        proxyBanEvents.increment();
        log.warn("METRIC: Proxy banned | total_bans={}", proxyBanEvents.count());
    }

    public void setProxyHealthyCount(int count) {
        proxyHealthyCount.set(count);
        log.debug("METRIC: Proxy healthy count = {}", count);
    }

    public int getProxyHealthyCount() {
        return proxyHealthyCount.get();
    }

    public void setWorkerRunning(boolean running) {
        workerStatus.set(running ? 1 : 0);
        log.info("METRIC: Worker status = {}", running ? "RUNNING" : "STOPPED");
    }

    // ==========================================================================
    // QUERY METHODS (for monitoring dashboards)
    // ==========================================================================

    public long getOrdersTotal() {
        return (long) ordersTotal.count();
    }

    public long getOrdersCompleted() {
        return (long) ordersCompleted.count();
    }

    public long getOrdersFailed() {
        return (long) ordersFailed.count();
    }

    public long getTasksCompleted() {
        return (long) tasksCompleted.count();
    }

    public long getTasksRetried() {
        return (long) tasksRetried.count();
    }

    public long getTasksOrphansRecovered() {
        return (long) tasksOrphansRecovered.count();
    }

    public long getProxyBanEvents() {
        return (long) proxyBanEvents.count();
    }

    public double getAvgDeliveryLatencyMs() {
        return deliveryLatency.mean(java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public double getAvgTaskExecutionTimeMs() {
        return taskExecutionTime.mean(java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Calculate success rate as percentage.
     * Success = (ordersCompleted / ordersTotal) * 100
     */
    public double getSuccessRate() {
        long total = getOrdersTotal();
        if (total == 0) return 0.0;
        return (getOrdersCompleted() / (double) total) * 100.0;
    }
}
