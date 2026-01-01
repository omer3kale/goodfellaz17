package com.goodfellaz17.presentation.api;

import com.goodfellaz17.infrastructure.proxy.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * REST API for Proxy Infrastructure Monitoring.
 * 
 * Endpoints:
 * - GET /api/proxy/stats - Pool statistics
 * - GET /api/proxy/health - Health by tier
 * - GET /api/proxy/cost - Cost analysis
 * - GET /api/proxy/optimize - Optimization recommendations
 * - POST /api/proxy/reset/{tier} - Reset circuit breaker
 */
@RestController
@RequestMapping("/api/proxy")
public class ProxyMonitoringController {
    
    private final HybridProxyRouter proxyRouter;
    private final ProxyHealthMonitor healthMonitor;
    private final CostTracker costTracker;
    private final TierCircuitBreaker circuitBreaker;
    
    public ProxyMonitoringController(
            HybridProxyRouter proxyRouter,
            ProxyHealthMonitor healthMonitor,
            CostTracker costTracker,
            TierCircuitBreaker circuitBreaker) {
        this.proxyRouter = proxyRouter;
        this.healthMonitor = healthMonitor;
        this.costTracker = costTracker;
        this.circuitBreaker = circuitBreaker;
    }
    
    /**
     * Get overall pool statistics.
     */
    @GetMapping("/stats")
    public Mono<ResponseEntity<HybridProxyRouter.PoolStatistics>> getStats() {
        return Mono.just(ResponseEntity.ok(proxyRouter.getStatistics()));
    }
    
    /**
     * Get health status by tier.
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<ProxyTier, ProxyHealthMonitor.TierHealthStatus>>> getHealth() {
        return Mono.just(ResponseEntity.ok(healthMonitor.getHealthByTier()));
    }
    
    /**
     * Get health monitoring statistics.
     */
    @GetMapping("/health/stats")
    public Mono<ResponseEntity<ProxyHealthMonitor.HealthStats>> getHealthStats() {
        return Mono.just(ResponseEntity.ok(healthMonitor.getStats()));
    }
    
    /**
     * Get cost breakdown by tier.
     */
    @GetMapping("/cost")
    public Mono<ResponseEntity<CostSummary>> getCost() {
        return Mono.just(ResponseEntity.ok(new CostSummary(
            costTracker.getTotalCostUsd(),
            costTracker.getCostByTier(),
            costTracker.getCostByOperation(),
            costTracker.getAverageCostPerSuccess()
        )));
    }
    
    /**
     * Get tier ROI analysis.
     */
    @GetMapping("/roi")
    public Mono<ResponseEntity<Map<ProxyTier, CostTracker.TierROI>>> getROI() {
        return Mono.just(ResponseEntity.ok(costTracker.getTierROI()));
    }
    
    /**
     * Get optimization recommendations.
     */
    @GetMapping("/optimize")
    public Mono<ResponseEntity<CostTracker.TierOptimizationAnalysis>> getOptimization() {
        return Mono.just(ResponseEntity.ok(costTracker.analyzeOptimization()));
    }
    
    /**
     * Get circuit breaker status for all tiers.
     */
    @GetMapping("/circuits")
    public Mono<ResponseEntity<Map<ProxyTier, TierCircuitBreaker.CircuitStatus>>> getCircuits() {
        return Mono.just(ResponseEntity.ok(circuitBreaker.getAllStatus()));
    }
    
    /**
     * Reset circuit breaker for a specific tier.
     */
    @PostMapping("/reset/{tier}")
    public Mono<ResponseEntity<String>> resetCircuit(@PathVariable String tier) {
        try {
            ProxyTier proxyTier = ProxyTier.valueOf(tier.toUpperCase());
            circuitBreaker.reset(proxyTier);
            return Mono.just(ResponseEntity.ok("Circuit reset for tier: " + proxyTier));
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest().body("Invalid tier: " + tier));
        }
    }
    
    /**
     * Trigger manual health check.
     */
    @PostMapping("/health/check")
    public Mono<ResponseEntity<String>> triggerHealthCheck() {
        healthMonitor.runScheduledHealthChecks();
        return Mono.just(ResponseEntity.ok("Health check triggered"));
    }
    
    /**
     * Get cost for a specific order.
     */
    @GetMapping("/cost/order/{orderId}")
    public Mono<ResponseEntity<?>> getOrderCost(@PathVariable String orderId) {
        return Mono.justOrEmpty(costTracker.getOrderCost(orderId))
            .map(cost -> ResponseEntity.ok((Object) new OrderCostResponse(
                cost.getOrderId(),
                cost.getTotalCostUsd(),
                cost.getTotalRequests(),
                cost.getSuccessfulRequests(),
                cost.getSuccessRate(),
                cost.getCostByTier()
            )))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    // === Response DTOs ===
    
    record CostSummary(
        double totalCostUsd,
        Map<ProxyTier, Double> costByTier,
        Map<OperationType, Double> costByOperation,
        Map<ProxyTier, Double> avgCostPerSuccess
    ) {}
    
    record OrderCostResponse(
        String orderId,
        double totalCostUsd,
        int totalRequests,
        int successfulRequests,
        double successRate,
        Map<ProxyTier, Double> costByTier
    ) {}
}
