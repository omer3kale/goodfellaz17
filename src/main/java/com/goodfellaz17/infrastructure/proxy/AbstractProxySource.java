package com.goodfellaz17.infrastructure.proxy;

import com.goodfellaz17.domain.exception.NoCapacityException;
import com.goodfellaz17.domain.model.*;
import com.goodfellaz17.domain.port.ProxySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base class for proxy sources.
 * Handles common functionality: capacity tracking, lease management, stats.
 */
public abstract class AbstractProxySource implements ProxySource {
    
    protected final Logger log = LoggerFactory.getLogger(getClass());
    
    protected final String name;
    protected final String displayName;
    protected final boolean enabled;
    protected final int capacityPerDay;
    protected final double costPer1k;
    protected final double riskLevel;
    protected final boolean premium;
    protected final Set<String> supportedGeos;
    protected final long leaseTtlSeconds;
    
    // Runtime state
    protected final AtomicInteger usedToday = new AtomicInteger(0);
    protected final Map<String, ProxyLease> activeLeases = new ConcurrentHashMap<>();
    protected final AtomicInteger successCount = new AtomicInteger(0);
    protected final AtomicInteger failureCount = new AtomicInteger(0);
    protected volatile long lastAcquireTime = 0;
    
    protected AbstractProxySource(
        String name,
        String displayName,
        boolean enabled,
        int capacityPerDay,
        double costPer1k,
        double riskLevel,
        boolean premium,
        List<String> supportedGeos,
        long leaseTtlSeconds
    ) {
        this.name = name;
        this.displayName = displayName;
        this.enabled = enabled;
        this.capacityPerDay = capacityPerDay;
        this.costPer1k = costPer1k;
        this.riskLevel = riskLevel;
        this.premium = premium;
        this.supportedGeos = Set.copyOf(supportedGeos != null ? supportedGeos : List.of("GLOBAL"));
        this.leaseTtlSeconds = leaseTtlSeconds > 0 ? leaseTtlSeconds : 300; // 5 min default
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public boolean supportsGeo(String country) {
        if (country == null || country.isBlank()) {
            return true; // No geo requirement
        }
        return supportedGeos.contains("GLOBAL") || supportedGeos.contains(country.toUpperCase());
    }
    
    @Override
    public boolean supportsProfile(RoutingProfile profile) {
        // Check risk tolerance
        if (!profile.allowsRisk(riskLevel)) {
            return false;
        }
        // Premium services require premium sources
        if (profile.priority().requiresPremiumSource() && !premium) {
            return false;
        }
        return true;
    }
    
    @Override
    public int getEstimatedCapacityPerDay() {
        return capacityPerDay;
    }
    
    @Override
    public int getRemainingCapacity() {
        return Math.max(0, capacityPerDay - usedToday.get());
    }
    
    @Override
    public double getCostPer1k() {
        return costPer1k;
    }
    
    @Override
    public double getRiskLevel() {
        return riskLevel;
    }
    
    @Override
    public boolean isPremium() {
        return premium;
    }
    
    @Override
    public ProxyLease acquire(OrderContext ctx) throws NoCapacityException {
        if (!isEnabled()) {
            throw new NoCapacityException("Source " + name + " is disabled");
        }
        
        if (!hasCapacity()) {
            throw new NoCapacityException("Source " + name + " has no remaining capacity");
        }
        
        if (!supportsGeo(ctx.targetCountry())) {
            throw new NoCapacityException("Source " + name + " does not support geo: " + ctx.targetCountry());
        }
        
        if (!supportsProfile(ctx.routingProfile())) {
            throw new NoCapacityException("Source " + name + " does not support routing profile: " + ctx.routingProfile().priority());
        }
        
        // Increment usage
        usedToday.incrementAndGet();
        lastAcquireTime = System.currentTimeMillis();
        
        // Create lease from subclass implementation
        ProxyLease lease = createLease(ctx);
        activeLeases.put(lease.leaseId(), lease);
        
        log.info("Acquired lease {} from {} for order {} (service: {})",
            lease.leaseId(), name, ctx.orderId(), ctx.serviceName());
        
        return lease;
    }
    
    /**
     * Subclasses implement this to create the actual proxy lease.
     */
    protected abstract ProxyLease createLease(OrderContext ctx);
    
    @Override
    public void release(ProxyLease lease) {
        if (lease == null) return;
        
        ProxyLease removed = activeLeases.remove(lease.leaseId());
        if (removed != null) {
            log.debug("Released lease {} from {}", lease.leaseId(), name);
        }
    }
    
    @Override
    public SourceStats getStats() {
        int total = successCount.get() + failureCount.get();
        double successRate = total > 0 ? (double) successCount.get() / total : 1.0;
        
        return new SourceStats(
            name,
            capacityPerDay,
            usedToday.get(),
            getRemainingCapacity(),
            activeLeases.size(),
            successRate,
            lastAcquireTime
        );
    }
    
    /**
     * Record a successful request (for stats).
     */
    public void recordSuccess() {
        successCount.incrementAndGet();
    }
    
    /**
     * Record a failed request (for stats).
     */
    public void recordFailure() {
        failureCount.incrementAndGet();
    }
    
    /**
     * Reset daily counters (called by scheduler at midnight).
     */
    public void resetDailyCounters() {
        usedToday.set(0);
        successCount.set(0);
        failureCount.set(0);
        log.info("Reset daily counters for source: {}", name);
    }
}
