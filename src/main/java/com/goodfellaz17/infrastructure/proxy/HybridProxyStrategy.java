package com.goodfellaz17.infrastructure.proxy;

import com.goodfellaz17.domain.exception.NoCapacityException;
import com.goodfellaz17.domain.model.OrderContext;
import com.goodfellaz17.domain.model.ProxyLease;
import com.goodfellaz17.domain.model.RoutingProfile;
import com.goodfellaz17.domain.model.ServicePriority;
import com.goodfellaz17.domain.port.ProxySource;
import com.goodfellaz17.domain.port.ProxyStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hybrid proxy strategy that selects the best proxy source based on:
 * - Service priority (BASIC, PREMIUM, ELITE, HIGH_VOLUME, MOBILE_EMULATION)
 * - Geographic requirements
 * - Cost optimization
 * - Available capacity
 * 
 * Selection rules (configuration-driven, not hard-wired):
 * 1. For premium/geo-sensitive orders: prefer sources flagged as premium (AWS, mobile)
 * 2. For large quantities: prefer high-capacity sources (Tor/P2P) where allowed
 * 3. For default/low priority: fall back to cheapest source by cost-per-1k
 * 4. Mobile-preferred services try mobile first, then graceful fallback
 * 
 * The frontend never knows which source handles which order - this is all backend logic.
 */
public class HybridProxyStrategy implements ProxyStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(HybridProxyStrategy.class);
    
    private final List<ProxySource> sources;
    
    public HybridProxyStrategy(List<ProxySource> sources) {
        this.sources = sources != null ? List.copyOf(sources) : List.of();
        log.info("HybridProxyStrategy initialized with {} sources: {}",
            this.sources.size(),
            this.sources.stream().map(ProxySource::getName).collect(Collectors.joining(", ")));
    }
    
    @Override
    public ProxyLease selectProxy(OrderContext ctx) throws NoCapacityException {
        log.debug("Selecting proxy for order {} (service: {}, priority: {}, geo: {})",
            ctx.orderId(), ctx.serviceName(), ctx.getPriority(), ctx.targetCountry());
        
        // Get candidate sources based on context
        List<ProxySource> candidates = selectCandidates(ctx);
        
        if (candidates.isEmpty()) {
            String attemptedSources = sources.stream()
                .map(ProxySource::getName)
                .collect(Collectors.joining(", "));
            throw new NoCapacityException(ctx.orderId(), ctx.serviceId(), attemptedSources);
        }
        
        // Try candidates in order until one succeeds
        List<String> failedSources = new ArrayList<>();
        for (ProxySource source : candidates) {
            try {
                ProxyLease lease = source.acquire(ctx);
                log.info("Selected source {} for order {} (priority: {}, route: {})",
                    source.getName(), ctx.orderId(), ctx.getPriority(), lease.getRouteDescription());
                return lease;
            } catch (NoCapacityException e) {
                log.debug("Source {} unavailable: {}", source.getName(), e.getMessage());
                failedSources.add(source.getName());
            }
        }
        
        // All candidates failed
        throw new NoCapacityException(
            ctx.orderId(),
            ctx.serviceId(),
            String.join(", ", failedSources)
        );
    }
    
    /**
     * Select and order candidate sources based on routing context.
     */
    private List<ProxySource> selectCandidates(OrderContext ctx) {
        RoutingProfile profile = ctx.routingProfile();
        ServicePriority priority = profile.priority();
        
        // Filter to eligible sources
        List<ProxySource> eligible = sources.stream()
            .filter(ProxySource::isEnabled)
            .filter(ProxySource::hasCapacity)
            .filter(s -> s.supportsGeo(ctx.targetCountry()))
            .filter(s -> s.supportsProfile(profile))
            .collect(Collectors.toList());
        
        if (eligible.isEmpty()) {
            return List.of();
        }
        
        // Sort based on priority
        return switch (priority) {
            case ELITE, MOBILE_EMULATION -> sortForElite(eligible, ctx);
            case PREMIUM -> sortForPremium(eligible, ctx);
            case HIGH_VOLUME -> sortForHighVolume(eligible, ctx);
            case BASIC -> sortByCost(eligible);
        };
    }
    
    /**
     * For ELITE and MOBILE_EMULATION: mobile first, then premium, then by cost.
     */
    private List<ProxySource> sortForElite(List<ProxySource> sources, OrderContext ctx) {
        return sources.stream()
            .sorted(Comparator
                // 1. Prefer mobile source if mobile-like behavior is needed
                .<ProxySource>comparingInt(s -> ctx.prefersMobile() && s.getName().equals("mobile") ? 0 : 1)
                // 2. Then prefer premium sources
                .thenComparingInt(s -> s.isPremium() ? 0 : 1)
                // 3. Then by lowest risk
                .thenComparingDouble(ProxySource::getRiskLevel)
                // 4. Then by cost
                .thenComparingDouble(ProxySource::getCostPer1k)
            )
            .toList();
    }
    
    /**
     * For PREMIUM: prefer premium sources, then by risk, then by cost.
     */
    private List<ProxySource> sortForPremium(List<ProxySource> sources, OrderContext ctx) {
        return sources.stream()
            .sorted(Comparator
                // 1. Prefer premium sources
                .<ProxySource>comparingInt(s -> s.isPremium() ? 0 : 1)
                // 2. Then by lowest risk
                .thenComparingDouble(ProxySource::getRiskLevel)
                // 3. Then by cost
                .thenComparingDouble(ProxySource::getCostPer1k)
            )
            .toList();
    }
    
    /**
     * For HIGH_VOLUME: prefer high-capacity, cheap sources.
     */
    private List<ProxySource> sortForHighVolume(List<ProxySource> sources, OrderContext ctx) {
        return sources.stream()
            .sorted(Comparator
                // 1. Prefer sources with most remaining capacity
                .<ProxySource>comparingInt(s -> -s.getRemainingCapacity())
                // 2. Then by lowest cost
                .thenComparingDouble(ProxySource::getCostPer1k)
            )
            .toList();
    }
    
    /**
     * For BASIC: sort by cost (cheapest first).
     */
    private List<ProxySource> sortByCost(List<ProxySource> sources) {
        return sources.stream()
            .sorted(Comparator.comparingDouble(ProxySource::getCostPer1k))
            .toList();
    }
    
    @Override
    public List<ProxySource> getSources() {
        return List.copyOf(sources);
    }
    
    @Override
    public void release(ProxyLease lease) {
        if (lease == null || lease.sourceName() == null) return;
        
        sources.stream()
            .filter(s -> s.getName().equals(lease.sourceName()))
            .findFirst()
            .ifPresent(source -> {
                source.release(lease);
                log.debug("Released lease {} back to {}", lease.leaseId(), source.getName());
            });
    }
    
    @Override
    public List<ProxySource> getAvailableSources() {
        return sources.stream()
            .filter(ProxySource::isEnabled)
            .filter(ProxySource::hasCapacity)
            .toList();
    }
    
    @Override
    public AggregateStats getAggregateStats() {
        int totalSources = sources.size();
        int enabledSources = (int) sources.stream().filter(ProxySource::isEnabled).count();
        int totalCapacity = sources.stream().mapToInt(ProxySource::getEstimatedCapacityPerDay).sum();
        int totalRemaining = sources.stream().mapToInt(ProxySource::getRemainingCapacity).sum();
        int totalActiveLeases = sources.stream()
            .map(ProxySource::getStats)
            .mapToInt(ProxySource.SourceStats::activeLeases)
            .sum();
        
        return new AggregateStats(
            totalSources,
            enabledSources,
            totalCapacity,
            totalRemaining,
            totalActiveLeases
        );
    }
}
