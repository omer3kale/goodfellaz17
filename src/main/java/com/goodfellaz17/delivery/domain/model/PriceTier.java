package com.goodfellaz17.delivery.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * PriceTier aggregate root — pricing model for a service.
 * 
 * Example: Service=SPOTIFY_PLAYS, Tier=Standard, Cost=0.13 USD per 1,000 plays.
 * 
 * Enforces:
 * - Unique combination of (service_id, tier_name)
 * - Positive unit_cost and quantity_step
 * - Min/max quantity bounds
 */
public class PriceTier {
    
    private final UUID id;
    private final UUID serviceId;
    private final String tierName;
    
    private BigDecimal unitCost;
    private int quantityStep; // 1 for per-unit, 1000 for per-1k, etc.
    private int minQuantity;
    private int maxQuantity;
    private boolean active;
    
    private final Instant createdAt;
    private Instant updatedAt;
    
    public PriceTier(UUID id, UUID serviceId, String tierName, BigDecimal unitCost) {
        this.id = id;
        this.serviceId = serviceId;
        this.tierName = tierName;
        this.unitCost = unitCost;
        this.quantityStep = 1;
        this.minQuantity = 1;
        this.maxQuantity = 1000000;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    public UUID getId() {
        return id;
    }
    
    public UUID getServiceId() {
        return serviceId;
    }
    
    public String getTierName() {
        return tierName;
    }
    
    public BigDecimal getUnitCost() {
        return unitCost;
    }
    
    public void setUnitCost(BigDecimal unitCost) {
        if (unitCost.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Unit cost must be > 0");
        }
        this.unitCost = unitCost;
        this.updatedAt = Instant.now();
    }
    
    public int getQuantityStep() {
        return quantityStep;
    }
    
    public void setQuantityStep(int step) {
        if (step <= 0) {
            throw new IllegalArgumentException("Quantity step must be > 0");
        }
        this.quantityStep = step;
        this.updatedAt = Instant.now();
    }
    
    public int getMinQuantity() {
        return minQuantity;
    }
    
    public int getMaxQuantity() {
        return maxQuantity;
    }
    
    public void setQuantityBounds(int min, int max) {
        if (min <= 0 || max <= 0) {
            throw new IllegalArgumentException("Quantities must be > 0");
        }
        if (min > max) {
            throw new IllegalArgumentException("Min must be <= max");
        }
        this.minQuantity = min;
        this.maxQuantity = max;
        this.updatedAt = Instant.now();
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }
    
    public void reactivate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }
    
    /**
     * Calculate total cost for a given quantity.
     * Example: quantity=5000, step=1000, cost=0.13 → 5000/1000 * 0.13 = 0.65
     */
    public BigDecimal calculateCost(int quantity) {
        if (quantity < minQuantity || quantity > maxQuantity) {
            throw new IllegalArgumentException(
                "Quantity " + quantity + " outside bounds [" + minQuantity + ", " + maxQuantity + "]"
            );
        }
        
        // Calculate cost based on step
        int steps = quantity / quantityStep;
        if (quantity % quantityStep != 0) {
            steps++; // Round up for partial steps
        }
        
        return unitCost.multiply(BigDecimal.valueOf(steps));
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    @Override
    public String toString() {
        return "PriceTier{" +
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
