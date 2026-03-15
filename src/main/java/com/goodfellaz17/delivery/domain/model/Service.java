package com.goodfellaz17.delivery.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Service aggregate root — defines what can be delivered (PLAYS, FOLLOWERS, etc.).
 * 
 * Enforces:
 * - Unique service code (SPOTIFY_PLAYS, TIKTOK_VIEWS, etc.)
 * - Active/inactive status
 * - Quantity constraints (min/max per order)
 */
public class Service {
    
    private final UUID id;
    private final String code;
    private final String name;
    
    private String description;
    private boolean active;
    private int minQuantity;
    private int maxQuantity;
    
    private final Instant createdAt;
    private Instant updatedAt;
    
    public Service(UUID id, String code, String name) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.active = true;
        this.minQuantity = 1;
        this.maxQuantity = 1000000;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    public UUID getId() {
        return id;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
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
    
    public int getMinQuantity() {
        return minQuantity;
    }
    
    public int getMaxQuantity() {
        return maxQuantity;
    }
    
    public void setQuantityConstraints(int min, int max) {
        if (min <= 0 || max <= 0) {
            throw new IllegalArgumentException("Quantities must be > 0");
        }
        if (min > max) {
            throw new IllegalArgumentException("Min quantity must be <= max quantity");
        }
        this.minQuantity = min;
        this.maxQuantity = max;
        this.updatedAt = Instant.now();
    }
    
    /**
     * Validate a requested quantity against service constraints.
     */
    public void validateQuantity(int quantity) {
        if (quantity < minQuantity) {
            throw new IllegalArgumentException(
                "Quantity " + quantity + " below minimum " + minQuantity
            );
        }
        if (quantity > maxQuantity) {
            throw new IllegalArgumentException(
                "Quantity " + quantity + " exceeds maximum " + maxQuantity
            );
        }
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    @Override
    public String toString() {
        return "Service{" +
                "id=" + id +
                ", code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", active=" + active +
                ", minQuantity=" + minQuantity +
                ", maxQuantity=" + maxQuantity +
                ", createdAt=" + createdAt +
                '}';
    }
}
