package com.spotifybot.domain.model;

/**
 * Domain Value Object - Delivery speed tier.
 * 
 * Controls drip schedule and concurrent bot allocation.
 */
public enum SpeedTier {
    NORMAL(72, 1.0),    // 72 hours, 1x concurrent
    FAST(24, 2.0),      // 24 hours, 2x concurrent
    VIP(12, 4.0);       // 12 hours, 4x concurrent

    private final int deliveryHours;
    private final double concurrencyMultiplier;

    SpeedTier(int deliveryHours, double concurrencyMultiplier) {
        this.deliveryHours = deliveryHours;
        this.concurrencyMultiplier = concurrencyMultiplier;
    }

    public int getDeliveryHours() {
        return deliveryHours;
    }

    public double getConcurrencyMultiplier() {
        return concurrencyMultiplier;
    }
}
