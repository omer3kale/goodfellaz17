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
 * RefundAnomalyEntity - Records detected discrepancies in refund data.
 * 
 * Created by reconciliation jobs when order-level aggregates don't match
 * task-level data. Severity levels:
 * - INFO: Minor discrepancy, likely rounding
 * - WARNING: Significant mismatch, needs review
 * - CRITICAL: Large discrepancy, possible fraud or bug
 * 
 * @author goodfellaz17
 * @since 1.0.0
 */
@Table("refund_anomalies")
public class RefundAnomalyEntity implements Serializable, Persistable<UUID> {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    @NotNull
    private UUID id;
    
    @NotNull
    @Column("order_id")
    private UUID orderId;
    
    @NotNull
    @Column("detected_at")
    private Instant detectedAt;
    
    @NotNull
    @Column("anomaly_type")
    private String anomalyType;
    
    @Column("expected_refund_amount")
    private BigDecimal expectedRefundAmount;
    
    @Column("actual_refund_amount")
    private BigDecimal actualRefundAmount;
    
    @Column("expected_failed_plays")
    private Integer expectedFailedPlays;
    
    @Column("actual_failed_plays")
    private Integer actualFailedPlays;
    
    @Column("refunded_task_count")
    private Integer refundedTaskCount;
    
    @NotNull
    @Column("severity")
    private String severity = "WARNING";
    
    @Column("resolved_at")
    private Instant resolvedAt;
    
    @Column("resolution_notes")
    private String resolutionNotes;
    
    @Transient
    private boolean isNew = true;
    
    public RefundAnomalyEntity() {
    }
    
    /**
     * Create a refund amount mismatch anomaly.
     */
    public static RefundAnomalyEntity refundMismatch(
            UUID orderId,
            BigDecimal expected,
            BigDecimal actual,
            int refundedTaskCount) {
        RefundAnomalyEntity anomaly = new RefundAnomalyEntity();
        anomaly.id = UUID.randomUUID();
        anomaly.orderId = orderId;
        anomaly.detectedAt = Instant.now();
        anomaly.anomalyType = "REFUND_AMOUNT_MISMATCH";
        anomaly.expectedRefundAmount = expected;
        anomaly.actualRefundAmount = actual;
        anomaly.refundedTaskCount = refundedTaskCount;
        anomaly.severity = determineSeverity(expected, actual);
        anomaly.isNew = true;
        return anomaly;
    }
    
    /**
     * Create a failed plays mismatch anomaly.
     */
    public static RefundAnomalyEntity failedPlaysMismatch(
            UUID orderId,
            int expected,
            int actual) {
        RefundAnomalyEntity anomaly = new RefundAnomalyEntity();
        anomaly.id = UUID.randomUUID();
        anomaly.orderId = orderId;
        anomaly.detectedAt = Instant.now();
        anomaly.anomalyType = "FAILED_PLAYS_MISMATCH";
        anomaly.expectedFailedPlays = expected;
        anomaly.actualFailedPlays = actual;
        anomaly.severity = Math.abs(expected - actual) > 1000 ? "CRITICAL" : "WARNING";
        anomaly.isNew = true;
        return anomaly;
    }
    
    private static String determineSeverity(BigDecimal expected, BigDecimal actual) {
        BigDecimal diff = expected.subtract(actual).abs();
        if (diff.compareTo(BigDecimal.valueOf(10)) > 0) {
            return "CRITICAL";
        } else if (diff.compareTo(BigDecimal.valueOf(1)) > 0) {
            return "WARNING";
        }
        return "INFO";
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
    
    // Getters and setters
    
    public UUID getOrderId() {
        return orderId;
    }
    
    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }
    
    public Instant getDetectedAt() {
        return detectedAt;
    }
    
    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }
    
    public String getAnomalyType() {
        return anomalyType;
    }
    
    public void setAnomalyType(String anomalyType) {
        this.anomalyType = anomalyType;
    }
    
    public BigDecimal getExpectedRefundAmount() {
        return expectedRefundAmount;
    }
    
    public void setExpectedRefundAmount(BigDecimal expectedRefundAmount) {
        this.expectedRefundAmount = expectedRefundAmount;
    }
    
    public BigDecimal getActualRefundAmount() {
        return actualRefundAmount;
    }
    
    public void setActualRefundAmount(BigDecimal actualRefundAmount) {
        this.actualRefundAmount = actualRefundAmount;
    }
    
    public Integer getExpectedFailedPlays() {
        return expectedFailedPlays;
    }
    
    public void setExpectedFailedPlays(Integer expectedFailedPlays) {
        this.expectedFailedPlays = expectedFailedPlays;
    }
    
    public Integer getActualFailedPlays() {
        return actualFailedPlays;
    }
    
    public void setActualFailedPlays(Integer actualFailedPlays) {
        this.actualFailedPlays = actualFailedPlays;
    }
    
    public Integer getRefundedTaskCount() {
        return refundedTaskCount;
    }
    
    public void setRefundedTaskCount(Integer refundedTaskCount) {
        this.refundedTaskCount = refundedTaskCount;
    }
    
    public String getSeverity() {
        return severity;
    }
    
    public void setSeverity(String severity) {
        this.severity = severity;
    }
    
    public Instant getResolvedAt() {
        return resolvedAt;
    }
    
    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
    
    public String getResolutionNotes() {
        return resolutionNotes;
    }
    
    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    @Override
    public String toString() {
        return "RefundAnomalyEntity{" +
                "id=" + id +
                ", orderId=" + orderId +
                ", anomalyType='" + anomalyType + '\'' +
                ", severity='" + severity + '\'' +
                ", detectedAt=" + detectedAt +
                '}';
    }
}
