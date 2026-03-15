package com.goodfellaz17.infrastructure.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * OrderTaskEntity — R2DBC mapping for order_tasks table.
 * Represents a single task decomposed from an order.
 */
@Table("order_tasks")
public class OrderTaskEntity {

    @Id
    @Column("id")
    private UUID id;

    @Column("order_id")
    private UUID orderId;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("account_id")
    private UUID accountId;

    @Column("proxy_node_id")
    private UUID proxyNodeId;

    @Column("status")
    private String status; // PENDING, ASSIGNED, EXECUTING, COMPLETED, FAILED

    @Column("quantity")
    private Integer quantity;

    @Column("retry_count")
    private Integer retryCount;

    @Column("max_retries")
    private Integer maxRetries;

    @Column("idempotency_key")
    private String idempotencyKey;

    @Column("created_at")
    private Instant createdAt;

    @Column("assigned_at")
    private Instant assignedAt;

    @Column("started_at")
    private Instant startedAt;

    @Column("completed_at")
    private Instant completedAt;

    @Column("stale_after")
    private Instant staleAfter;

    @Column("last_error")
    private String lastError;

    // ── Constructors ────────────────────────────────────

    public OrderTaskEntity() {
    }

    public OrderTaskEntity(UUID id, UUID orderId, UUID tenantId, Integer quantity) {
        this.id = id;
        this.orderId = orderId;
        this.tenantId = tenantId;
        this.quantity = quantity;
        this.status = "PENDING";
        this.retryCount = 0;
        this.maxRetries = 3;
        this.createdAt = Instant.now();
    }

    // ── Getters & Setters ────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public UUID getProxyNodeId() { return proxyNodeId; }
    public void setProxyNodeId(UUID proxyNodeId) { this.proxyNodeId = proxyNodeId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Instant getStaleAfter() { return staleAfter; }
    public void setStaleAfter(Instant staleAfter) { this.staleAfter = staleAfter; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    @Override
    public String toString() {
        return "OrderTaskEntity{" +
                "id=" + id +
                ", orderId=" + orderId +
                ", status='" + status + '\'' +
                ", quantity=" + quantity +
                ", retryCount=" + retryCount +
                ", createdAt=" + createdAt +
                '}';
    }
}
