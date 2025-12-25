package com.goodfellaz17.domain.port;

import com.goodfellaz17.domain.exception.NoCapacityException;
import com.goodfellaz17.domain.model.OrderContext;
import com.goodfellaz17.domain.model.ProxyLease;

import java.util.List;

/**
 * Port interface for proxy selection strategy.
 * Implementations decide which ProxySource to use based on order context,
 * service priority, geo requirements, cost, and capacity.
 * 
 * The strategy is configuration-driven and backend-only;
 * the frontend never knows which source handles which order.
 */
public interface ProxyStrategy {
    
    /**
     * Select and acquire a proxy for the given order context.
     * 
     * @param ctx Order context containing service, geo, quantity, priority
     * @return A valid proxy lease from the selected source
     * @throws NoCapacityException if no suitable source has capacity
     */
    ProxyLease selectProxy(OrderContext ctx) throws NoCapacityException;
    
    /**
     * Get all registered proxy sources.
     */
    List<ProxySource> getSources();
    
    /**
     * Get sources that are currently enabled and have capacity.
     */
    List<ProxySource> getAvailableSources();
    
    /**
     * Get aggregate statistics across all sources.
     */
    AggregateStats getAggregateStats();
    
    /**
     * Aggregate statistics for monitoring dashboard.
     */
    record AggregateStats(
        int totalSources,
        int enabledSources,
        int totalCapacity,
        int totalRemaining,
        int totalActiveLeases
    ) {}
}
