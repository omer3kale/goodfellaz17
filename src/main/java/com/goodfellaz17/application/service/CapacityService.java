package com.goodfellaz17.application.service;

import com.goodfellaz17.domain.model.generated.OrderEntity;
import com.goodfellaz17.domain.model.generated.OrderStatus;
import com.goodfellaz17.domain.model.generated.ProxyNodeEntity;
import com.goodfellaz17.domain.model.generated.ProxyMetricsEntity;
import com.goodfellaz17.domain.model.generated.ProxyTier;
import com.goodfellaz17.infrastructure.persistence.generated.GeneratedProxyNodeRepository;
import com.goodfellaz17.infrastructure.persistence.generated.GeneratedProxyMetricsRepository;
import com.goodfellaz17.infrastructure.persistence.generated.GeneratedOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Capacity Planning Service.
 * 
 * Estimates total safe plays/hour based on proxy pool health metrics.
 * Used to determine if a 15k order can be accepted within 48–72 hours.
 * 
 * Key assumptions:
 * - Each proxy can handle ~20 plays/hour safely (anti-detection limit)
 * - Success rate affects effective capacity
 * - Safety factor of 0.7 to account for variance
 * - 48h target, 72h maximum for 15k packages
 */
@Service
public class CapacityService {
    
    private static final Logger log = LoggerFactory.getLogger(CapacityService.class);
    
    // === Configuration Constants ===
    
    /** Base plays per proxy per hour (conservative for Spotify anti-detection) */
    private static final int BASE_PLAYS_PER_PROXY_HOUR = 20;
    
    /** Tier multipliers - higher quality tiers can push slightly harder */
    private static final Map<String, Double> TIER_MULTIPLIERS = Map.of(
        ProxyTier.DATACENTER.name(), 1.0,    // 20 plays/hr
        ProxyTier.ISP.name(),        1.5,    // 30 plays/hr (less detection)
        ProxyTier.RESIDENTIAL.name(), 2.0,   // 40 plays/hr (trusted IPs)
        ProxyTier.MOBILE.name(),     2.5,    // 50 plays/hr (best quality)
        ProxyTier.TOR.name(),        0.5     // 10 plays/hr (only for diversity)
    );
    
    /** Safety factor to account for variance (70% of theoretical max) */
    private static final double SAFETY_FACTOR = 0.70;
    
    /** Minimum success rate for a proxy to be counted in capacity */
    private static final double MIN_SUCCESS_RATE = 0.85;
    
    /** Target hours for 15k package delivery */
    private static final int TARGET_HOURS = 48;
    
    /** Maximum hours allowed for 15k package */
    private static final int MAX_HOURS = 72;
    
    /** Standard package size */
    private static final int STANDARD_PACKAGE = 15000;
    
    private final GeneratedProxyNodeRepository proxyNodeRepository;
    private final GeneratedProxyMetricsRepository proxyMetricsRepository;
    private final GeneratedOrderRepository orderRepository;
    
    /** Simulated capacity override for testing (null = use real capacity) */
    private volatile Integer simulatedPlaysPerHour = null;
    
    public CapacityService(
            GeneratedProxyNodeRepository proxyNodeRepository,
            GeneratedProxyMetricsRepository proxyMetricsRepository,
            GeneratedOrderRepository orderRepository) {
        this.proxyNodeRepository = proxyNodeRepository;
        this.proxyMetricsRepository = proxyMetricsRepository;
        this.orderRepository = orderRepository;
    }
    
    /**
     * Enable capacity simulation for testing rejection behavior.
     * @param playsPerHour simulated plays/hour, or null to disable
     */
    public void setSimulatedCapacity(Integer playsPerHour) {
        this.simulatedPlaysPerHour = playsPerHour;
        if (playsPerHour != null) {
            log.warn("⚠️  CAPACITY SIMULATION ENABLED: {} plays/hr", playsPerHour);
        } else {
            log.info("Capacity simulation DISABLED");
        }
    }
    
    /**
     * Get current simulation state.
     */
    public Integer getSimulatedCapacity() {
        return simulatedPlaysPerHour;
    }
    
    // =========================================================================
    // PUBLIC API
    // =========================================================================
    
    /**
     * Calculate current estimated plays per hour across all tiers.
     * 
     * @return Mono with CapacitySnapshot containing per-tier and total capacity
     */
    public Mono<CapacitySnapshot> calculateCurrentCapacity() {
        log.debug("Calculating current capacity snapshot...");
        
        return proxyNodeRepository.findAll()
            .filter(node -> "ONLINE".equals(node.getStatus()))
            .flatMap(this::enrichWithMetrics)
            .collectList()
            .map(this::buildCapacitySnapshot)
            .doOnSuccess(snapshot -> log.info(
                "Capacity snapshot: {} plays/hr total, {} max in {}h, canAccept15k={}",
                snapshot.totalPlaysPerHour(),
                snapshot.max72hCapacity(),
                MAX_HOURS,
                snapshot.canAccept15kOrder()));
    }
    
    /**
     * Check if a specific order quantity can be accepted within deadline.
     * 
     * @param quantity Number of plays requested
     * @return Mono<CanAcceptResult> with acceptance status and ETA
     */
    public Mono<CanAcceptResult> canAccept(int quantity) {
        return calculateCurrentCapacity()
            .flatMap(snapshot -> calculatePendingLoad()
                .map(pendingPlays -> {
                    // Apply simulation override if set
                    int effectivePlaysPerHour = simulatedPlaysPerHour != null 
                        ? simulatedPlaysPerHour 
                        : snapshot.totalPlaysPerHour();
                    
                    int effectiveMax72h = effectivePlaysPerHour * MAX_HOURS;
                    int effectiveMax48h = effectivePlaysPerHour * TARGET_HOURS;
                    
                    // Available capacity after pending orders
                    int availableCapacity72h = effectiveMax72h - pendingPlays;
                    int availableCapacity48h = effectiveMax48h - pendingPlays;
                    
                    boolean canAcceptIn72h = quantity <= availableCapacity72h;
                    boolean canAcceptIn48h = quantity <= availableCapacity48h;
                    
                    // Calculate estimated completion time
                    int netPlaysPerHour = Math.max(1, effectivePlaysPerHour);
                    double hoursNeeded = (double) quantity / netPlaysPerHour;
                    Instant estimatedCompletion = Instant.now().plus(
                        Duration.ofMinutes((long)(hoursNeeded * 60)));
                    
                    return new CanAcceptResult(
                        canAcceptIn72h,
                        canAcceptIn48h,
                        quantity,
                        availableCapacity72h,
                        availableCapacity48h,
                        pendingPlays,
                        hoursNeeded,
                        estimatedCompletion,
                        canAcceptIn72h ? null : buildRejectionReason(quantity, availableCapacity72h)
                    );
                }));
    }
    
    /**
     * Get estimated completion time for an order.
     * Considers current queue depth and capacity.
     */
    public Mono<Instant> estimateCompletionTime(int quantity) {
        return canAccept(quantity)
            .map(result -> result.estimatedCompletion());
    }
    
    /**
     * Calculate total pending plays from active orders.
     */
    public Mono<Integer> calculatePendingLoad() {
        return orderRepository.findByStatusIn(List.of(
                OrderStatus.PENDING.name(),
                OrderStatus.VALIDATING.name(),
                OrderStatus.RUNNING.name()))
            .map(order -> order.getRemains())
            .reduce(0, Integer::sum)
            .doOnSuccess(total -> log.debug("Pending plays in queue: {}", total));
    }
    
    // =========================================================================
    // CAPACITY CALCULATION HELPERS
    // =========================================================================
    
    /**
     * Enrich proxy node with its metrics for capacity calculation.
     */
    private Mono<ProxyWithMetrics> enrichWithMetrics(ProxyNodeEntity node) {
        return proxyMetricsRepository.findByProxyNodeId(node.getId())
            .defaultIfEmpty(new ProxyMetricsEntity(node.getId()))
            .map(metrics -> new ProxyWithMetrics(node, metrics));
    }
    
    /**
     * Build capacity snapshot from list of proxies with metrics.
     */
    private CapacitySnapshot buildCapacitySnapshot(List<ProxyWithMetrics> proxies) {
        Map<String, TierCapacity> tierCapacities = new HashMap<>();
        int totalPlaysPerHour = 0;
        int healthyProxyCount = 0;
        int totalProxyCount = proxies.size();
        
        // Group by tier and calculate capacity
        for (ProxyWithMetrics pm : proxies) {
            String tier = pm.node().getTier();
            double successRate = pm.metrics().getSuccessRate();
            
            // Skip unhealthy proxies
            if (successRate < MIN_SUCCESS_RATE) {
                continue;
            }
            
            healthyProxyCount++;
            
            // Calculate effective plays/hour for this proxy
            double tierMultiplier = TIER_MULTIPLIERS.getOrDefault(tier, 1.0);
            int basePlaysPerHour = (int)(BASE_PLAYS_PER_PROXY_HOUR * tierMultiplier);
            int effectivePlaysPerHour = (int)(basePlaysPerHour * successRate * SAFETY_FACTOR);
            
            totalPlaysPerHour += effectivePlaysPerHour;
            
            // Accumulate tier capacity
            tierCapacities.compute(tier, (k, existing) -> {
                if (existing == null) {
                    return new TierCapacity(tier, 1, effectivePlaysPerHour);
                }
                return new TierCapacity(tier, 
                    existing.proxyCount() + 1, 
                    existing.playsPerHour() + effectivePlaysPerHour);
            });
        }
        
        int max48h = totalPlaysPerHour * TARGET_HOURS;
        int max72h = totalPlaysPerHour * MAX_HOURS;
        
        return new CapacitySnapshot(
            totalPlaysPerHour,
            max48h,
            max72h,
            healthyProxyCount,
            totalProxyCount,
            tierCapacities,
            Instant.now(),
            max72h >= STANDARD_PACKAGE
        );
    }
    
    private String buildRejectionReason(int requested, int available) {
        int deficit = requested - available;
        return String.format(
            "Package capacity full. Requested: %,d plays, Available (72h): %,d. " +
            "Need %,d more capacity. Try again later or reduce quantity.",
            requested, Math.max(0, available), deficit);
    }
    
    // =========================================================================
    // DATA CLASSES
    // =========================================================================
    
    /** Internal helper to pair proxy node with its metrics */
    private record ProxyWithMetrics(ProxyNodeEntity node, ProxyMetricsEntity metrics) {}
    
    /** Capacity for a single tier */
    public record TierCapacity(
        String tier,
        int proxyCount,
        int playsPerHour
    ) {}
    
    /** Full capacity snapshot */
    public record CapacitySnapshot(
        int totalPlaysPerHour,
        int max48hCapacity,
        int max72hCapacity,
        int healthyProxyCount,
        int totalProxyCount,
        Map<String, TierCapacity> tierCapacities,
        Instant calculatedAt,
        boolean canAccept15kOrder
    ) {}
    
    /** Result of capacity check for a specific order */
    public record CanAcceptResult(
        boolean accepted,
        boolean withinTarget,
        int requestedQuantity,
        int availableCapacity72h,
        int availableCapacity48h,
        int pendingPlays,
        double estimatedHours,
        Instant estimatedCompletion,
        String rejectionReason
    ) {}
}
