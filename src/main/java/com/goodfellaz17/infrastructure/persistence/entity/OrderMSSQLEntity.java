package com.goodfellaz17.infrastructure.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Order Entity (MSSQL) — Maps to new MSSQL orders table.
 * 
 * Represents a top-level customer order for delivery.
 * Supports the new FTL-B schema with proper temporal tracking and pricing.
 */
@Table("orders")
public class OrderMSSQLEntity {

    @Id
    @Column("id")
    private UUID id;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("service_id")
    private UUID serviceId;

    @Column("track_id")
    private String trackId;

    @Column("artist_id")
    private String artistId;

    @Column("quantity")
    private Integer quantity;

    @Column("status")
    private String status;

    @Column("status_changed_at")
    private Instant statusChangedAt;

    @Column("price_tier_id")
    private UUID priceTrierId;

    @Column("estimated_cost")
    private BigDecimal estimatedCost;

    @Column("actual_cost")
    private BigDecimal actualCost;

    @Column("plays_delivered")
    private Integer playsDelivered;

    @Column("plays_failed")
    private Integer playsFailed;

    @Column("failure_reason")
    private String failureReason;

    @Column("created_at")
    private Instant createdAt;

    @Column("started_at")
    private Instant startedAt;

    @Column("completed_at")
    private Instant completedAt;

    @Column("created_by_api_key_id")
    private UUID createdByApiKeyId;

    // ── Constructors ────────────────────────────────────

    public OrderMSSQLEntity() {
    }

    public OrderMSSQLEntity(UUID id, UUID tenantId, UUID serviceId, String trackId, 
                           String artistId, Integer quantity, String status, 
                           Instant statusChangedAt, UUID priceTrierId, BigDecimal estimatedCost, 
                           BigDecimal actualCost, Integer playsDelivered, Integer playsFailed, 
                           String failureReason, Instant createdAt, Instant startedAt, 
                           Instant completedAt, UUID createdByApiKeyId) {
        this.id = id;
        this.tenantId = tenantId;
        this.serviceId = serviceId;
        this.trackId = trackId;
        this.artistId = artistId;
        this.quantity = quantity;
        this.status = status;
        this.statusChangedAt = statusChangedAt;
        this.priceTrierId = priceTrierId;
        this.estimatedCost = estimatedCost;
        this.actualCost = actualCost;
        this.playsDelivered = playsDelivered;
        this.playsFailed = playsFailed;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.createdByApiKeyId = createdByApiKeyId;
    }

    // ── Getters & Setters ────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public void setServiceId(UUID serviceId) {
        this.serviceId = serviceId;
    }

    public String getTrackId() {
        return trackId;
    }

    public void setTrackId(String trackId) {
        this.trackId = trackId;
    }

    public String getArtistId() {
        return artistId;
    }

    public void setArtistId(String artistId) {
        this.artistId = artistId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public void setStatusChangedAt(Instant statusChangedAt) {
        this.statusChangedAt = statusChangedAt;
    }

    public UUID getPriceTrierId() {
        return priceTrierId;
    }

    public void setPriceTrierId(UUID priceTrierId) {
        this.priceTrierId = priceTrierId;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(BigDecimal estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    public BigDecimal getActualCost() {
        return actualCost;
    }

    public void setActualCost(BigDecimal actualCost) {
        this.actualCost = actualCost;
    }

    public Integer getPlaysDelivered() {
        return playsDelivered;
    }

    public void setPlaysDelivered(Integer playsDelivered) {
        this.playsDelivered = playsDelivered;
    }

    public Integer getPlaysFailed() {
        return playsFailed;
    }

    public void setPlaysFailed(Integer playsFailed) {
        this.playsFailed = playsFailed;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public UUID getCreatedByApiKeyId() {
        return createdByApiKeyId;
    }

    public void setCreatedByApiKeyId(UUID createdByApiKeyId) {
        this.createdByApiKeyId = createdByApiKeyId;
    }

    @Override
    public String toString() {
        return "OrderMSSQLEntity{" +
                "id=" + id +
                ", tenantId=" + tenantId +
                ", serviceId=" + serviceId +
                ", trackId='" + trackId + '\'' +
                ", quantity=" + quantity +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
