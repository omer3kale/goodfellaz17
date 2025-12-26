package com.goodfellaz17.infrastructure.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Service Entity - 12 package tiers.
 * Maps to services table for R2DBC.
 * Matches existing Neon schema exactly.
 */
@Table("services")
public class ServiceEntity {

    @Id
    @Column("service_id")
    private Integer serviceId;

    @Column("name")
    private String name;

    @Column("category")
    private String category;

    @Column("rate")
    private BigDecimal rate;  // Price per 1000 (called 'rate' in existing schema)

    @Column("min_quantity")
    private Integer minQuantity;

    @Column("max_quantity")
    private Integer maxQuantity;

    @Column("drip_feed")
    private Boolean dripFeed;

    @Column("retention_days")
    private Integer retentionDays;

    @Column("description")
    private String description;

    @Column("delivery_strategy")
    private String deliveryStrategy;

    @Column("created_at")
    private Instant createdAt;

    // Default constructor for R2DBC
    public ServiceEntity() {}

    // Getters and Setters
    public Integer getServiceId() { return serviceId; }
    public Integer getId() { return serviceId; }  // Alias for compatibility
    public void setServiceId(Integer serviceId) { this.serviceId = serviceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getRate() { return rate; }
    public BigDecimal getPricePer1000() { return rate; }  // Alias for compatibility
    public void setRate(BigDecimal rate) { this.rate = rate; }

    public Integer getMinQuantity() { return minQuantity; }
    public void setMinQuantity(Integer minQuantity) { this.minQuantity = minQuantity; }

    public Integer getMaxQuantity() { return maxQuantity; }
    public void setMaxQuantity(Integer maxQuantity) { this.maxQuantity = maxQuantity; }

    public Boolean getDripFeed() { return dripFeed; }
    public void setDripFeed(Boolean dripFeed) { this.dripFeed = dripFeed; }

    public Integer getRetentionDays() { return retentionDays; }
    public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDeliveryStrategy() { return deliveryStrategy; }
    public void setDeliveryStrategy(String deliveryStrategy) { this.deliveryStrategy = deliveryStrategy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    // Computed properties for dashboard compatibility
    public Integer getDeliveryHours() {
        // Estimate based on delivery strategy
        if ("lightning".equalsIgnoreCase(deliveryStrategy)) return 12;
        if ("stealth".equalsIgnoreCase(deliveryStrategy)) return 48;
        if ("ultra".equalsIgnoreCase(deliveryStrategy)) return 96;
        return 24; // default
    }

    public String getSpeedTier() {
        return deliveryStrategy != null ? deliveryStrategy.toUpperCase() : "STANDARD";
    }

    public String getNeonColor() {
        if ("lightning".equalsIgnoreCase(deliveryStrategy)) return "#00ff88";
        if ("stealth".equalsIgnoreCase(deliveryStrategy)) return "#00d4ff";
        if ("ultra".equalsIgnoreCase(deliveryStrategy)) return "#ff0080";
        return "#00ff88";
    }

    public String getGeoTarget() {
        return "WORLDWIDE";  // Default
    }
}
