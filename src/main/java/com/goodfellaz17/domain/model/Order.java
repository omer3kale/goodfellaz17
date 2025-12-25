package com.goodfellaz17.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Domain Aggregate Root - Order entity.
 * 
 * Encapsulates business rules for Spotify stream orders:
 * - Minimum 35s play duration for royalty eligibility
 * - Maximum 5% hourly spike to avoid detection
 * - Geo-targeted proxy/account selection
 */
public class Order {

    private final UUID id;
    private final String serviceId;      // e.g., "plays_usa", "followers_premium"
    private final String serviceName;    // Human readable, e.g., "USA Plays Premium"
    private final String trackUrl;
    private final int quantity;
    private final GeoTarget geoTarget;
    private final SpeedTier speedTier;
    private int delivered;
    private OrderStatus status;
    private final Instant createdAt;
    private Instant completedAt;

    private Order(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID();
        this.serviceId = builder.serviceId != null ? builder.serviceId : "plays_ww";
        this.serviceName = builder.serviceName != null ? builder.serviceName : "Worldwide Plays";
        this.trackUrl = builder.trackUrl;
        this.quantity = builder.quantity;
        this.geoTarget = builder.geoTarget != null ? builder.geoTarget : GeoTarget.WORLDWIDE;
        this.speedTier = builder.speedTier != null ? builder.speedTier : SpeedTier.NORMAL;
        this.delivered = builder.delivered;
        this.status = builder.status != null ? builder.status : OrderStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public Order(UUID id, String trackUrl, int quantity, GeoTarget geoTarget, SpeedTier speedTier) {
        this.id = id;
        this.serviceId = "plays_ww";
        this.serviceName = "Worldwide Plays";
        this.trackUrl = trackUrl;
        this.quantity = quantity;
        this.geoTarget = geoTarget;
        this.speedTier = speedTier;
        this.delivered = 0;
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Order create(String trackUrl, int quantity, GeoTarget geoTarget, SpeedTier speedTier) {
        return new Order(UUID.randomUUID(), trackUrl, quantity, geoTarget, speedTier);
    }

    public void startProcessing() {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("Order must be PENDING to start processing");
        }
        this.status = OrderStatus.PROCESSING;
    }

    public void addDelivered(int count) {
        this.delivered += count;
        if (this.delivered >= this.quantity) {
            this.status = OrderStatus.COMPLETED;
            this.completedAt = Instant.now();
        }
    }

    public void cancel() {
        if (this.status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel completed order");
        }
        this.status = OrderStatus.CANCELLED;
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
        // Calculate plays per hour based on speed tier
        int deliveryHours = switch (speedTier) {
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
    public GeoTarget getGeoTarget() { return geoTarget; }
    public SpeedTier getSpeedTier() { return speedTier; }
    public int getDelivered() { return delivered; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }

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
}
