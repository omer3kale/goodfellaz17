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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * Entity: Order
 * Table: orders
 * 
 * Core delivery order for Spotify engagement services.
 * Tracks quantity, delivery progress, geo-targeting, and financials.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
@Table("orders")
public class OrderEntity implements Serializable, Persistable<UUID> {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    @NotNull
    private UUID id;
    
    @NotNull
    @Column("user_id")
    private UUID userId;
    
    @NotNull
    @Column("service_id")
    private UUID serviceId;
    
    @NotNull
    @Min(1)
    @Max(10000000)
    @Column("quantity")
    private Integer quantity;
    
    @NotNull
    @Min(0)
    @Column("delivered")
    private Integer delivered = 0;
    
    @NotNull
    @Size(max = 512)
    @Pattern(regexp = "^https://(open\\.)?spotify\\.com/.*$")
    @Column("target_url")
    private String targetUrl;
    
    /**
     * Geographic targeting profile: WORLDWIDE, USA, UK, DE, etc.
     * Stored as STRING in database for forward compatibility.
     */
    @NotNull
    @Column("geo_profile")
    private String geoProfile = GeoProfile.WORLDWIDE.name();
    
    @NotNull
    @DecimalMin("0.1")
    @DecimalMax("5.0")
    @Column("speed_multiplier")
    private Double speedMultiplier = 1.0;
    
    /**
     * Order lifecycle status: PENDING, VALIDATING, RUNNING, COMPLETED, etc.
     * Stored as STRING in database for forward compatibility.
     */
    @NotNull
    @Column("status")
    private String status = OrderStatus.PENDING.name();
    
    @Nullable
    @Size(max = 255)
    @Column("service_name")
    private String serviceName;
    
    @Nullable
    @DecimalMin("0.0001")
    @Column("price_per_unit")
    private BigDecimal pricePerUnit;
    
    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("99999.99")
    @Column("total_cost")
    private BigDecimal totalCost;
    
    @NotNull
    @Min(0)
    @Column("remains")
    private Integer remains = 0;
    
    @NotNull
    @DecimalMin("0.00")
    @Column("refund_amount")
    private BigDecimal refundAmount = BigDecimal.ZERO;
    
    @Nullable
    @Column("start_count")
    private Integer startCount;
    
    @Nullable
    @Column("current_count")
    private Integer currentCount;
    
    @NotNull
    @CreatedDate
    @Column("created_at")
    private Instant createdAt = Instant.now();
    
    @Nullable
    @Column("started_at")
    private Instant startedAt;
    
    @Nullable
    @Column("completed_at")
    private Instant completedAt;
    
    @Nullable
    @Size(max = 500)
    @Column("failure_reason")
    private String failureReason;
    
    @Nullable
    @Size(max = 1000)
    @Column("internal_notes")
    private String internalNotes;
    
    @Nullable
    @Size(max = 64)
    @Column("external_order_id")
    private String externalOrderId;
    
    @NotNull
    @Column("webhook_delivered")
    private Boolean webhookDelivered = false;
    
    /**
     * Estimated completion time based on capacity planning.
     * Set during order creation by CapacityService.
     */
    @Nullable
    @Column("estimated_completion_at")
    private Instant estimatedCompletionAt;
    
    /**
     * Count of plays that permanently failed (dead-letter queue).
     * Only applicable for task-based delivery (15k+ orders).
     */
    @NotNull
    @Min(0)
    @Column("failed_permanent_plays")
    private Integer failedPermanentPlays = 0;
    
    /**
     * True if order uses task-based delivery (15k+ orders).
     */
    @NotNull
    @Column("uses_task_delivery")
    private Boolean usesTaskDelivery = false;
    
    @Transient
    private boolean isNew = true;
    
    @Override
    public boolean isNew() {
        return isNew;
    }
    
    public OrderEntity markNotNew() {
        this.isNew = false;
        return this;
    }

    // Default constructor for R2DBC
    public OrderEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.isNew = true;
    }
    
    private OrderEntity(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID();
        this.userId = Objects.requireNonNull(builder.userId, "userId is required");
        this.serviceId = Objects.requireNonNull(builder.serviceId, "serviceId is required");
        this.serviceName = builder.serviceName;
        this.quantity = Objects.requireNonNull(builder.quantity, "quantity is required");
        this.delivered = builder.delivered != null ? builder.delivered : 0;
        this.remains = builder.remains != null ? builder.remains : builder.quantity;
        this.targetUrl = Objects.requireNonNull(builder.targetUrl, "targetUrl is required");
        this.geoProfile = builder.geoProfile != null ? builder.geoProfile : GeoProfile.WORLDWIDE.name();
        this.speedMultiplier = builder.speedMultiplier != null ? builder.speedMultiplier : 1.0;
        this.status = builder.status != null ? builder.status : OrderStatus.PENDING.name();
        this.pricePerUnit = builder.pricePerUnit;
        this.totalCost = Objects.requireNonNull(builder.totalCost, "totalCost is required");
        this.refundAmount = builder.refundAmount != null ? builder.refundAmount : BigDecimal.ZERO;
        this.startCount = builder.startCount;
        this.currentCount = builder.currentCount;
        this.createdAt = Instant.now();
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.failureReason = builder.failureReason;
        this.internalNotes = builder.internalNotes;
        this.externalOrderId = builder.externalOrderId;
        this.webhookDelivered = builder.webhookDelivered != null ? builder.webhookDelivered : false;
        this.estimatedCompletionAt = builder.estimatedCompletionAt;
        this.isNew = true;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getServiceId() { return serviceId; }
    public Integer getQuantity() { return quantity; }
    public Integer getDelivered() { return delivered; }
    public String getTargetUrl() { return targetUrl; }
    public String getGeoProfile() { return geoProfile; }
    public GeoProfile getGeoProfileEnum() { return GeoProfile.valueOf(geoProfile); }
    public Double getSpeedMultiplier() { return speedMultiplier; }
    public String getStatus() { return status; }
    public OrderStatus getStatusEnum() { return OrderStatus.valueOf(status); }
    public String getServiceName() { return serviceName; }
    public BigDecimal getPricePerUnit() { return pricePerUnit; }
    public BigDecimal getTotalCost() { return totalCost; }
    @Deprecated public BigDecimal getCost() { return totalCost; } // Alias for backward compat
    public Integer getRemains() { return remains; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    @Nullable public Integer getStartCount() { return startCount; }
    @Nullable public Integer getCurrentCount() { return currentCount; }
    public Instant getCreatedAt() { return createdAt; }
    @Nullable public Instant getStartedAt() { return startedAt; }
    @Nullable public Instant getCompletedAt() { return completedAt; }
    @Nullable public String getFailureReason() { return failureReason; }
    @Nullable public String getInternalNotes() { return internalNotes; }
    @Nullable public String getExternalOrderId() { return externalOrderId; }
    public Boolean getWebhookDelivered() { return webhookDelivered; }
    @Nullable public Instant getEstimatedCompletionAt() { return estimatedCompletionAt; }
    public Integer getFailedPermanentPlays() { return failedPermanentPlays; }
    public Boolean getUsesTaskDelivery() { return usesTaskDelivery; }
    
    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public void setServiceId(UUID serviceId) { this.serviceId = serviceId; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public void setDelivered(Integer delivered) { this.delivered = delivered; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }
    public void setGeoProfile(String geoProfile) { this.geoProfile = geoProfile; }
    public void setGeoProfile(GeoProfile geoProfile) { this.geoProfile = geoProfile.name(); }
    public void setSpeedMultiplier(Double speedMultiplier) { this.speedMultiplier = speedMultiplier; }
    public void setStatus(String status) { this.status = status; }
    public void setStatus(OrderStatus status) { this.status = status.name(); }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public void setPricePerUnit(BigDecimal pricePerUnit) { this.pricePerUnit = pricePerUnit; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    @Deprecated public void setCost(BigDecimal cost) { this.totalCost = cost; } // Alias
    public void setRemains(Integer remains) { this.remains = remains; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public void setStartCount(@Nullable Integer startCount) { this.startCount = startCount; }
    public void setCurrentCount(@Nullable Integer currentCount) { this.currentCount = currentCount; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setStartedAt(@Nullable Instant startedAt) { this.startedAt = startedAt; }
    public void setCompletedAt(@Nullable Instant completedAt) { this.completedAt = completedAt; }
    public void setFailureReason(@Nullable String failureReason) { this.failureReason = failureReason; }
    public void setInternalNotes(@Nullable String internalNotes) { this.internalNotes = internalNotes; }
    public void setExternalOrderId(@Nullable String externalOrderId) { this.externalOrderId = externalOrderId; }
    public void setWebhookDelivered(Boolean webhookDelivered) { this.webhookDelivered = webhookDelivered; }
    public void setEstimatedCompletionAt(@Nullable Instant estimatedCompletionAt) { this.estimatedCompletionAt = estimatedCompletionAt; }
    public void setFailedPermanentPlays(Integer failedPermanentPlays) { this.failedPermanentPlays = failedPermanentPlays; }
    public void setUsesTaskDelivery(Boolean usesTaskDelivery) { this.usesTaskDelivery = usesTaskDelivery; }
    
    // Domain methods
    
    /**
     * Transition order to RUNNING state
     */
    public void startProcessing() {
        if (!OrderStatus.PENDING.name().equals(this.status)) {
            throw new IllegalStateException("Order must be PENDING to start processing");
        }
        this.status = OrderStatus.RUNNING.name();
        this.startedAt = Instant.now();
    }
    
    /**
     * Update delivery progress
     */
    public void updateProgress(int newDelivered) {
        if (newDelivered < this.delivered) {
            throw new IllegalArgumentException("Delivered count cannot decrease");
        }
        if (newDelivered > this.quantity) {
            throw new IllegalArgumentException("Delivered cannot exceed quantity");
        }
        this.delivered = newDelivered;
        
        if (this.delivered >= this.quantity) {
            this.complete();
        }
    }
    
    /**
     * Complete the order
     */
    public void complete() {
        this.status = OrderStatus.COMPLETED.name();
        this.completedAt = Instant.now();
    }
    
    /**
     * Mark order as partially completed
     */
    public void markPartial(String reason) {
        this.status = OrderStatus.PARTIAL.name();
        this.completedAt = Instant.now();
        this.failureReason = reason;
    }
    
    /**
     * Fail the order with reason
     */
    public void fail(String reason) {
        this.status = OrderStatus.FAILED.name();
        this.completedAt = Instant.now();
        this.failureReason = reason;
    }
    
    /**
     * Process refund
     */
    public void refund(BigDecimal amount) {
        this.refundAmount = amount;
        this.status = OrderStatus.REFUNDED.name();
    }
    
    /**
     * Calculate delivery progress percentage
     */
    public double getProgressPercent() {
        if (this.quantity == 0) return 0.0;
        return (double) this.delivered / this.quantity * 100.0;
    }
    
    /**
     * Check if order is in a terminal state
     */
    public boolean isTerminal() {
        return OrderStatus.COMPLETED.name().equals(this.status) ||
               OrderStatus.FAILED.name().equals(this.status) ||
               OrderStatus.REFUNDED.name().equals(this.status) ||
               OrderStatus.CANCELLED.name().equals(this.status);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderEntity that = (OrderEntity) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "OrderEntity{" +
            "id=" + id +
            ", userId=" + userId +
            ", serviceId=" + serviceId +
            ", quantity=" + quantity +
            ", delivered=" + delivered +
            ", status='" + status + '\'' +
            ", geoProfile='" + geoProfile + '\'' +
            ", totalCost=" + totalCost +
            ", createdAt=" + createdAt +
            '}';
    }
    
    // Builder
    public static class Builder {
        private UUID id;
        private UUID userId;
        private UUID serviceId;
        private String serviceName;
        private Integer quantity;
        private Integer delivered;
        private Integer remains;
        private String targetUrl;
        private String geoProfile;
        private Double speedMultiplier;
        private String status;
        private BigDecimal pricePerUnit;
        private BigDecimal totalCost;
        private BigDecimal refundAmount;
        private Integer startCount;
        private Integer currentCount;
        private Instant startedAt;
        private Instant completedAt;
        private String failureReason;
        private String internalNotes;
        private String externalOrderId;
        private Boolean webhookDelivered;
        private Instant estimatedCompletionAt;
        
        public Builder id(UUID id) { this.id = id; return this; }
        public Builder userId(UUID userId) { this.userId = userId; return this; }
        public Builder serviceId(UUID serviceId) { this.serviceId = serviceId; return this; }
        public Builder serviceName(String serviceName) { this.serviceName = serviceName; return this; }
        public Builder quantity(Integer quantity) { this.quantity = quantity; return this; }
        public Builder delivered(Integer delivered) { this.delivered = delivered; return this; }
        public Builder remains(Integer remains) { this.remains = remains; return this; }
        public Builder targetUrl(String targetUrl) { this.targetUrl = targetUrl; return this; }
        public Builder geoProfile(String geoProfile) { this.geoProfile = geoProfile; return this; }
        public Builder geoProfile(GeoProfile geoProfile) { this.geoProfile = geoProfile.name(); return this; }
        public Builder speedMultiplier(Double speedMultiplier) { this.speedMultiplier = speedMultiplier; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder status(OrderStatus status) { this.status = status.name(); return this; }
        public Builder pricePerUnit(BigDecimal pricePerUnit) { this.pricePerUnit = pricePerUnit; return this; }
        public Builder totalCost(BigDecimal totalCost) { this.totalCost = totalCost; return this; }
        @Deprecated public Builder cost(BigDecimal cost) { this.totalCost = cost; return this; }
        public Builder refundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; return this; }
        public Builder startCount(Integer startCount) { this.startCount = startCount; return this; }
        public Builder currentCount(Integer currentCount) { this.currentCount = currentCount; return this; }
        public Builder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public Builder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }
        public Builder failureReason(String failureReason) { this.failureReason = failureReason; return this; }
        public Builder internalNotes(String internalNotes) { this.internalNotes = internalNotes; return this; }
        public Builder externalOrderId(String externalOrderId) { this.externalOrderId = externalOrderId; return this; }
        public Builder webhookDelivered(Boolean webhookDelivered) { this.webhookDelivered = webhookDelivered; return this; }
        public Builder estimatedCompletionAt(Instant estimatedCompletionAt) { this.estimatedCompletionAt = estimatedCompletionAt; return this; }
        
        public OrderEntity build() {
            return new OrderEntity(this);
        }
    }
}
