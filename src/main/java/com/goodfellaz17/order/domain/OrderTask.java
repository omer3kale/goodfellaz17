package com.goodfellaz17.order.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * OrderTask: a single unit of work for an Order.
 * Each task represents delivering 1 play to a specific account.
 * Table: pipeline_order_tasks (separate from the main order_tasks table for global order system)
 */
@Table(name = "pipeline_order_tasks")
public class OrderTask {

    @Id
    private UUID id;

    @Column(value = "order_id")
    private UUID orderId;  // FK to Order (R2DBC uses simple FK columns, not JPA relationships)

    @Column(value = "account_id")
    private String accountId;       // Spotify account to play from

    @Column(value = "status")
    private String status;      // PENDING → ASSIGNED → EXECUTING → COMPLETED/FAILED (String for R2DBC)

    @Column(value = "assigned_proxy_node")
    private String assignedProxyNode;  // Which proxy node is handling this

    @Column(value = "failure_reason")
    private String failureReason;   // Why it failed

    @Column(value = "created_at")
    private Instant createdAt;

    @Column(value = "started_at")
    private Instant startedAt;

    @Column(value = "completed_at")
    private Instant completedAt;

    @Column(value = "retry_count")
    private Integer retryCount = 0;

    @Column(value = "max_retries")
    private Integer maxRetries = 3;

    // Constructor for R2DBC - generates UUID and initializes defaults
    public OrderTask() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.status = "PENDING";
        this.retryCount = 0;
        this.maxRetries = 3;
    }

// Getters
    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getAccountId() { return accountId; }
    public String getStatus() { return status; }
    public String getAssignedProxyNode() { return assignedProxyNode; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Integer getRetryCount() { return retryCount; }
    public Integer getMaxRetries() { return maxRetries; }

    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public void setStatus(String status) { this.status = status; }
    public void setAssignedProxyNode(String node) { this.assignedProxyNode = node; }
    public void setFailureReason(String reason) { this.failureReason = reason; }
    public void setCreatedAt(Instant time) { this.createdAt = time; }
    public void setStartedAt(Instant time) { this.startedAt = time; }
    public void setCompletedAt(Instant time) { this.completedAt = time; }
    public void setRetryCount(Integer count) { this.retryCount = count; }
    public void setMaxRetries(Integer max) { this.maxRetries = max; }

    // Business Logic
    public boolean canRetry() {
        return retryCount < maxRetries && status.equals("FAILED");
    }

    public void incrementRetry() {
        this.retryCount++;
    }

    public boolean isStale() {
        if (startedAt == null) return false;
        long ageSeconds = (System.currentTimeMillis() - startedAt.toEpochMilli()) / 1000;
        return ageSeconds > 300; // 5 minutes
    }
}
