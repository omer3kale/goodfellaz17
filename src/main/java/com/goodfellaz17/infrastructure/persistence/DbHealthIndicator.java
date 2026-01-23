package com.goodfellaz17.infrastructure.persistence;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Database Health Indicator - Exposes DB status via /actuator/health.
 * 
 * Status logic:
 * - UP:       latency < 100ms and no consecutive failures
 * - DEGRADED: latency 100-500ms (configurable threshold)
 * - DOWN:     latency > 500ms OR 3+ consecutive failures (configurable)
 * 
 * Details exposed:
 * - latencyMs: Last query latency in milliseconds
 * - consecutiveFailures: Number of failures in a row
 * - lastCheckTime: ISO timestamp of last probe
 * - lastError: Error message if last check failed
 */
@Component("db")
public class DbHealthIndicator implements HealthIndicator {

    private static final Status DEGRADED = new Status("DEGRADED");
    private static final long LATENCY_UP_THRESHOLD_MS = 100;

    private final DbHealthProbe probe;

    public DbHealthIndicator(DbHealthProbe probe) {
        this.probe = probe;
    }

    @Override
    public Health health() {
        long latencyMs = probe.getLastLatencyMs();
        int consecutiveFailures = probe.getConsecutiveFailures();
        Instant lastCheckTime = probe.getLastCheckTime();
        String lastError = probe.getLastError();
        long warningThresholdMs = probe.getLatencyWarningThresholdMs();
        int maxFailures = probe.getMaxConsecutiveFailures();

        // No data yet - first probe hasn't run
        if (latencyMs < 0 || lastCheckTime == null) {
            return Health.unknown()
                    .withDetail("message", "Health probe has not run yet")
                    .build();
        }

        // Determine status
        Status status = determineStatus(latencyMs, consecutiveFailures, warningThresholdMs, maxFailures);

        Health.Builder builder = Health.status(status)
                .withDetail("latencyMs", latencyMs)
                .withDetail("consecutiveFailures", consecutiveFailures)
                .withDetail("lastCheckTime", lastCheckTime.toString())
                .withDetail("thresholds", new ThresholdInfo(
                        LATENCY_UP_THRESHOLD_MS, 
                        warningThresholdMs, 
                        maxFailures
                ));

        if (lastError != null) {
            builder.withDetail("lastError", lastError);
        }

        return builder.build();
    }

    /**
     * Determines health status based on latency and failure count.
     */
    private Status determineStatus(long latencyMs, int consecutiveFailures, 
                                   long warningThresholdMs, int maxFailures) {
        // DOWN: Too many consecutive failures
        if (consecutiveFailures >= maxFailures) {
            return Status.DOWN;
        }

        // DOWN: Latency exceeds warning threshold (500ms default)
        if (latencyMs > warningThresholdMs) {
            return Status.DOWN;
        }

        // DEGRADED: Latency between 100-500ms
        if (latencyMs > LATENCY_UP_THRESHOLD_MS) {
            return DEGRADED;
        }

        // UP: Latency < 100ms and no failures
        return Status.UP;
    }

    /**
     * Threshold configuration info for health response.
     */
    public record ThresholdInfo(
            long upThresholdMs,
            long degradedThresholdMs,
            int maxConsecutiveFailures
    ) {}
}
