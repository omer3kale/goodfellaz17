package com.goodfellaz17.domain.port;

import com.goodfellaz17.domain.exception.NoCapacityException;
import com.goodfellaz17.domain.model.OrderContext;
import com.goodfellaz17.domain.model.ProxyLease;
import com.goodfellaz17.domain.model.RoutingProfile;

/**
 * Port interface for proxy traffic sources.
 * Each implementation represents a different traffic routing method:
 * - AWS/Cloud (premium geo-targeted)
 * - Tor (high-volume global)
 * - Mobile/Phone Farm (elite mobile-like behavior)
 * - P2P/Residential (mid-tier natural traffic)
 * 
 * Configuration-driven: all capacity, cost, and endpoints come from config,
 * not hard-coded values.
 */
public interface ProxySource {
    
    /**
     * Unique identifier for this source (e.g., "aws", "tor", "mobile", "p2p").
     */
    String getName();
    
    /**
     * Human-readable display name for logging/monitoring.
     */
    String getDisplayName();
    
    /**
     * Check if this source is currently enabled in configuration.
     */
    boolean isEnabled();
    
    /**
     * Check if this source can serve traffic for the given country.
     * @param country ISO country code (e.g., "US", "UK") or "GLOBAL"
     * @return true if this source supports the specified geo
     */
    boolean supportsGeo(String country);
    
    /**
     * Check if this source is appropriate for the given routing profile.
     * @param profile The routing requirements for the service
     * @return true if this source can handle the profile's requirements
     */
    boolean supportsProfile(RoutingProfile profile);
    
    /**
     * Get the operator-configured capacity per day.
     * This is NOT a hard-coded fantasy number - it comes from config.
     * @return estimated daily capacity in requests
     */
    int getEstimatedCapacityPerDay();
    
    /**
     * Get the current remaining capacity for today.
     * @return remaining capacity, or 0 if exhausted
     */
    int getRemainingCapacity();
    
    /**
     * Check if this source has any capacity available.
     */
    default boolean hasCapacity() {
        return isEnabled() && getRemainingCapacity() > 0;
    }
    
    /**
     * Get the cost per 1000 requests (for routing optimization).
     * Used by the hybrid strategy to prefer cheaper sources when appropriate.
     * @return cost in arbitrary units (configured by operator)
     */
    double getCostPer1k();
    
    /**
     * Risk level of this source (0.0 = safest, 1.0 = highest risk).
     * Used to exclude risky sources for sensitive services.
     */
    double getRiskLevel();
    
    /**
     * Whether this source is considered "premium" (more reliable/safer).
     */
    boolean isPremium();
    
    /**
     * Acquire a proxy lease for the given order context.
     * @param ctx Order context with routing requirements
     * @return A valid proxy lease
     * @throws NoCapacityException if no capacity is available
     */
    ProxyLease acquire(OrderContext ctx) throws NoCapacityException;
    
    /**
     * Release a previously acquired lease.
     * Called when the request is complete (success or failure).
     * @param lease The lease to release
     */
    void release(ProxyLease lease);
    
    /**
     * Get current usage statistics for monitoring.
     */
    SourceStats getStats();
    
    /**
     * Statistics record for monitoring.
     */
    record SourceStats(
        String sourceName,
        int totalCapacity,
        int usedToday,
        int remainingCapacity,
        int activeLeases,
        double successRate,
        long lastAcquireTimeMs
    ) {
        public static SourceStats empty(String name) {
            return new SourceStats(name, 0, 0, 0, 0, 0.0, 0);
        }
    }
}
