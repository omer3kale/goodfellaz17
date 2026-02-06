package com.goodfellaz17.infrastructure.proxy;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Real-time metrics for a single proxy instance.
 *
 * Thread-safe counters track:
 * - Success/failure rates
 * - Latency percentiles
 * - Ban detection
 * - Cost accumulation
 */
public class ProxyMetrics {

    private final String proxyId;
    private final ProxyTier tier;
    private final String host;
    private final int port;
    private final String country; // ISO 3166-1 alpha-2 country code (e.g., "US", "GB")

    // Request counters (thread-safe)
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder successfulRequests = new LongAdder();
    private final LongAdder failedRequests = new LongAdder();
    private final LongAdder bannedRequests = new LongAdder();
    private final LongAdder timeoutRequests = new LongAdder();

    // Latency tracking (moving average)
    private final AtomicLong totalLatencyMs = new AtomicLong(0);
    private final AtomicLong minLatencyMs = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxLatencyMs = new AtomicLong(0);

    // Cost tracking
    private final LongAdder totalBytesTransferred = new LongAdder();
    private final LongAdder totalCostMicros = new LongAdder(); // Cost in microdollars

    // State
    private volatile Instant lastUsed = null;
    private volatile Instant lastSuccess = null;
    private volatile Instant lastFailure = null;
    private volatile Instant createdAt = Instant.now();
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile boolean circuitOpen = false;
    private volatile Instant circuitOpenedAt = null;

    // Error codes tracking
    private final LongAdder error401Count = new LongAdder(); // Unauthorized
    private final LongAdder error403Count = new LongAdder(); // Forbidden
    private final LongAdder error429Count = new LongAdder(); // Rate limited
    private final LongAdder error5xxCount = new LongAdder(); // Server errors

    public ProxyMetrics(String proxyId, ProxyTier tier, String host, int port) {
        this(proxyId, tier, host, port, null);
    }

    public ProxyMetrics(String proxyId, ProxyTier tier, String host, int port, String country) {
        this.proxyId = proxyId;
        this.tier = tier;
        this.host = host;
        this.port = port;
        this.country = country;
    }

    // === Recording Methods ===

    public void recordSuccess(long latencyMs, long bytesTransferred) {
        totalRequests.increment();
        successfulRequests.increment();
        recordLatency(latencyMs);
        recordBytes(bytesTransferred);
        lastUsed = Instant.now();
        lastSuccess = Instant.now();
        consecutiveFailures.set(0);

        // Auto-close circuit on success
        if (circuitOpen && consecutiveFailures.get() == 0) {
            circuitOpen = false;
            circuitOpenedAt = null;
        }
    }

    public void recordFailure(long latencyMs, int errorCode) {
        totalRequests.increment();
        failedRequests.increment();
        recordLatency(latencyMs);
        lastUsed = Instant.now();
        lastFailure = Instant.now();

        int failures = consecutiveFailures.incrementAndGet();

        // Track error codes
        switch (errorCode) {
            case 401 -> error401Count.increment();
            case 403 -> error403Count.increment();
            case 429 -> error429Count.increment();
            default -> { if (errorCode >= 500) error5xxCount.increment(); }
        }

        // Open circuit after 5 consecutive failures
        if (failures >= 5 && !circuitOpen) {
            circuitOpen = true;
            circuitOpenedAt = Instant.now();
        }
    }

    public void recordBan() {
        bannedRequests.increment();
        failedRequests.increment();
        totalRequests.increment();
        consecutiveFailures.incrementAndGet();
        lastFailure = Instant.now();

        // Immediate circuit open on ban
        circuitOpen = true;
        circuitOpenedAt = Instant.now();
    }

    public void recordTimeout(long timeoutMs) {
        timeoutRequests.increment();
        failedRequests.increment();
        totalRequests.increment();
        recordLatency(timeoutMs);
        consecutiveFailures.incrementAndGet();
        lastFailure = Instant.now();
    }

    private void recordLatency(long latencyMs) {
        totalLatencyMs.addAndGet(latencyMs);
        minLatencyMs.updateAndGet(current -> Math.min(current, latencyMs));
        maxLatencyMs.updateAndGet(current -> Math.max(current, latencyMs));
    }

    private void recordBytes(long bytes) {
        totalBytesTransferred.add(bytes);
        // Calculate cost based on tier
        long costMicros = (long) (bytes * tier.getCostPerGb() / 1_000_000_000.0 * 1_000_000);
        totalCostMicros.add(costMicros);
    }

    // === Computed Metrics ===

    public double getSuccessRate() {
        long total = totalRequests.sum();
        if (total == 0) return 1.0; // No data = assume good
        return (double) successfulRequests.sum() / total;
    }

    public double getFailureRate() {
        return 1.0 - getSuccessRate();
    }

    public double getBanRate() {
        long total = totalRequests.sum();
        if (total == 0) return 0.0;
        return (double) bannedRequests.sum() / total;
    }

    public double getAverageLatencyMs() {
        long total = totalRequests.sum();
        if (total == 0) return 0.0;
        return (double) totalLatencyMs.get() / total;
    }

    public double getTotalCostUsd() {
        return totalCostMicros.sum() / 1_000_000.0;
    }

    public long getTotalBytes() {
        return totalBytesTransferred.sum();
    }

    /**
     * Calculate health score (0.0-1.0).
     * Combines success rate, ban rate, latency, and recency.
     */
    public double getHealthScore() {
        if (circuitOpen) return 0.0;

        double successWeight = 0.5;
        double banPenalty = 0.3;
        double latencyWeight = 0.1;
        double recencyWeight = 0.1;

        double successScore = getSuccessRate();
        double banScore = 1.0 - (getBanRate() * 3); // Heavy penalty for bans
        banScore = Math.max(0, banScore);

        // Latency score (100ms = perfect, 5000ms = bad)
        double avgLatency = getAverageLatencyMs();
        double latencyScore = avgLatency > 0 ? Math.max(0, 1.0 - (avgLatency - 100) / 5000) : 1.0;

        // Recency score (used recently = good)
        double recencyScore = 1.0;
        if (lastSuccess != null) {
            long hoursSinceSuccess = Duration.between(lastSuccess, Instant.now()).toHours();
            recencyScore = Math.max(0, 1.0 - (hoursSinceSuccess / 24.0));
        }

        return (successScore * successWeight) +
               (banScore * banPenalty) +
               (latencyScore * latencyWeight) +
               (recencyScore * recencyWeight);
    }

    /**
     * Check if proxy should be used (circuit closed, healthy).
     */
    public boolean isAvailable() {
        if (circuitOpen) {
            // Allow retry after 5 minutes
            if (circuitOpenedAt != null &&
                Duration.between(circuitOpenedAt, Instant.now()).toMinutes() >= 5) {
                return true; // Half-open state
            }
            return false;
        }
        return getHealthScore() > 0.3; // Minimum health threshold
    }

    // === Getters ===

    public String getProxyId() { return proxyId; }
    public ProxyTier getTier() { return tier; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getCountry() { return country; }
    public long getTotalRequests() { return totalRequests.sum(); }
    public long getSuccessfulRequests() { return successfulRequests.sum(); }
    public long getFailedRequests() { return failedRequests.sum(); }
    public long getBannedRequests() { return bannedRequests.sum(); }
    public boolean isCircuitOpen() { return circuitOpen; }
    public int getConsecutiveFailures() { return consecutiveFailures.get(); }
    public Instant getLastUsed() { return lastUsed; }
    public Instant getLastSuccess() { return lastSuccess; }
    public Instant getLastFailure() { return lastFailure; }

    /**
     * Reset circuit breaker (manual intervention).
     */
    public void resetCircuit() {
        circuitOpen = false;
        circuitOpenedAt = null;
        consecutiveFailures.set(0);
    }

    @Override
    public String toString() {
        return String.format("ProxyMetrics[%s:%d tier=%s health=%.2f success=%.2f%% requests=%d circuit=%s]",
            host, port, tier, getHealthScore(), getSuccessRate() * 100,
            totalRequests.sum(), circuitOpen ? "OPEN" : "CLOSED");
    }
}
