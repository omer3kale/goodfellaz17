package com.goodfellaz17.domain.model.generated;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * OrderTaskEntity - Granular execution units for 15k orders.
 * 
 * Each task represents a small batch of plays (200-500) that:
 * - Is atomic: either fully completed or fully failed
 * - Has retry capability with exponential backoff
 * - Is idempotent: same task execution won't double-count
 * - Is checkpointed: survives app restarts
 * 
 * States:
 * - PENDING: Waiting to be picked up by worker
 * - EXECUTING: Currently being processed (may be orphaned if crash)
 * - COMPLETED: Successfully delivered plays
 * - FAILED_RETRYING: Transient failure, will retry
 * - FAILED_PERMANENT: After max retries, moved to dead-letter queue
 * 
 * @author goodfellaz17
 * @since 1.0.0
 */
@Table("order_tasks")
public class OrderTaskEntity implements Serializable, Persistable<UUID> {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    @NotNull
    private UUID id;
    
    /** Parent order ID - the 15k order this task belongs to */
    @NotNull
    @Column("order_id")
    private UUID orderId;
    
    /** 
     * Sequence number within the order (1, 2, 3...).
     * Used for ordering and idempotency.
     */
    @NotNull
    @Min(1)
    @Column("sequence_number")
    private Integer sequenceNumber;
    
    /** Number of plays this task will deliver (e.g., 300-500) */
    @NotNull
    @Min(1)
    @Max(1000)
    @Column("quantity")
    private Integer quantity;
    
    /** Current task status */
    @NotNull
    @Column("status")
    private String status = TaskStatus.PENDING.name();
    
    /** Number of execution attempts (starts at 0) */
    @NotNull
    @Min(0)
    @Column("attempts")
    private Integer attempts = 0;
    
    /** Maximum allowed attempts before permanent failure */
    @NotNull
    @Column("max_attempts")
    private Integer maxAttempts = 3;
    
    /** Last error message if failed */
    @Nullable
    @Size(max = 1000)
    @Column("last_error")
    private String lastError;
    
    /** Proxy node ID that last attempted this task */
    @Nullable
    @Column("proxy_node_id")
    private UUID proxyNodeId;
    
    /** When the task was last picked up for execution */
    @Nullable
    @Column("execution_started_at")
    private Instant executionStartedAt;
    
    /** When the task was successfully completed */
    @Nullable
    @Column("executed_at")
    private Instant executedAt;
    
    /** Scheduled execution time (for spreading across 48-72h window) */
    @NotNull
    @Column("scheduled_at")
    private Instant scheduledAt;
    
    /** When the task should be retried (for backoff) */
    @Nullable
    @Column("retry_after")
    private Instant retryAfter;
    
    /** Creation timestamp */
    @NotNull
    @CreatedDate
    @Column("created_at")
    private Instant createdAt = Instant.now();
    
    /** 
     * Idempotency token - unique identifier for this exact task execution.
     * Format: {orderId}:{sequenceNumber}:{attempts}
     * Prevents double-counting on retries.
     */
    @NotNull
    @Size(max = 128)
    @Column("idempotency_token")
    private String idempotencyToken;
    
    /** Worker ID that claimed this task (for orphan detection) */
    @Nullable
    @Size(max = 64)
    @Column("worker_id")
    private String workerId;
    
    @Transient
    private boolean isNew = true;
    
    @Override
    public boolean isNew() {
        return isNew;
    }
    
    @Override
    public UUID getId() {
        return id;
    }
    
    public OrderTaskEntity markNotNew() {
        this.isNew = false;
        return this;
    }
    
    // === Constructors ===
    
    public OrderTaskEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.isNew = true;
    }
    
    private OrderTaskEntity(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID();
        this.orderId = Objects.requireNonNull(builder.orderId, "orderId is required");
        this.sequenceNumber = Objects.requireNonNull(builder.sequenceNumber, "sequenceNumber is required");
        this.quantity = Objects.requireNonNull(builder.quantity, "quantity is required");
        this.status = builder.status != null ? builder.status : TaskStatus.PENDING.name();
        this.attempts = builder.attempts != null ? builder.attempts : 0;
        this.maxAttempts = builder.maxAttempts != null ? builder.maxAttempts : 3;
        this.lastError = builder.lastError;
        this.proxyNodeId = builder.proxyNodeId;
        this.executionStartedAt = builder.executionStartedAt;
        this.executedAt = builder.executedAt;
        this.scheduledAt = Objects.requireNonNull(builder.scheduledAt, "scheduledAt is required");
        this.retryAfter = builder.retryAfter;
        this.createdAt = Instant.now();
        this.idempotencyToken = builder.idempotencyToken != null 
            ? builder.idempotencyToken 
            : buildIdempotencyToken(this.orderId, this.sequenceNumber, 0);
        this.workerId = builder.workerId;
        this.isNew = true;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // === Business Logic ===
    
    /**
     * Check if task is ready to execute (past scheduled time, not locked).
     */
    public boolean isReadyForExecution() {
        if (!TaskStatus.PENDING.name().equals(status) && 
            !TaskStatus.FAILED_RETRYING.name().equals(status)) {
            return false;
        }
        
        Instant now = Instant.now();
        
        // Must be past scheduled time
        if (scheduledAt.isAfter(now)) {
            return false;
        }
        
        // If retrying, must be past retry delay
        if (retryAfter != null && retryAfter.isAfter(now)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if task appears orphaned (EXECUTING but stale).
     */
    public boolean isOrphaned(int orphanThresholdSeconds) {
        if (!TaskStatus.EXECUTING.name().equals(status)) {
            return false;
        }
        
        if (executionStartedAt == null) {
            return true; // No start time = definitely orphaned
        }
        
        return executionStartedAt.plusSeconds(orphanThresholdSeconds).isBefore(Instant.now());
    }
    
    /**
     * Mark task as starting execution.
     */
    public void startExecution(String workerId, UUID proxyNodeId) {
        this.status = TaskStatus.EXECUTING.name();
        this.executionStartedAt = Instant.now();
        this.workerId = workerId;
        this.proxyNodeId = proxyNodeId;
        this.attempts++;
        this.isNew = false;
    }
    
    /**
     * Mark task as completed.
     */
    public void completeExecution() {
        this.status = TaskStatus.COMPLETED.name();
        this.executedAt = Instant.now();
        this.lastError = null;
        this.isNew = false;
    }
    
    /**
     * Mark task as failed, potentially for retry.
     */
    public void failExecution(String errorMessage) {
        this.lastError = errorMessage;
        this.executionStartedAt = null;
        this.workerId = null;
        this.isNew = false;
        
        if (this.attempts >= this.maxAttempts) {
            this.status = TaskStatus.FAILED_PERMANENT.name();
        } else {
            this.status = TaskStatus.FAILED_RETRYING.name();
            // Exponential backoff: 30s, 60s, 120s...
            long backoffSeconds = 30L * (1L << (this.attempts - 1));
            this.retryAfter = Instant.now().plusSeconds(backoffSeconds);
            // Update idempotency token for new attempt
            this.idempotencyToken = buildIdempotencyToken(this.orderId, this.sequenceNumber, this.attempts);
        }
    }
    
    /**
     * Reset orphaned task to PENDING for re-pickup.
     */
    public void resetOrphanedTask() {
        this.status = TaskStatus.PENDING.name();
        this.executionStartedAt = null;
        this.workerId = null;
        // Don't increment attempts for orphan reset
        this.isNew = false;
    }
    
    private static String buildIdempotencyToken(UUID orderId, int sequenceNumber, int attemptNumber) {
        return String.format("%s:%d:%d", orderId.toString(), sequenceNumber, attemptNumber);
    }
    
    // === Getters ===
    
    public UUID getOrderId() { return orderId; }
    public Integer getSequenceNumber() { return sequenceNumber; }
    public Integer getQuantity() { return quantity; }
    public String getStatus() { return status; }
    public Integer getAttempts() { return attempts; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public String getLastError() { return lastError; }
    public UUID getProxyNodeId() { return proxyNodeId; }
    public Instant getExecutionStartedAt() { return executionStartedAt; }
    public Instant getExecutedAt() { return executedAt; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getRetryAfter() { return retryAfter; }
    public Instant getCreatedAt() { return createdAt; }
    public String getIdempotencyToken() { return idempotencyToken; }
    public String getWorkerId() { return workerId; }
    
    public TaskStatus getStatusEnum() {
        try {
            return TaskStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return TaskStatus.PENDING;
        }
    }
    
    // === Setters (for R2DBC) ===
    
    public void setId(UUID id) { this.id = id; this.isNew = false; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public void setSequenceNumber(Integer sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public void setStatus(String status) { this.status = status; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public void setProxyNodeId(UUID proxyNodeId) { this.proxyNodeId = proxyNodeId; }
    public void setExecutionStartedAt(Instant executionStartedAt) { this.executionStartedAt = executionStartedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public void setRetryAfter(Instant retryAfter) { this.retryAfter = retryAfter; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setIdempotencyToken(String idempotencyToken) { this.idempotencyToken = idempotencyToken; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
    
    // === Builder ===
    
    public static class Builder {
        private UUID id;
        private UUID orderId;
        private Integer sequenceNumber;
        private Integer quantity;
        private String status;
        private Integer attempts;
        private Integer maxAttempts;
        private String lastError;
        private UUID proxyNodeId;
        private Instant executionStartedAt;
        private Instant executedAt;
        private Instant scheduledAt;
        private Instant retryAfter;
        private String idempotencyToken;
        private String workerId;
        
        public Builder id(UUID id) { this.id = id; return this; }
        public Builder orderId(UUID orderId) { this.orderId = orderId; return this; }
        public Builder sequenceNumber(Integer sequenceNumber) { this.sequenceNumber = sequenceNumber; return this; }
        public Builder quantity(Integer quantity) { this.quantity = quantity; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder attempts(Integer attempts) { this.attempts = attempts; return this; }
        public Builder maxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; return this; }
        public Builder lastError(String lastError) { this.lastError = lastError; return this; }
        public Builder proxyNodeId(UUID proxyNodeId) { this.proxyNodeId = proxyNodeId; return this; }
        public Builder executionStartedAt(Instant executionStartedAt) { this.executionStartedAt = executionStartedAt; return this; }
        public Builder executedAt(Instant executedAt) { this.executedAt = executedAt; return this; }
        public Builder scheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; return this; }
        public Builder retryAfter(Instant retryAfter) { this.retryAfter = retryAfter; return this; }
        public Builder idempotencyToken(String idempotencyToken) { this.idempotencyToken = idempotencyToken; return this; }
        public Builder workerId(String workerId) { this.workerId = workerId; return this; }
        
        public OrderTaskEntity build() {
            return new OrderTaskEntity(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("OrderTask{id=%s, orderId=%s, seq=%d, qty=%d, status=%s, attempts=%d/%d}",
            id, orderId, sequenceNumber, quantity, status, attempts, maxAttempts);
    }
}
