package com.goodfellaz17.infrastructure.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * ExecutionResultEntity — R2DBC mapping for execution_results table.
 * Represents the outcome of a single task execution attempt.
 */
@Table("execution_results")
public class ExecutionResultEntity {

    @Id
    @Column("id")
    private UUID id;

    @Column("order_task_id")
    private UUID orderTaskId;

    @Column("order_id")
    private UUID orderId;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("account_id")
    private UUID accountId;

    @Column("proxy_node_id")
    private UUID proxyNodeId;

    @Column("attempt_num")
    private Integer attemptNum;

    @Column("success_flag")
    private Boolean successFlag;

    @Column("failure_reason")
    private String failureReason;

    @Column("http_status")
    private Integer httpStatus;

    @Column("executing_node_id")
    private String executingNodeId;

    @Column("duration_ms")
    private Long durationMs;

    @Column("started_at")
    private Instant startedAt;

    @Column("completed_at")
    private Instant completedAt;

    @Column("created_at")
    private Instant createdAt;

    @Column("detection_signal")
    private String detectionSignal;

    @Column("detection_score")
    private Double detectionScore;

    @Column("revenue_impact")
    private BigDecimal revenueImpact;

    // ── Constructors ────────────────────────────────────

    public ExecutionResultEntity() {
    }

    public ExecutionResultEntity(UUID id, UUID orderTaskId, UUID orderId, UUID tenantId,
                                 UUID accountId, UUID proxyNodeId, Integer attemptNum,
                                 Boolean successFlag, String failureReason, Long durationMs) {
        this.id = id;
        this.orderTaskId = orderTaskId;
        this.orderId = orderId;
        this.tenantId = tenantId;
        this.accountId = accountId;
        this.proxyNodeId = proxyNodeId;
        this.attemptNum = attemptNum;
        this.successFlag = successFlag;
        this.failureReason = failureReason;
        this.durationMs = durationMs;
        this.startedAt = Instant.now();
        this.completedAt = Instant.now();
        this.createdAt = Instant.now();
    }

    // ── Getters & Setters ────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrderTaskId() { return orderTaskId; }
    public void setOrderTaskId(UUID orderTaskId) { this.orderTaskId = orderTaskId; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public UUID getProxyNodeId() { return proxyNodeId; }
    public void setProxyNodeId(UUID proxyNodeId) { this.proxyNodeId = proxyNodeId; }

    public Integer getAttemptNum() { return attemptNum; }
    public void setAttemptNum(Integer attemptNum) { this.attemptNum = attemptNum; }

    public Boolean getSuccessFlag() { return successFlag; }
    public void setSuccessFlag(Boolean successFlag) { this.successFlag = successFlag; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }

    public String getExecutingNodeId() { return executingNodeId; }
    public void setExecutingNodeId(String executingNodeId) { this.executingNodeId = executingNodeId; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getDetectionSignal() { return detectionSignal; }
    public void setDetectionSignal(String detectionSignal) { this.detectionSignal = detectionSignal; }

    public Double getDetectionScore() { return detectionScore; }
    public void setDetectionScore(Double detectionScore) { this.detectionScore = detectionScore; }

    public BigDecimal getRevenueImpact() { return revenueImpact; }
    public void setRevenueImpact(BigDecimal revenueImpact) { this.revenueImpact = revenueImpact; }

    @Override
    public String toString() {
        return "ExecutionResultEntity{" +
                "id=" + id +
                ", orderTaskId=" + orderTaskId +
                ", attemptNum=" + attemptNum +
                ", successFlag=" + successFlag +
                ", durationMs=" + durationMs +
                ", createdAt=" + createdAt +
                '}';
    }
}
