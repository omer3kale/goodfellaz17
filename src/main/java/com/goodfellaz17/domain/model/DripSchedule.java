package com.goodfellaz17.domain.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain Value Object - Drip schedule for order delivery.
 * 
 * Compliance constraints:
 * - Max 5% hourly spike (Spotify detection threshold)
 * - Spread delivery over speed tier duration
 */
public record DripSchedule(
        Duration totalDuration,
        int batchSize,
        Duration batchInterval,
        double maxHourlySpike
) {
    private static final double MAX_SPIKE_LIMIT = 0.05; // 5%
    
    public static DripSchedule forSpeedTier(SpeedTier tier, int totalQuantity) {
        int hours = tier.getDeliveryHours();
        Duration total = Duration.ofHours(hours);
        
        // Calculate batch size to stay under 5% spike
        int maxHourlyDelivery = (int) (totalQuantity * MAX_SPIKE_LIMIT);
        int batchSize = Math.max(100, maxHourlyDelivery / 6); // 6 batches/hour
        
        Duration interval = Duration.ofMinutes(10); // 10min between batches
        
        return new DripSchedule(total, batchSize, interval, MAX_SPIKE_LIMIT);
    }
    
    /**
     * Decompose order into bot tasks respecting spike limits.
     */
    public List<Integer> decomposeToBatches(int totalQuantity) {
        List<Integer> batches = new ArrayList<>();
        int remaining = totalQuantity;
        
        while (remaining > 0) {
            int batch = Math.min(batchSize, remaining);
            batches.add(batch);
            remaining -= batch;
        }
        
        return batches;
    }
    
    /**
     * Validate delivery rate stays under spike limit.
     */
    public void validateSpikeLimit(int deliveredLastHour, int total) {
        double spikeRate = (double) deliveredLastHour / total;
        if (spikeRate > maxHourlySpike) {
            throw new IllegalStateException(
                    "Spike limit exceeded: " + spikeRate + " > " + maxHourlySpike);
        }
    }
}
