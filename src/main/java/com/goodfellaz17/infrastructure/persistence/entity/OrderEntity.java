package com.goodfellaz17.infrastructure.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * OrderEntity — R2DBC mapping for orders table (V15 MSSQL schema).
 * Represents a top-level customer order for streaming delivery.
 * Status progression: PENDING → ACTIVE → DELIVERING → COMPLETED/FAILED
 */
@Table("orders")
public class OrderEntity {

    @Id
    @Column("id")
    private UUID id;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("service_id")
    private UUID serviceId;

    @Column("created_by_api_key_id")
    private UUID createdByApiKeyId;

    @Column("track_id")
    private String trackId;

    @Column("artist_id")
    private String artistId;

    @Column("quantity")
    private Integer quantity;

    @Column("price_tier_id")
    private UUID priceTierId;

    @Column("estimated_cost")
    private BigDecimal estimatedCost;

    @Column("actual_cost")
    private BigDecimal actualCost;

    @Column("status")
    private String status; // PENDING, ACTIVE, DELIVERING, COMPLETED, FAILED

    @Column("status_changed_at")
    private Instant statusChangedAt;

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

    @Column("metadata")
    private String metadata;

    // ── Constructors ────────────────────────────────────

    public OrderEntity() {
    }

    public OrderEntity(UUID tenantId, UUID serviceId, Integer quantity) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.serviceId = serviceId;
        this.quantity = quantity;
        this.status = "PENDING";
        this.playsDelivered = 0;
        this.playsFailed = 0;
        this.createdAt = Instant.now();
        this.statusChangedAt = Instant.now();
    }

    // ── Getters & Setters ────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getServiceId() { return serviceId; }
    public void setServiceId(UUID serviceId) { this.serviceId = serviceId; }

    public UUID getCreatedByApiKeyId() { return createdByApiKeyId; }
    public void setCreatedByApiKeyId(UUID createdByApiKeyId) { this.createdByApiKeyId = createdByApiKeyId; }

    public String getTrackId() { return trackId; }
    public void setTrackId(String trackId) { this.trackId = trackId; }

    public String getArtistId() { return artistId; }
    public void setArtistId(String artistId) { this.artistId = artistId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public UUID getPriceTierId() { return priceTierId; }
    public void setPriceTierId(UUID priceTierId) { this.priceTierId = priceTierId; }

    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(BigDecimal estimatedCost) { this.estimatedCost = estimatedCost; }

    public BigDecimal getActualCost() { return actualCost; }
    public void setActualCost(BigDecimal actualCost) { this.actualCost = actualCost; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getStatusChangedAt() { return statusChangedAt; }
    public void setStatusChangedAt(Instant statusChangedAt) { this.statusChangedAt = statusChangedAt; }

    public Integer getPlaysDelivered() { return playsDelivered; }
    public void setPlaysDelivered(Integer playsDelivered) { this.playsDelivered = playsDelivered; }

    public Integer getPlaysFailed() { return playsFailed; }
    public void setPlaysFailed(Integer playsFailed) { this.playsFailed = playsFailed; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    @Override
    public String toString() {
        return "OrderEntity{" +
                "id=" + id +
                ", tenantId=" + tenantId +
                ", serviceId=" + serviceId +
                ", quantity=" + quantity +
                ", status='" + status + '\'' +
                ", playsDelivered=" + playsDelivered +
                ", createdAt=" + createdAt +
                '}';
    }
}
