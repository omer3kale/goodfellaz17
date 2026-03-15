package com.goodfellaz17.delivery.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * OrderTask — individual task decomposed from an Order.
 * One task = one account/proxy combination needed to deliver.
 * 
 * Maps to MSSQL order_tasks table.
 * Enforces INV-4: status progression PENDING → ASSIGNED → EXECUTING → COMPLETED/FAILED
 * Enforces INV-5: ASSIGNED state requires account_id and proxy_node_id
 * Enforces INV-6: EXECUTING state requires started_at
 * Enforces INV-7: Terminal states require completed_at
 */
public class OrderTask {
    
    private final UUID id;
    private final UUID orderId;
    private final UUID tenantId;
    
    private UUID accountId;         // Set at ASSIGNED
    private UUID proxyNodeId;       // Set at ASSIGNED
    
    private OrderTaskStatus status;
    private int quantity;
    private int retryCount;
    private int maxRetries;
    
    private String idempotencyKey;
    
    private final Instant createdAt;
    private Instant assignedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant staleAfter;
    
    private String lastError;

    public OrderTask(UUID id, UUID orderId, UUID tenantId, int quantity) {
        this.id = id;
        this.orderId = orderId;
        this.tenantId = tenantId;
        this.quantity = quantity;
        this.status = OrderTaskStatus.PENDING;
        this.retryCount = 0;
        this.maxRetries = 3;
        this.createdAt = Instant.now();
    }

    // ── State transitions (enforcing INV-4, INV-5, INV-6, INV-7) ────────

    /**
     * Transition to ASSIGNED: lock in account and proxy.
     * Enforces INV-5: both accountId and proxyNodeId must be set.
     */
    public void assignTo(UUID accountId, UUID proxyNodeId, long timeoutSec) {
        if (status != OrderTaskStatus.PENDING) {
            throw new IllegalStateException("Can only assign from PENDING");
        }
        if (accountId == null || proxyNodeId == null) {
            throw new IllegalArgumentException("accountId and proxyNodeId required for ASSIGNED");
        }
        this.accountId = accountId;
        this.proxyNodeId = proxyNodeId;
        this.status = OrderTaskStatus.ASSIGNED;
        this.assignedAt = Instant.now();
        this.staleAfter = Instant.now().plusSeconds(timeoutSec);
    }

    /**
     * Transition to EXECUTING.
     * Enforces INV-6: started_at must be set immediately.
     */
    public void startExecution() {
        if (status != OrderTaskStatus.ASSIGNED) {
            throw new IllegalStateException("Can only start from ASSIGNED");
        }
        this.status = OrderTaskStatus.EXECUTING;
        this.startedAt = Instant.now();  // INV-6
    }

    /**
     * Mark as successfully completed.
     * Enforces INV-7: completedAt must be set.
     */
    public void markCompleted() {
        if (status != OrderTaskStatus.EXECUTING) {
            throw new IllegalStateException("Can only complete from EXECUTING");
        }
        this.status = OrderTaskStatus.COMPLETED;
        this.completedAt = Instant.now();  // INV-7
        this.lastError = null;
    }

    /**
     * Mark as failed.
     * Enforces INV-7: completedAt must be set.
     */
    public void markFailed(String error) {
        if (status != OrderTaskStatus.EXECUTING) {
            throw new IllegalStateException("Can only fail from EXECUTING");
        }
        this.status = OrderTaskStatus.FAILED;
        this.completedAt = Instant.now();  // INV-7
        this.lastError = error;
    }

    /**
     * Check if this task can be retried.
     * Returns true if we haven't exceeded maxRetries.
     */
    public boolean canRetry() {
        return retryCount < maxRetries;
    }

    /**
     * Increment retry count and reset to PENDING for re-execution.
     */
    public void retry() {
        if (!canRetry()) {
            throw new IllegalStateException("Max retries exceeded");
        }
        retryCount++;
        this.status = OrderTaskStatus.PENDING;  // Back to PENDING for scheduler
        this.accountId = null;  // De-assign
        this.proxyNodeId = null;
        this.assignedAt = null;
        this.startedAt = null;
        this.completedAt = null;
        this.staleAfter = null;
    }

    /**
     * Check if this task has been stale (started but never finished).
     * Used by scheduler to detect hung tasks.
     */
    public boolean isStale() {
        if (startedAt == null || staleAfter == null) {
            return false;
        }
        return Instant.now().isAfter(staleAfter);
    }

    // ── Getters ────────────────────────────────────────────
    
    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAccountId() { return accountId; }
    public UUID getProxyNodeId() { return proxyNodeId; }
    public OrderTaskStatus getStatus() { return status; }
    public int getQuantity() { return quantity; }
    public int getRetryCount() { return retryCount; }
    public int getMaxRetries() { return maxRetries; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getStaleAfter() { return staleAfter; }
    public String getLastError() { return lastError; }
    
    public void setIdempotencyKey(String key) { this.idempotencyKey = key; }
    public void setMaxRetries(int max) { this.maxRetries = max; }

    public enum OrderTaskStatus {
        PENDING, ASSIGNED, EXECUTING, COMPLETED, FAILED
    }
}
