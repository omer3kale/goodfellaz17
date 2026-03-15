package com.goodfellaz17.delivery.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * ExecutionResult — outcome of a single task execution attempt.
 * 
 * Maps to MSSQL execution_results table.
 * Enforces INV-8: created_at ≤ completed_at
 * Enforces INV-9/10: success_flag and failure_reason consistency
 * Enforces INV-11: duration_ms ≥ 0
 * 
 * Contains detection signals for FTL-X (abuse pattern detection).
 */
public class ExecutionResult {
    
    private final UUID id;
    private final UUID orderTaskId;
    private final UUID orderId;
    private final UUID tenantId;
    private final UUID accountId;
    private final UUID proxyNodeId;
    
    private int attemptNum;
    
    private boolean successFlag;
    private String failureReason;         // INV-10: null if success, non-null if failed
    private Integer httpStatus;
    private String executingNodeId;
    
    private long durationMs;
    private final Instant startedAt;
    private final Instant completedAt;    // INV-8: Always set
    private final Instant createdAt;
    
    private String detectionSignal;       // JSON flags for FTL-X
    private Double detectionScore;        // 0–100 anomaly score
    
    private BigDecimal revenueImpact;

    /**
     * Factory for a successful result.
     */
    public static ExecutionResult success(UUID id, UUID orderTaskId, UUID orderId, UUID tenantId,
                                          UUID accountId, UUID proxyNodeId,
                                          int attemptNum, long durationMs, String executingNodeId) {
        return new ExecutionResult(
            id, orderTaskId, orderId, tenantId, accountId, proxyNodeId,
            attemptNum, true, null, null, executingNodeId, durationMs,
            Instant.now(), Instant.now()
        );
    }

    /**
     * Factory for a failed result.
     */
    public static ExecutionResult failure(UUID id, UUID orderTaskId, UUID orderId, UUID tenantId,
                                          UUID accountId, UUID proxyNodeId,
                                          int attemptNum, String failureReason,
                                          long durationMs, Integer httpStatus,
                                          String executingNodeId) {
        ExecutionResult result = new ExecutionResult(
            id, orderTaskId, orderId, tenantId, accountId, proxyNodeId,
            attemptNum, false, failureReason, httpStatus, executingNodeId, durationMs,
            Instant.now(), Instant.now()
        );
        return result;
    }

    private ExecutionResult(UUID id, UUID orderTaskId, UUID orderId, UUID tenantId,
                           UUID accountId, UUID proxyNodeId,
                           int attemptNum, boolean successFlag, String failureReason,
                           Integer httpStatus, String executingNodeId,
                           long durationMs, Instant startedAt, Instant completedAt) {
        // Validate INV-9/10: success/failure consistency
        if (!successFlag && failureReason == null) {
            throw new IllegalArgumentException("failureReason required when successFlag=false");
        }
        if (successFlag && failureReason != null) {
            throw new IllegalArgumentException("failureReason must be null when successFlag=true");
        }
        
        // Validate INV-11: duration ≥ 0
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must be >= 0");
        }
        
        // Validate INV-8: started ≤ completed
        if (startedAt != null && completedAt != null && startedAt.isAfter(completedAt)) {
            throw new IllegalArgumentException("startedAt must be <= completedAt");
        }
        
        this.id = id;
        this.orderTaskId = orderTaskId;
        this.orderId = orderId;
        this.tenantId = tenantId;
        this.accountId = accountId;
        this.proxyNodeId = proxyNodeId;
        this.attemptNum = attemptNum;
        this.successFlag = successFlag;
        this.failureReason = failureReason;
        this.httpStatus = httpStatus;
        this.executingNodeId = executingNodeId;
        this.durationMs = durationMs;
        this.startedAt = startedAt;
        this.completedAt = completedAt;  // INV-8: Always set
        this.createdAt = Instant.now();
    }

    // ── FTL-X Detection Signal Recording ────────────────────

    /**
     * Record abuse detection signals as JSON.
     * Example: {"account_behavior_anomaly": true, "network_fingerprint_mismatch": false, ...}
     */
    public void recordDetectionSignal(String jsonSignal, double score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be 0–100");
        }
        this.detectionSignal = jsonSignal;
        this.detectionScore = score;
    }

    /**
     * Record revenue impact from this execution (portion of order cost).
     */
    public void recordRevenueImpact(BigDecimal impact) {
        if (impact != null && impact.signum() < 0) {
            throw new IllegalArgumentException("revenue impact must be >= 0");
        }
        this.revenueImpact = impact;
    }

    // ── Getters ────────────────────────────────────────────
    
    public UUID getId() { return id; }
    public UUID getOrderTaskId() { return orderTaskId; }
    public UUID getOrderId() { return orderId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAccountId() { return accountId; }
    public UUID getProxyNodeId() { return proxyNodeId; }
    public int getAttemptNum() { return attemptNum; }
    public boolean isSuccessful() { return successFlag; }
    public String getFailureReason() { return failureReason; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getExecutingNodeId() { return executingNodeId; }
    public long getDurationMs() { return durationMs; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public String getDetectionSignal() { return detectionSignal; }
    public Double getDetectionScore() { return detectionScore; }
    public BigDecimal getRevenueImpact() { return revenueImpact; }
}
