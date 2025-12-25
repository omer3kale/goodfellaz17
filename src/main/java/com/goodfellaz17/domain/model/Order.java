package com.goodfellaz17.domain.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Domain Aggregate Root - Order entity.
 * 
 * R2DBC mapped to Neon PostgreSQL table: orders
 * 
 * Encapsulates business rules for Spotify stream orders:
 * - Minimum 35s play duration for royalty eligibility
 * - Maximum 5% hourly spike to avoid detection
 * - Geo-targeted proxy/account selection
 */
@Table("orders")
public class Order {

    @Id
    private UUID id;
    
    @Column("service_id")
    private String serviceId;      // e.g., "plays_usa", "followers_premium"
    
    @Column("service_name")
    private String serviceName;    // Human readable, e.g., "USA Plays Premium"
    
    @Column("track_url")
    private String trackUrl;
    
    private int quantity;
    
    @Column("geo_target")
    private String geoTarget;
    
    @Column("speed_tier")
    private String speedTier;
    
    private int delivered;
    private String status;
    
    @Column("created_at")
    private Instant createdAt;
    
    @Column("completed_at")
    private Instant completedAt;
    
    @Column("rate_per_thousand")
    private Double ratePerThousand;

    // Default constructor for R2DBC
    public Order() {
        this.createdAt = Instant.now();
    }

    private Order(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID();
        this.serviceId = builder.serviceId != null ? builder.serviceId : "plays_ww";
        this.serviceName = builder.serviceName != null ? builder.serviceName : "Worldwide Plays";
        this.trackUrl = builder.trackUrl;
        this.quantity = builder.quantity;
        this.geoTarget = builder.geoTarget != null ? builder.geoTarget.name() : "WORLDWIDE";
        this.speedTier = builder.speedTier != null ? builder.speedTier.name() : "NORMAL";
        this.delivered = builder.delivered;
        this.status = builder.status != null ? builder.status.name() : "PENDING";
        this.createdAt = Instant.now();
    }

    public Order(UUID id, String trackUrl, int quantity, GeoTarget geoTarget, SpeedTier speedTier) {
        this.id = id;
        this.serviceId = "plays_ww";
        this.serviceName = "Worldwide Plays";
        this.trackUrl = trackUrl;
        this.quantity = quantity;
        this.geoTarget = geoTarget != null ? geoTarget.name() : "WORLDWIDE";
        this.speedTier = speedTier != null ? speedTier.name() : "NORMAL";
        this.delivered = 0;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Order create(String trackUrl, int quantity, GeoTarget geoTarget, SpeedTier speedTier) {
        return new Order(UUID.randomUUID(), trackUrl, quantity, geoTarget, speedTier);
    }

    public void startProcessing() {
        if (!"PENDING".equals(this.status)) {
            throw new IllegalStateException("Order must be PENDING to start processing");
        }
        this.status = "PROCESSING";
    }

    public void addDelivered(int count) {
        this.delivered += count;
        if (this.delivered >= this.quantity) {
            this.status = "COMPLETED";
            this.completedAt = Instant.now();
        }
    }

    public void cancel() {
        if ("COMPLETED".equals(this.status)) {
            throw new IllegalStateException("Cannot cancel completed order");
        }
        this.status = "CANCELLED";
    }

    public double getProgress() {
        if (quantity == 0) return 0.0;
        return (double) delivered / quantity * 100.0;
    }

    public int getRemaining() {
        return Math.max(0, quantity - delivered);
    }

    /**
     * Spotify compliance check: max 5% hourly spike.
     * 
     * @return true if order respects Spotify rate limits
     */
    public boolean isSpotifyCompliant() {
        SpeedTier tier = getSpeedTierEnum();
        // Calculate plays per hour based on speed tier
        int deliveryHours = switch (tier) {
            case VIP -> 24;
            case FAST -> 48;
            case NORMAL -> 72;
        };
        
        double playsPerHour = (double) quantity / deliveryHours;
        
        // 5% spike limit = approximately 50 plays/hour max for small orders
        // Scale with quantity (larger orders can have higher absolute rate)
        double maxHourlyRate = Math.max(50, quantity * 0.05);
        
        return playsPerHour <= maxHourlyRate;
    }

    /**
     * Decompose order into individual bot tasks.
     * 
     * Each task represents one Chrome worker assignment.
     */
    public List<BotTask> decompose() {
        List<BotTask> tasks = new ArrayList<>();
        int remaining = getRemaining();
        
        // Create one task per play (can batch in real impl)
        int batchSize = Math.min(100, remaining);
        int batches = (remaining + batchSize - 1) / batchSize;
        
        for (int i = 0; i < batches; i++) {
            // Each task handles a batch of plays
            BotTask task = BotTask.create(
                    id,
                    trackUrl,
                    null,  // Proxy assigned by orchestrator
                    null,  // Account assigned by orchestrator
                    SessionProfile.createDefault()
            );
            tasks.add(task);
        }
        
        return tasks;
    }

    // Getters
    public UUID getId() { return id; }
    public String getServiceId() { return serviceId; }
    public String getServiceName() { return serviceName; }
    public String getTrackUrl() { return trackUrl; }
    public int getQuantity() { return quantity; }
    
    public GeoTarget getGeoTarget() { 
        if (geoTarget == null) return GeoTarget.WORLDWIDE;
        try {
            return GeoTarget.valueOf(geoTarget);
        } catch (Exception e) {
            return GeoTarget.WORLDWIDE;
        }
    }
    
    public SpeedTier getSpeedTierEnum() { 
        if (speedTier == null) return SpeedTier.NORMAL;
        try {
            return SpeedTier.valueOf(speedTier);
        } catch (Exception e) {
            return SpeedTier.NORMAL;
        }
    }
    
    public SpeedTier getSpeedTier() { return getSpeedTierEnum(); }
    
    public int getDelivered() { return delivered; }
    
    public OrderStatus getStatus() { 
        if (status == null) return OrderStatus.PENDING;
        try {
            return OrderStatus.valueOf(status);
        } catch (Exception e) {
            return OrderStatus.PENDING;
        }
    }
    
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    
    // Setters for R2DBC
    public void setId(UUID id) { this.id = id; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public void setTrackUrl(String trackUrl) { this.trackUrl = trackUrl; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setGeoTarget(String geoTarget) { this.geoTarget = geoTarget; }
    public void setSpeedTier(String speedTier) { this.speedTier = speedTier; }
    public void setDelivered(int delivered) { this.delivered = delivered; }
    public void setStatus(String status) { this.status = status; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public void setRatePerThousand(Double ratePerThousand) { this.ratePerThousand = ratePerThousand; }

    // Builder
    public static class Builder {
        private UUID id;
        private String serviceId;
        private String serviceName;
        private String trackUrl;
        private int quantity;
        private int delivered = 0;
        private GeoTarget geoTarget;
        private SpeedTier speedTier;
        private OrderStatus status;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder serviceId(String serviceId) { this.serviceId = serviceId; return this; }
        public Builder serviceName(String serviceName) { this.serviceName = serviceName; return this; }
        public Builder trackUrl(String trackUrl) { this.trackUrl = trackUrl; return this; }
        public Builder quantity(int quantity) { this.quantity = quantity; return this; }
        public Builder delivered(int delivered) { this.delivered = delivered; return this; }
        public Builder geoTarget(GeoTarget geoTarget) { this.geoTarget = geoTarget; return this; }
        public Builder speedTier(SpeedTier speedTier) { this.speedTier = speedTier; return this; }
        public Builder status(OrderStatus status) { this.status = status; return this; }

        public Order build() {
            return new Order(this);
        }
    }
    
    // For backwards compatibility in tests
    @Transient
    public DripSchedule calculateDripSchedule() {
        return DripSchedule.forSpeedTier(getSpeedTierEnum(), quantity);
    }
}
