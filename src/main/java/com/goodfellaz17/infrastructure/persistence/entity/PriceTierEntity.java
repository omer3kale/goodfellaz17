package com.goodfellaz17.infrastructure.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * PriceTier Entity (MSSQL) — Maps to price_tiers table.
 * 
 * Represents a tiered pricing level for a specific service.
 * Example: SPOTIFY_PLAYS service with tier "Standard" at $0.13 per 1000 plays.
 */
@Table("price_tiers")
public class PriceTierEntity {

    @Id
    @Column("id")
    private UUID id;

    @Column("service_id")
    private UUID serviceId;

    @Column("tier_name")
    private String tierName;

    @Column("unit_cost")
    private BigDecimal unitCost;

    @Column("quantity_step")
    private Integer quantityStep;

    @Column("min_quantity")
    private Integer minQuantity;

    @Column("max_quantity")
    private Integer maxQuantity;

    @Column("active")
    private Boolean active;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    // ── Constructors ────────────────────────────────────

    public PriceTierEntity() {
    }

    public PriceTierEntity(UUID id, UUID serviceId, String tierName, BigDecimal unitCost,
                          Integer quantityStep, Integer minQuantity, Integer maxQuantity,
                          Boolean active, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.serviceId = serviceId;
        this.tierName = tierName;
        this.unitCost = unitCost;
        this.quantityStep = quantityStep;
        this.minQuantity = minQuantity;
        this.maxQuantity = maxQuantity;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ── Getters & Setters ────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public void setServiceId(UUID serviceId) {
        this.serviceId = serviceId;
    }

    public String getTierName() {
        return tierName;
    }

    public void setTierName(String tierName) {
        this.tierName = tierName;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public Integer getQuantityStep() {
        return quantityStep;
    }

    public void setQuantityStep(Integer quantityStep) {
        this.quantityStep = quantityStep;
    }

    public Integer getMinQuantity() {
        return minQuantity;
    }

    public void setMinQuantity(Integer minQuantity) {
        this.minQuantity = minQuantity;
    }

    public Integer getMaxQuantity() {
        return maxQuantity;
    }

    public void setMaxQuantity(Integer maxQuantity) {
        this.maxQuantity = maxQuantity;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "PriceTierEntity{" +
                "id=" + id +
                ", serviceId=" + serviceId +
                ", tierName='" + tierName + '\'' +
                ", unitCost=" + unitCost +
                ", quantityStep=" + quantityStep +
                ", minQuantity=" + minQuantity +
                ", maxQuantity=" + maxQuantity +
                ", active=" + active +
                ", createdAt=" + createdAt +
                '}';
    }
}
