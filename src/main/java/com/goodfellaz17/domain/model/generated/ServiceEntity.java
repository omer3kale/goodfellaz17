package com.goodfellaz17.domain.model.generated;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * Entity: Service
 * Table: services
 * 
 * Spotify engagement service catalog with tiered pricing.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
@Table("services")
public class ServiceEntity implements Serializable, Persistable<UUID> {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    @NotNull
    private UUID id;
    
    @NotNull
    @Size(max = 100)
    @Column("name")
    private String name;
    
    @NotNull
    @Size(max = 255)
    @Column("display_name")
    private String displayName;
    
    @NotNull
    @Column("service_type")
    private String serviceType;
    
    @Nullable
    @Size(max = 1000)
    @Column("description")
    private String description;
    
    @NotNull
    @DecimalMin("0.01")
    @DecimalMax("999.99")
    @Column("cost_per_1k")
    private BigDecimal costPer1k;
    
    @NotNull
    @DecimalMin("0.01")
    @DecimalMax("999.99")
    @Column("reseller_cost_per_1k")
    private BigDecimal resellerCostPer1k;
    
    @NotNull
    @DecimalMin("0.01")
    @DecimalMax("999.99")
    @Column("agency_cost_per_1k")
    private BigDecimal agencyCostPer1k;
    
    @NotNull
    @Min(1)
    @Max(1000000)
    @Column("min_quantity")
    private Integer minQuantity = 100;
    
    @NotNull
    @Min(1)
    @Max(10000000)
    @Column("max_quantity")
    private Integer maxQuantity = 1000000;
    
    @NotNull
    @Column("estimated_days_min")
    private Integer estimatedDaysMin = 1;
    
    @NotNull
    @Column("estimated_days_max")
    private Integer estimatedDaysMax = 7;
    
    @NotNull
    @Column("geo_profiles")
    private String geoProfiles; // JSON array
    
    @NotNull
    @Column("is_active")
    private Boolean isActive = true;
    
    @NotNull
    @Column("sort_order")
    private Integer sortOrder = 0;
    
    @NotNull
    @CreatedDate
    @Column("created_at")
    private Instant createdAt = Instant.now();
    
    @NotNull
    @LastModifiedDate
    @Column("updated_at")
    private Instant updatedAt = Instant.now();
    
    @Transient
    private boolean isNew = true;
    
    @Override
    public boolean isNew() {
        return isNew;
    }
    
    public ServiceEntity markNotNew() {
        this.isNew = false;
        return this;
    }

    public ServiceEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.isNew = true;
    }
    
    private ServiceEntity(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID();
        this.name = Objects.requireNonNull(builder.name);
        this.displayName = Objects.requireNonNull(builder.displayName);
        this.serviceType = Objects.requireNonNull(builder.serviceType);
        this.description = builder.description;
        this.costPer1k = Objects.requireNonNull(builder.costPer1k);
        this.resellerCostPer1k = builder.resellerCostPer1k != null ? builder.resellerCostPer1k : builder.costPer1k.multiply(new BigDecimal("0.8"));
        this.agencyCostPer1k = builder.agencyCostPer1k != null ? builder.agencyCostPer1k : builder.costPer1k.multiply(new BigDecimal("0.6"));
        this.minQuantity = builder.minQuantity != null ? builder.minQuantity : 100;
        this.maxQuantity = builder.maxQuantity != null ? builder.maxQuantity : 1000000;
        this.estimatedDaysMin = builder.estimatedDaysMin != null ? builder.estimatedDaysMin : 1;
        this.estimatedDaysMax = builder.estimatedDaysMax != null ? builder.estimatedDaysMax : 7;
        this.geoProfiles = builder.geoProfiles != null ? builder.geoProfiles : "[\"WORLDWIDE\"]";
        this.isActive = builder.isActive != null ? builder.isActive : true;
        this.sortOrder = builder.sortOrder != null ? builder.sortOrder : 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.isNew = true;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public String getServiceType() { return serviceType; }
    public ServiceType getServiceTypeEnum() { return ServiceType.valueOf(serviceType); }
    @Nullable public String getDescription() { return description; }
    public BigDecimal getCostPer1k() { return costPer1k; }
    public BigDecimal getResellerCostPer1k() { return resellerCostPer1k; }
    public BigDecimal getAgencyCostPer1k() { return agencyCostPer1k; }
    public Integer getMinQuantity() { return minQuantity; }
    public Integer getMaxQuantity() { return maxQuantity; }
    public Integer getEstimatedDaysMin() { return estimatedDaysMin; }
    public Integer getEstimatedDaysMax() { return estimatedDaysMax; }
    public String getGeoProfiles() { return geoProfiles; }
    public Boolean getIsActive() { return isActive; }
    public Integer getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    
    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public void setServiceType(ServiceType serviceType) { this.serviceType = serviceType.name(); }
    public void setDescription(@Nullable String description) { this.description = description; }
    public void setCostPer1k(BigDecimal costPer1k) { this.costPer1k = costPer1k; }
    public void setResellerCostPer1k(BigDecimal resellerCostPer1k) { this.resellerCostPer1k = resellerCostPer1k; }
    public void setAgencyCostPer1k(BigDecimal agencyCostPer1k) { this.agencyCostPer1k = agencyCostPer1k; }
    public void setMinQuantity(Integer minQuantity) { this.minQuantity = minQuantity; }
    public void setMaxQuantity(Integer maxQuantity) { this.maxQuantity = maxQuantity; }
    public void setEstimatedDaysMin(Integer estimatedDaysMin) { this.estimatedDaysMin = estimatedDaysMin; }
    public void setEstimatedDaysMax(Integer estimatedDaysMax) { this.estimatedDaysMax = estimatedDaysMax; }
    public void setGeoProfiles(String geoProfiles) { this.geoProfiles = geoProfiles; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    // Domain methods
    
    /**
     * Get cost for a user tier.
     */
    public BigDecimal getCostForTier(UserTier tier) {
        return switch (tier) {
            case CONSUMER -> costPer1k;
            case RESELLER -> resellerCostPer1k;
            case AGENCY -> agencyCostPer1k;
        };
    }
    
    /**
     * Calculate total cost for quantity.
     */
    public BigDecimal calculateCost(int quantity, UserTier tier) {
        BigDecimal costPer1k = getCostForTier(tier);
        return costPer1k.multiply(new BigDecimal(quantity))
            .divide(new BigDecimal(1000), 2, java.math.RoundingMode.HALF_UP);
    }
    
    /**
     * Validate quantity is within bounds.
     */
    public boolean isValidQuantity(int quantity) {
        return quantity >= minQuantity && quantity <= maxQuantity;
    }
    
    /**
     * Get estimated delivery range as string.
     */
    public String getEstimatedDelivery() {
        if (estimatedDaysMin.equals(estimatedDaysMax)) {
            return estimatedDaysMin + " day" + (estimatedDaysMin == 1 ? "" : "s");
        }
        return estimatedDaysMin + "-" + estimatedDaysMax + " days";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceEntity that = (ServiceEntity) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "ServiceEntity{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", serviceType='" + serviceType + '\'' +
            ", costPer1k=" + costPer1k +
            ", isActive=" + isActive +
            '}';
    }
    
    public static class Builder {
        private UUID id;
        private String name;
        private String displayName;
        private String serviceType;
        private String description;
        private BigDecimal costPer1k;
        private BigDecimal resellerCostPer1k;
        private BigDecimal agencyCostPer1k;
        private Integer minQuantity;
        private Integer maxQuantity;
        private Integer estimatedDaysMin;
        private Integer estimatedDaysMax;
        private String geoProfiles;
        private Boolean isActive;
        private Integer sortOrder;
        
        public Builder id(UUID id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder serviceType(String serviceType) { this.serviceType = serviceType; return this; }
        public Builder serviceType(ServiceType serviceType) { this.serviceType = serviceType.name(); return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder costPer1k(BigDecimal costPer1k) { this.costPer1k = costPer1k; return this; }
        public Builder resellerCostPer1k(BigDecimal resellerCostPer1k) { this.resellerCostPer1k = resellerCostPer1k; return this; }
        public Builder agencyCostPer1k(BigDecimal agencyCostPer1k) { this.agencyCostPer1k = agencyCostPer1k; return this; }
        public Builder minQuantity(Integer minQuantity) { this.minQuantity = minQuantity; return this; }
        public Builder maxQuantity(Integer maxQuantity) { this.maxQuantity = maxQuantity; return this; }
        public Builder estimatedDaysMin(Integer estimatedDaysMin) { this.estimatedDaysMin = estimatedDaysMin; return this; }
        public Builder estimatedDaysMax(Integer estimatedDaysMax) { this.estimatedDaysMax = estimatedDaysMax; return this; }
        public Builder geoProfiles(String geoProfiles) { this.geoProfiles = geoProfiles; return this; }
        public Builder isActive(Boolean isActive) { this.isActive = isActive; return this; }
        public Builder sortOrder(Integer sortOrder) { this.sortOrder = sortOrder; return this; }
        
        public ServiceEntity build() {
            return new ServiceEntity(this);
        }
    }
}
