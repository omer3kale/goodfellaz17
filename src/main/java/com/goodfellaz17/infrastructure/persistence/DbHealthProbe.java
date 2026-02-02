package com.goodfellaz17.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Database Health Probe - Scheduled connectivity check.
 *
 * Executes SELECT 1 every 30 seconds to monitor database availability
 * and latency. Tracks consecutive failures for circuit-breaker logic.
 *
 * Thresholds are configurable via:
 * - goodfellaz17.db.health.latency-warning-threshold-ms (default: 500)
 * - goodfellaz17.db.health.max-consecutive-failures (default: 3)
 */
@Component
public class DbHealthProbe {

    private static final Logger log = LoggerFactory.getLogger(DbHealthProbe.class);

    private final DatabaseClient databaseClient;

    @Value("${goodfellaz17.db.health.latency-warning-threshold-ms:500}")
    private long latencyWarningThresholdMs;

    @Value("${goodfellaz17.db.health.max-consecutive-failures:3}")
    private int maxConsecutiveFailures;

    // Thread-safe state
    private final AtomicLong lastLatencyMs = new AtomicLong(-1);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<Instant> lastCheckTime = new AtomicReference<>(Instant.now());
    private final AtomicReference<String> lastError = new AtomicReference<>(null);

    public DbHealthProbe(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    /**
     * Scheduled health check - runs every 30 seconds.
     * Executes a simple SELECT 1 query and measures latency.
     */
    @Scheduled(fixedDelay = 30000)
    public void probe() {
        Instant start = Instant.now();
        lastCheckTime.set(start);

        try {
            databaseClient.sql("SELECT 1 AS health_check")
                    .fetch()
                    .first()
                    .block(Duration.ofSeconds(5));

            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            lastLatencyMs.set(latencyMs);
            consecutiveFailures.set(0);
            lastError.set(null);

            if (latencyMs > latencyWarningThresholdMs) {
                log.warn("DB health check SLOW: {}ms (threshold: {}ms)",
                        latencyMs, latencyWarningThresholdMs);
            } else {
                log.info("DB health check OK: {}ms", latencyMs);
            }

        } catch (Exception e) {
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            lastLatencyMs.set(latencyMs);
            int failures = consecutiveFailures.incrementAndGet();
            lastError.set(e.getMessage());

            log.error("DB health check FAILED: {} (consecutive failures: {}, latency: {}ms)",
                    e.getMessage(), failures, latencyMs);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Accessors for DbHealthIndicator
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Returns the last measured latency in milliseconds.
     * Returns -1 if no check has been performed yet.
     */
    public long getLastLatencyMs() {
        return lastLatencyMs.get();
    }

    /**
     * Returns the count of consecutive failures.
     * Resets to 0 on successful probe.
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * Returns the timestamp of the last health check.
     */
    public Instant getLastCheckTime() {
        return lastCheckTime.get();
    }

    /**
     * Returns the last error message, or null if last check succeeded.
     */
    public String getLastError() {
        return lastError.get();
    }

    /**
     * Returns the configured latency warning threshold.
     */
    public long getLatencyWarningThresholdMs() {
        return latencyWarningThresholdMs;
    }

    /**
     * Returns the configured max consecutive failures threshold.
     */
    public int getMaxConsecutiveFailures() {
        return maxConsecutiveFailures;
    }
}
