package com.goodfellaz17.delivery.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Order aggregate root — top-level customer request for streaming delivery.
 * 
 * Maps to MSSQL orders table.
 * Enforces INV-2: status progression PENDING → ACTIVE → DELIVERING → COMPLETED/FAILED
 * Enforces INV-3: terminal states require completed_at ≠ null
 */
public class Order {
    
    private final UUID id;
    private final UUID tenantId;
    private final UUID serviceId;
    private final String trackId;
    private final String artistId;
    private final int quantity;
    
    private OrderStatus status;
    private Instant statusChangedAt;
    
    private final UUID priceTrierId;
    private BigDecimal estimatedCost;
    private BigDecimal actualCost;
    
    private int playsDelivered;
    private int playsFailed;
    private String failureReason;
    
    private final Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;

    public Order(UUID id, UUID tenantId, UUID serviceId, String trackId, 
                 String artistId, int quantity, UUID priceTrierId) {
        this.id = id;
        this.tenantId = tenantId;
        this.serviceId = serviceId;
        this.trackId = trackId;
        this.artistId = artistId;
        this.quantity = quantity;
        this.status = OrderStatus.PENDING;
        this.statusChangedAt = Instant.now();
        this.priceTrierId = priceTrierId;
        this.playsDelivered = 0;
        this.playsFailed = 0;
        this.createdAt = Instant.now();
    }

    // ── State transitions (enforcing INV-2) ────────────────
    
    public void transitionToActive() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("Can only move to ACTIVE from PENDING");
        }
        this.status = OrderStatus.ACTIVE;
        this.statusChangedAt = Instant.now();
    }

    public void transitionToDelivering() {
        if (status != OrderStatus.ACTIVE) {
            throw new IllegalStateException("Can only move to DELIVERING from ACTIVE");
        }
        if (startedAt == null) {
            this.startedAt = Instant.now();
        }
        this.status = OrderStatus.DELIVERING;
        this.statusChangedAt = Instant.now();
    }

    public void recordSuccess(int count) {
        this.playsDelivered += count;
    }

    public void recordFailure(int count) {
        this.playsFailed += count;
    }

    public void complete() {
        if (status != OrderStatus.DELIVERING) {
            throw new IllegalStateException("Can only complete from DELIVERING");
        }
        this.status = OrderStatus.COMPLETED;
        this.completedAt = Instant.now();  // INV-3
        this.statusChangedAt = completedAt;
    }

    public void fail(String reason) {
        if (status != OrderStatus.DELIVERING) {
            throw new IllegalStateException("Can only fail from DELIVERING");
        }
        this.status = OrderStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = Instant.now();  // INV-3
        this.statusChangedAt = completedAt;
    }

    public double getSuccessRate() {
        int total = playsDelivered + playsFailed;
        return total == 0 ? 0.0 : (double) playsDelivered / total;
    }

    public boolean isComplete() {
        return status == OrderStatus.COMPLETED || status == OrderStatus.FAILED;
    }

    // ── Getters ────────────────────────────────────────────
    
    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getServiceId() { return serviceId; }
    public String getTrackId() { return trackId; }
    public String getArtistId() { return artistId; }
    public int getQuantity() { return quantity; }
    public OrderStatus getStatus() { return status; }
    public UUID getPriceTrierId() { return priceTrierId; }
    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public BigDecimal getActualCost() { return actualCost; }
    public int getPlaysDelivered() { return playsDelivered; }
    public int getPlaysFailed() { return playsFailed; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    
    public void setEstimatedCost(BigDecimal cost) { this.estimatedCost = cost; }
    public void setActualCost(BigDecimal cost) { this.actualCost = cost; }

    public enum OrderStatus {
        PENDING, ACTIVE, DELIVERING, COMPLETED, FAILED
    }
}
