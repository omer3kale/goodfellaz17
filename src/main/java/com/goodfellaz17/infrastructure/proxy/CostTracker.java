package com.goodfellaz17.infrastructure.proxy;

import io.micrometer.core.instrument.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Cost Tracker & Analyzer for Proxy Operations.
 * 
 * Tracks:
 * - Cost per operation type
 * - Cost per proxy tier
 * - Cost per order
 * - Efficiency metrics (success cost vs failure cost)
 * 
 * Answers key questions:
 * - Can we use cheaper proxies for X% of traffic?
 * - What's the actual cost per successful stream?
 * - Which tier has best ROI for each operation?
 */
@Component
public class CostTracker {
    
    private static final Logger log = LoggerFactory.getLogger(CostTracker.class);
    
    // Cost tracking per tier (in microdollars for precision)
    private final Map<ProxyTier, AtomicLong> tierCostMicros = new ConcurrentHashMap<>();
    private final Map<ProxyTier, AtomicLong> tierSuccessCostMicros = new ConcurrentHashMap<>();
    private final Map<ProxyTier, AtomicLong> tierFailureCostMicros = new ConcurrentHashMap<>();
    
    // Cost tracking per operation
    private final Map<OperationType, AtomicLong> operationCostMicros = new ConcurrentHashMap<>();
    private final Map<OperationType, AtomicLong> operationCount = new ConcurrentHashMap<>();
    
    // Request counters
    private final Map<ProxyTier, AtomicLong> tierRequests = new ConcurrentHashMap<>();
    private final Map<ProxyTier, AtomicLong> tierSuccesses = new ConcurrentHashMap<>();
    private final Map<ProxyTier, AtomicLong> tierFailures = new ConcurrentHashMap<>();
    
    // Bytes transferred per tier
    private final Map<ProxyTier, AtomicLong> tierBytes = new ConcurrentHashMap<>();
    
    // Order-level tracking
    private final Map<String, OrderCost> orderCosts = new ConcurrentHashMap<>();
    
    // Hourly rollups for trending
    private final Map<String, HourlyCost> hourlyCosts = new ConcurrentHashMap<>();
    
    // Micrometer metrics (for Prometheus export)
    private final MeterRegistry meterRegistry;
    
    public CostTracker(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize counters
        for (ProxyTier tier : ProxyTier.values()) {
            tierCostMicros.put(tier, new AtomicLong(0));
            tierSuccessCostMicros.put(tier, new AtomicLong(0));
            tierFailureCostMicros.put(tier, new AtomicLong(0));
            tierRequests.put(tier, new AtomicLong(0));
            tierSuccesses.put(tier, new AtomicLong(0));
            tierFailures.put(tier, new AtomicLong(0));
            tierBytes.put(tier, new AtomicLong(0));
            
            // Register Prometheus gauges
            String tierName = tier.name().toLowerCase();
            Gauge.builder("proxy.cost.total", tierCostMicros.get(tier), v -> v.get() / 1_000_000.0)
                .tag("tier", tierName)
                .description("Total cost in USD")
                .register(meterRegistry);
            
            Gauge.builder("proxy.requests.total", tierRequests.get(tier), AtomicLong::get)
                .tag("tier", tierName)
                .description("Total requests")
                .register(meterRegistry);
        }
        
        for (OperationType op : OperationType.values()) {
            operationCostMicros.put(op, new AtomicLong(0));
            operationCount.put(op, new AtomicLong(0));
        }
    }
    
    /**
     * Record a proxy request with cost.
     */
    public void recordRequest(ProxyTier tier, OperationType operation, 
                              long bytesTransferred, boolean success, String orderId) {
        
        // Calculate cost based on tier pricing
        double costPerGb = tier.getCostPerGb();
        long costMicros = (long) (bytesTransferred * costPerGb / 1_000_000_000.0 * 1_000_000);
        
        // Update tier totals
        tierCostMicros.get(tier).addAndGet(costMicros);
        tierRequests.get(tier).incrementAndGet();
        tierBytes.get(tier).addAndGet(bytesTransferred);
        
        if (success) {
            tierSuccesses.get(tier).incrementAndGet();
            tierSuccessCostMicros.get(tier).addAndGet(costMicros);
        } else {
            tierFailures.get(tier).incrementAndGet();
            tierFailureCostMicros.get(tier).addAndGet(costMicros);
        }
        
        // Update operation totals
        operationCostMicros.get(operation).addAndGet(costMicros);
        operationCount.get(operation).incrementAndGet();
        
        // Update order tracking
        if (orderId != null) {
            orderCosts.computeIfAbsent(orderId, OrderCost::new)
                .addRequest(tier, costMicros, success);
        }
        
        // Update hourly rollup
        String hourKey = getHourKey(Instant.now());
        hourlyCosts.computeIfAbsent(hourKey, HourlyCost::new)
            .addCost(tier, costMicros, success);
    }
    
    /**
     * Get total cost in USD.
     */
    public double getTotalCostUsd() {
        return tierCostMicros.values().stream()
            .mapToLong(AtomicLong::get)
            .sum() / 1_000_000.0;
    }
    
    /**
     * Get cost per tier.
     */
    public Map<ProxyTier, Double> getCostByTier() {
        Map<ProxyTier, Double> costs = new EnumMap<>(ProxyTier.class);
        for (ProxyTier tier : ProxyTier.values()) {
            costs.put(tier, tierCostMicros.get(tier).get() / 1_000_000.0);
        }
        return costs;
    }
    
    /**
     * Get cost per operation type.
     */
    public Map<OperationType, Double> getCostByOperation() {
        Map<OperationType, Double> costs = new EnumMap<>(OperationType.class);
        for (OperationType op : OperationType.values()) {
            costs.put(op, operationCostMicros.get(op).get() / 1_000_000.0);
        }
        return costs;
    }
    
    /**
     * Calculate average cost per successful request per tier.
     */
    public Map<ProxyTier, Double> getAverageCostPerSuccess() {
        Map<ProxyTier, Double> avgCosts = new EnumMap<>(ProxyTier.class);
        for (ProxyTier tier : ProxyTier.values()) {
            long successes = tierSuccesses.get(tier).get();
            if (successes > 0) {
                avgCosts.put(tier, tierSuccessCostMicros.get(tier).get() / (double) successes / 1_000_000.0);
            } else {
                avgCosts.put(tier, 0.0);
            }
        }
        return avgCosts;
    }
    
    /**
     * Calculate ROI per tier (success rate / cost).
     */
    public Map<ProxyTier, TierROI> getTierROI() {
        Map<ProxyTier, TierROI> rois = new EnumMap<>(ProxyTier.class);
        
        for (ProxyTier tier : ProxyTier.values()) {
            long requests = tierRequests.get(tier).get();
            long successes = tierSuccesses.get(tier).get();
            double cost = tierCostMicros.get(tier).get() / 1_000_000.0;
            
            double successRate = requests > 0 ? (double) successes / requests : 0.0;
            double costPerRequest = requests > 0 ? cost / requests : 0.0;
            double successesPerDollar = cost > 0 ? successes / cost : 0.0;
            
            rois.put(tier, new TierROI(
                tier,
                requests,
                successes,
                successRate,
                cost,
                costPerRequest,
                successesPerDollar
            ));
        }
        
        return rois;
    }
    
    /**
     * Analyze if cheaper tiers can handle more traffic.
     * Returns recommendation on tier optimization.
     */
    public TierOptimizationAnalysis analyzeOptimization() {
        Map<ProxyTier, TierROI> rois = getTierROI();
        
        // Find best value tier per operation type
        Map<OperationType, ProxyTier> recommendations = new EnumMap<>(OperationType.class);
        
        for (OperationType op : OperationType.values()) {
            ProxyTier minTier = op.getMinimumTier();
            
            // Find tier with best ROI that meets minimum
            ProxyTier bestTier = Arrays.stream(ProxyTier.values())
                .filter(t -> t.meetsRequirement(minTier))
                .filter(t -> rois.get(t).requests() > 10) // Need some data
                .filter(t -> rois.get(t).successRate() >= 0.7) // Minimum acceptable
                .max(Comparator.comparingDouble(t -> rois.get(t).successesPerDollar()))
                .orElse(minTier);
            
            recommendations.put(op, bestTier);
        }
        
        // Calculate potential savings
        double currentCost = getTotalCostUsd();
        double optimizedCost = calculateOptimizedCost(recommendations, rois);
        double potentialSavings = currentCost - optimizedCost;
        double savingsPercent = currentCost > 0 ? potentialSavings / currentCost * 100 : 0;
        
        return new TierOptimizationAnalysis(
            recommendations,
            currentCost,
            optimizedCost,
            potentialSavings,
            savingsPercent,
            rois
        );
    }
    
    /**
     * Get cost for a specific order.
     */
    public Optional<OrderCost> getOrderCost(String orderId) {
        return Optional.ofNullable(orderCosts.get(orderId));
    }
    
    /**
     * Scheduled cleanup of old data.
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void cleanupOldData() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(7));
        
        // Clean old order costs
        orderCosts.entrySet().removeIf(e -> 
            e.getValue().getLastUpdated().isBefore(cutoff));
        
        // Clean old hourly rollups (keep 7 days)
        String cutoffHour = getHourKey(cutoff);
        hourlyCosts.entrySet().removeIf(e -> e.getKey().compareTo(cutoffHour) < 0);
        
        log.debug("Cleaned up cost tracking data older than {}", cutoff);
    }
    
    // === Private Helpers ===
    
    private String getHourKey(Instant instant) {
        return instant.toString().substring(0, 13); // YYYY-MM-DDTHH
    }
    
    private double calculateOptimizedCost(Map<OperationType, ProxyTier> recommendations,
                                          Map<ProxyTier, TierROI> rois) {
        double optimizedCost = 0;
        
        for (OperationType op : OperationType.values()) {
            long count = operationCount.get(op).get();
            ProxyTier recommendedTier = recommendations.get(op);
            double costPerRequest = rois.get(recommendedTier).costPerRequest();
            optimizedCost += count * costPerRequest;
        }
        
        return optimizedCost;
    }
    
    // === Inner Classes ===
    
    /**
     * ROI metrics for a tier.
     */
    public record TierROI(
        ProxyTier tier,
        long requests,
        long successes,
        double successRate,
        double totalCostUsd,
        double costPerRequest,
        double successesPerDollar
    ) {}
    
    /**
     * Cost tracking for a single order.
     */
    public static class OrderCost {
        private final String orderId;
        private long totalCostMicros = 0;
        private int totalRequests = 0;
        private int successfulRequests = 0;
        private final Map<ProxyTier, Long> costByTier = new EnumMap<>(ProxyTier.class);
        private Instant lastUpdated = Instant.now();
        
        public OrderCost(String orderId) {
            this.orderId = orderId;
        }
        
        public synchronized void addRequest(ProxyTier tier, long costMicros, boolean success) {
            totalCostMicros += costMicros;
            totalRequests++;
            if (success) successfulRequests++;
            costByTier.merge(tier, costMicros, Long::sum);
            lastUpdated = Instant.now();
        }
        
        public String getOrderId() { return orderId; }
        public double getTotalCostUsd() { return totalCostMicros / 1_000_000.0; }
        public int getTotalRequests() { return totalRequests; }
        public int getSuccessfulRequests() { return successfulRequests; }
        public double getSuccessRate() { return totalRequests > 0 ? (double) successfulRequests / totalRequests : 0; }
        public Map<ProxyTier, Double> getCostByTier() {
            Map<ProxyTier, Double> result = new EnumMap<>(ProxyTier.class);
            costByTier.forEach((k, v) -> result.put(k, v / 1_000_000.0));
            return result;
        }
        public Instant getLastUpdated() { return lastUpdated; }
    }
    
    /**
     * Hourly cost rollup.
     */
    public static class HourlyCost {
        private final String hour;
        private long totalCostMicros = 0;
        private int totalRequests = 0;
        private int successfulRequests = 0;
        private final Map<ProxyTier, Long> costByTier = new EnumMap<>(ProxyTier.class);
        
        public HourlyCost(String hour) {
            this.hour = hour;
        }
        
        public synchronized void addCost(ProxyTier tier, long costMicros, boolean success) {
            totalCostMicros += costMicros;
            totalRequests++;
            if (success) successfulRequests++;
            costByTier.merge(tier, costMicros, Long::sum);
        }
        
        public String getHour() { return hour; }
        public double getTotalCostUsd() { return totalCostMicros / 1_000_000.0; }
        public int getTotalRequests() { return totalRequests; }
    }
    
    /**
     * Tier optimization analysis result.
     */
    public record TierOptimizationAnalysis(
        Map<OperationType, ProxyTier> recommendations,
        double currentCostUsd,
        double optimizedCostUsd,
        double potentialSavingsUsd,
        double savingsPercent,
        Map<ProxyTier, TierROI> tierROIs
    ) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== TIER OPTIMIZATION ANALYSIS ===\n");
            sb.append(String.format("Current Cost: $%.2f\n", currentCostUsd));
            sb.append(String.format("Optimized Cost: $%.2f\n", optimizedCostUsd));
            sb.append(String.format("Potential Savings: $%.2f (%.1f%%)\n", potentialSavingsUsd, savingsPercent));
            sb.append("\nRecommended Tiers:\n");
            recommendations.forEach((op, tier) ->
                sb.append(String.format("  %s → %s\n", op, tier)));
            return sb.toString();
        }
    }
}
