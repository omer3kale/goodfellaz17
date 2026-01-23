package com.goodfellaz17.domain.model.generated;

import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * RefundEventEntity - Append-only audit record for refund operations.
 * 
 * Each row represents a single refund credit for a failed task.
 * Used for:
 * - Financial reconciliation
 * - Fraud detection
 * - Dispute resolution
 * - Audit trail
 * 
 * Guarantees:
 * - One event per task (unique constraint on task_id)
 * - Immutable after creation (no UPDATE operations allowed)
 * - Permanent retention (ON DELETE RESTRICT)
 * 
 * @author goodfellaz17
 * @since 1.0.0
 */
@Table("refund_events")
public class RefundEventEntity implements Serializable, Persistable<UUID> {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    @NotNull
    private UUID id;
    
    /** The order this refund belongs to */
    @NotNull
    @Column("order_id")
    private UUID orderId;
    
    /** The specific task that was refunded */
    @NotNull
    @Column("task_id")
    private UUID taskId;
    
    /** The user who received the credit */
    @NotNull
    @Column("user_id")
    private UUID userId;
    
    /** Number of plays refunded */
    @NotNull
    @Column("quantity")
    private Integer quantity;
    
    /** Refund amount credited to user balance */
    @NotNull
    @Column("amount")
    private BigDecimal amount;
    
    /** Price per unit used for calculation (for audit) */
    @NotNull
    @Column("price_per_unit")
    private BigDecimal pricePerUnit;
    
    /** Worker ID that processed this refund (for debugging) */
    @Column("worker_id")
    private String workerId;
    
    /** Timestamp when refund was processed */
    @NotNull
    @Column("created_at")
    private Instant createdAt;
    
    @Transient
    private boolean isNew = true;
    
    // Default constructor for Spring Data
    public RefundEventEntity() {
    }
    
    /**
     * Create a new refund event.
     */
    public RefundEventEntity(
            UUID orderId,
            UUID taskId,
            UUID userId,
            int quantity,
            BigDecimal amount,
            BigDecimal pricePerUnit,
            String workerId) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.taskId = taskId;
        this.userId = userId;
        this.quantity = quantity;
        this.amount = amount;
        this.pricePerUnit = pricePerUnit;
        this.workerId = workerId;
        this.createdAt = Instant.now();
        this.isNew = true;
    }
    
    /**
     * Factory method with all parameters.
     */
    public static RefundEventEntity create(
            UUID orderId,
            UUID taskId,
            UUID userId,
            int quantity,
            BigDecimal amount,
            BigDecimal pricePerUnit,
            String workerId) {
        return new RefundEventEntity(orderId, taskId, userId, quantity, amount, pricePerUnit, workerId);
    }
    
    @Override
    public UUID getId() {
        return id;
    }
    
    @Override
    @Transient
    public boolean isNew() {
        return isNew;
    }
    
    public void markNotNew() {
        this.isNew = false;
    }
    
    // Getters
    
    public UUID getOrderId() {
        return orderId;
    }
    
    public UUID getTaskId() {
        return taskId;
    }
    
    public UUID getUserId() {
        return userId;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public BigDecimal getPricePerUnit() {
        return pricePerUnit;
    }
    
    public String getWorkerId() {
        return workerId;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    // Setters (limited - this is mostly immutable)
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }
    
    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }
    
    public void setUserId(UUID userId) {
        this.userId = userId;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
    
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    
    public void setPricePerUnit(BigDecimal pricePerUnit) {
        this.pricePerUnit = pricePerUnit;
    }
    
    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public String toString() {
        return "RefundEventEntity{" +
                "id=" + id +
                ", orderId=" + orderId +
                ", taskId=" + taskId +
                ", userId=" + userId +
                ", quantity=" + quantity +
                ", amount=" + amount +
                ", createdAt=" + createdAt +
                '}';
    }
}
