package com.spotifybot.domain.service;

import com.spotifybot.domain.model.DripSchedule;
import com.spotifybot.domain.model.Order;
import org.springframework.stereotype.Service;

/**
 * Domain Service - Spotify compliance validation.
 * 
 * Ensures all delivery parameters meet Spotify ToS thresholds
 * to avoid detection and maintain royalty eligibility.
 */
@Service
public class SpotifyComplianceService {
    
    private static final int MIN_SESSION_DURATION_SECONDS = 35;
    private static final double MAX_HOURLY_SPIKE = 0.05;
    private static final int MAX_PLAYS_PER_ACCOUNT_PER_DAY = 1000;
    
    /**
     * Validate order can be fulfilled compliantly.
     */
    public void validateOrder(Order order) {
        // Check quantity is achievable with account farm
        int requiredAccounts = calculateRequiredAccounts(order.getQuantity());
        if (requiredAccounts > 1000) {
            throw new IllegalArgumentException(
                    "Order quantity exceeds daily capacity: " + order.getQuantity());
        }
    }
    
    /**
     * Validate drip schedule respects spike limits.
     */
    public void validateDripSchedule(DripSchedule schedule, int totalQuantity) {
        schedule.validateSpikeLimit(
                (int) (totalQuantity * schedule.maxHourlySpike()), 
                totalQuantity
        );
    }
    
    /**
     * Calculate minimum accounts needed for order.
     */
    public int calculateRequiredAccounts(int quantity) {
        return (int) Math.ceil((double) quantity / MAX_PLAYS_PER_ACCOUNT_PER_DAY);
    }
    
    /**
     * Check if session duration meets royalty threshold.
     */
    public boolean isRoyaltyEligible(int durationSeconds) {
        return durationSeconds >= MIN_SESSION_DURATION_SECONDS;
    }
}
