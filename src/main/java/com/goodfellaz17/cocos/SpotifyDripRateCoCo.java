package com.goodfellaz17.cocos;

import com.goodfellaz17.cocos.order.OrderContext;

/**
 * CoCo: Spotify drip rate must not exceed 5% hourly spike.
 * 
 * Manual Ch.10: Domain-specific semantic constraints.
 * Validates Spotify compliance for detection avoidance.
 */
public class SpotifyDripRateCoCo implements CoCo<OrderContext> {
    
    public static final String ERROR_CODE = "0xGFL02";
    
    /**
     * Maximum hourly spike percentage (Spotify detection threshold).
     */
    private static final double MAX_HOURLY_SPIKE = 0.05;  // 5%
    
    /**
     * Minimum delivery hours for drip feed.
     */
    private static final int MIN_DRIP_HOURS = 24;
    
    @Override
    public void check(OrderContext context) throws CoCoViolationException {
        int quantity = context.quantity();
        int deliveryHours = context.deliveryHours();
        
        // Calculate hourly rate
        double hourlyRate = (double) quantity / deliveryHours;
        
        // Spotify average monthly listeners baseline
        // For new tracks, assume baseline of 1000
        int baseline = Math.max(1000, context.existingPlays());
        
        // Calculate spike percentage
        double spikePercentage = hourlyRate / baseline;
        
        if (spikePercentage > MAX_HOURLY_SPIKE) {
            throw new CoCoViolationException(
                    ERROR_CODE,
                    String.format("Hourly spike %.2f%% exceeds Spotify safe limit %.2f%%. " +
                            "Recommend spreading %d plays over %d+ hours.",
                            spikePercentage * 100, MAX_HOURLY_SPIKE * 100,
                            quantity, calculateSafeHours(quantity, baseline)),
                    "deliveryHours",
                    deliveryHours
            );
        }
    }
    
    /**
     * Calculate minimum safe delivery hours.
     */
    private int calculateSafeHours(int quantity, int baseline) {
        return (int) Math.ceil(quantity / (baseline * MAX_HOURLY_SPIKE));
    }
    
    @Override
    public String getErrorCode() {
        return ERROR_CODE;
    }
    
    @Override
    public String getDescription() {
        return "Spotify drip rate must not exceed 5% hourly spike";
    }
}
