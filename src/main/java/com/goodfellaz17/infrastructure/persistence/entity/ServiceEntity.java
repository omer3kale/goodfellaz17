package com.goodfellaz17.infrastructure.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Service Entity - 12 package tiers.
 * Maps to services table for R2DBC.
 */
@Table("services")
public class ServiceEntity {

    @Id
    private Integer id;

    @Column("service_id")
    private String serviceId;

    @Column("name")
    private String name;

    @Column("category")
    private String category;

    @Column("price_per_1000")
    private BigDecimal pricePer1000;

    @Column("delivery_hours")
    private Integer deliveryHours;

    @Column("min_quantity")
    private Integer minQuantity;

    @Column("max_quantity")
    private Integer maxQuantity;

    @Column("neon_color")
    private String neonColor;

    @Column("speed_tier")
    private String speedTier;

    @Column("geo_target")
    private String geoTarget;

    @Column("description")
    private String description;

    @Column("is_active")
    private Boolean isActive;

    @Column("created_at")
    private Instant createdAt;

    // Default constructor for R2DBC
    public ServiceEntity() {}

    // Getters and Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getPricePer1000() { return pricePer1000; }
    public void setPricePer1000(BigDecimal pricePer1000) { this.pricePer1000 = pricePer1000; }

    public Integer getDeliveryHours() { return deliveryHours; }
    public void setDeliveryHours(Integer deliveryHours) { this.deliveryHours = deliveryHours; }

    public Integer getMinQuantity() { return minQuantity; }
    public void setMinQuantity(Integer minQuantity) { this.minQuantity = minQuantity; }

    public Integer getMaxQuantity() { return maxQuantity; }
    public void setMaxQuantity(Integer maxQuantity) { this.maxQuantity = maxQuantity; }

    public String getNeonColor() { return neonColor; }
    public void setNeonColor(String neonColor) { this.neonColor = neonColor; }

    public String getSpeedTier() { return speedTier; }
    public void setSpeedTier(String speedTier) { this.speedTier = speedTier; }

    public String getGeoTarget() { return geoTarget; }
    public void setGeoTarget(String geoTarget) { this.geoTarget = geoTarget; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
