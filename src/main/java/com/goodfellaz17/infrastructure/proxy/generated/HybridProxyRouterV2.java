package com.goodfellaz17.infrastructure.proxy.generated;

import com.goodfellaz17.domain.model.generated.*;
import com.goodfellaz17.infrastructure.persistence.generated.GeneratedProxyNodeRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * 
 * HybridProxyRouterV2 - Enhanced proxy selection engine.
 * 
 * Integrates with:
 *   - ProxyNodeEntity (from generated domain model)
 *   - ProxyMetrics (real-time health data)
 *   - GeneratedProxyNodeRepository (R2DBC persistence)
 * 
 * Selection algorithm for 15k/48-72h package guarantee:
 *   1. Filter by operation requirements (tier, geo)
 *   2. Score based on success rate, latency, cost
 *   3. Apply circuit breaker for failing tiers
 *   4. Load balance across healthy proxies
 *   5. Track sticky sessions for account operations
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
@Component
public class HybridProxyRouterV2 {
    
    private static final Logger log = LoggerFactory.getLogger(HybridProxyRouterV2.class);
    
    // === Configuration ===
    private static final int TOP_N_RANDOM_SELECTION = 3;
    private static final double MIN_SUCCESS_RATE_THRESHOLD = 0.7;
    private static final int MAX_LATENCY_P95_MS = 5000;
    private static final Duration METRICS_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration STICKY_SESSION_TTL = Duration.ofMinutes(30);
    
    // === Dependencies ===
    private final GeneratedProxyNodeRepository proxyRepository;
    
    // === In-memory state ===
    private final Cache<UUID, ProxyMetricsSnapshot> metricsCache;
    private final Cache<String, UUID> stickySessions;
    private final Map<ProxyTier, TierHealth> tierHealthMap = new ConcurrentHashMap<>();
    private final Map<ProxyTier, Long> selectionCounts = new ConcurrentHashMap<>();
    
    public HybridProxyRouterV2(GeneratedProxyNodeRepository proxyRepository) {
        this.proxyRepository = proxyRepository;
        
        this.metricsCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(METRICS_CACHE_TTL)
            .build();
        
        this.stickySessions = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(STICKY_SESSION_TTL)
            .build();
        
        // Initialize tier health tracking
        for (ProxyTier tier : ProxyTier.values()) {
            tierHealthMap.put(tier, new TierHealth(tier));
            selectionCounts.put(tier, 0L);
        }
    }
    
    // =========================================================================
    // PUBLIC API
    // =========================================================================
    
    /**
     * Route a request to the best available proxy.
     * 
     * @param request Routing request with operation and context
     * @return Selected proxy wrapped in Mono, empty if none available
     */
    public Mono<ProxySelection> route(RoutingRequest request) {
        log.debug("Routing request: operation={}, geo={}, sessionId={}",
            request.operation(), request.targetCountry(), request.sessionId());
        
        // Check sticky session first
        if (request.sessionId() != null && request.operation().requiresSticky()) {
            UUID stickyProxyId = stickySessions.getIfPresent(request.sessionId());
            if (stickyProxyId != null) {
                return proxyRepository.findById(stickyProxyId)
                    .filter(this::isProxyUsable)
                    .map(proxy -> createSelection(proxy, request, true))
                    .switchIfEmpty(selectNewProxy(request));
            }
        }
        
        return selectNewProxy(request);
    }
    
    /**
     * Report proxy usage result for metrics tracking.
     */
    public Mono<Void> reportResult(UUID proxyId, ProxyResult result) {
        return proxyRepository.findById(proxyId)
            .flatMap(proxy -> {
                // Update cached metrics
                ProxyMetricsSnapshot metrics = metricsCache.get(proxyId, 
                    id -> new ProxyMetricsSnapshot(proxyId));
                metrics.recordResult(result);
                
                // Update tier health
                ProxyTier tier = ProxyTier.valueOf(proxy.getTier());
                tierHealthMap.get(tier).recordResult(result);
                
                // Handle failures
                if (!result.success()) {
                    if (metrics.getConsecutiveFailures() >= 5) {
                        log.warn("Proxy {} circuit opened after {} consecutive failures",
                            proxyId, metrics.getConsecutiveFailures());
                    }
                    
                    if (result.errorCode() == 403 || result.errorCode() == 429) {
                        return proxyRepository.markOffline(proxyId).then();
                    }
                }
                
                return Mono.empty();
            });
    }
    
    /**
     * Get pool health status for a tier.
     */
    public Mono<PoolHealthStatus> getPoolHealth(ProxyTier tier) {
        return proxyRepository.findByTierAndStatus(tier.name(), ProxyStatus.ONLINE.name())
            .collectList()
            .map(proxies -> {
                TierHealth health = tierHealthMap.get(tier);
                int totalCapacity = proxies.stream().mapToInt(ProxyNodeEntity::getCapacity).sum();
                int currentLoad = proxies.stream().mapToInt(ProxyNodeEntity::getCurrentLoad).sum();
                
                return new PoolHealthStatus(
                    tier,
                    proxies.size(),
                    totalCapacity,
                    currentLoad,
                    health.getSuccessRate(),
                    health.getAvgLatencyMs(),
                    health.isCircuitOpen()
                );
            });
    }
    
    /**
     * Get overall pool statistics.
     */
    public Flux<PoolHealthStatus> getAllPoolHealth() {
        return Flux.fromArray(ProxyTier.values())
            .flatMap(this::getPoolHealth);
    }
    
    // =========================================================================
    // PRIVATE SELECTION LOGIC
    // =========================================================================
    
    private Mono<ProxySelection> selectNewProxy(RoutingRequest request) {
        ProxyTier minTier = request.operation().getMinimumTier();
        ProxyTier preferredTier = getPreferredTier(request.operation());
        
        // Find best available tier (considering circuit breakers)
        Optional<ProxyTier> activeTier = findBestAvailableTier(preferredTier, minTier);
        
        if (activeTier.isEmpty()) {
            log.warn("No available tier for operation {} (min: {})", request.operation(), minTier);
            return Mono.empty();
        }
        
        // Query candidates from database
        return getCandidates(activeTier.get(), request.targetCountry())
            .collectList()
            .flatMap(candidates -> {
                if (candidates.isEmpty()) {
                    log.warn("No proxy candidates for tier {} and country {}", 
                        activeTier.get(), request.targetCountry());
                    return Mono.empty();
                }
                
                // Score and select
                List<ScoredProxy> scored = candidates.stream()
                    .map(proxy -> scoreProxy(proxy, request))
                    .filter(sp -> sp.score() >= MIN_SUCCESS_RATE_THRESHOLD)
                    .sorted(Comparator.comparingDouble(ScoredProxy::score).reversed())
                    .limit(TOP_N_RANDOM_SELECTION)
                    .toList();
                
                if (scored.isEmpty()) {
                    log.warn("No proxies meet minimum score threshold");
                    return Mono.empty();
                }
                
                // Select from top N (weighted random for load distribution)
                ScoredProxy selected = selectWeightedRandom(scored);
                
                // Track selection
                ProxyTier tier = ProxyTier.valueOf(selected.proxy().getTier());
                selectionCounts.merge(tier, 1L, Long::sum);
                
                // Store sticky session if needed
                if (request.sessionId() != null && request.operation().requiresSticky()) {
                    stickySessions.put(request.sessionId(), selected.proxy().getId());
                }
                
                // Increment load
                return proxyRepository.incrementLoad(selected.proxy().getId())
                    .map(proxy -> createSelection(proxy, request, false));
            });
    }
    
    private Flux<ProxyNodeEntity> getCandidates(ProxyTier tier, String targetCountry) {
        if (targetCountry != null && !targetCountry.isBlank()) {
            return proxyRepository.findAvailableByCountryAndTier(
                targetCountry, tier.name(), 50);
        }
        return proxyRepository.findAvailableByTier(tier.name(), 50);
    }
    
    private ScoredProxy scoreProxy(ProxyNodeEntity proxy, RoutingRequest request) {
        ProxyMetricsSnapshot metrics = metricsCache.get(proxy.getId(), 
            id -> new ProxyMetricsSnapshot(proxy.getId()));
        
        // Base score from success rate (0.0 - 1.0)
        double score = metrics.getSuccessRate();
        
        // Latency penalty
        if (metrics.getAvgLatencyMs() > MAX_LATENCY_P95_MS) {
            score *= 0.5;
        } else if (metrics.getAvgLatencyMs() > 2000) {
            score *= 0.8;
        }
        
        // Load factor - prefer less loaded proxies
        double loadPercent = (double) proxy.getCurrentLoad() / proxy.getCapacity();
        score *= (1.0 - loadPercent * 0.3);
        
        // Cost factor - prefer cheaper when quality is similar
        ProxyTier tier = ProxyTier.valueOf(proxy.getTier());
        double costFactor = 1.0 - (tier.getCostPerRequest() / 0.10) * 0.1;
        score *= Math.max(0.8, costFactor);
        
        // Freshness bonus - prefer recently successful proxies
        if (metrics.getLastSuccess() != null) {
            Duration sinceLastSuccess = Duration.between(metrics.getLastSuccess(), Instant.now());
            if (sinceLastSuccess.toMinutes() < 5) {
                score *= 1.1;
            }
        }
        
        return new ScoredProxy(proxy, Math.min(1.0, score), metrics);
    }
    
    private ScoredProxy selectWeightedRandom(List<ScoredProxy> scored) {
        if (scored.size() == 1) {
            return scored.get(0);
        }
        
        // Weight by score squared for stronger preference to best
        double totalWeight = scored.stream()
            .mapToDouble(sp -> sp.score() * sp.score())
            .sum();
        
        double random = Math.random() * totalWeight;
        double cumulative = 0;
        
        for (ScoredProxy sp : scored) {
            cumulative += sp.score() * sp.score();
            if (random <= cumulative) {
                return sp;
            }
        }
        
        return scored.get(0);
    }
    
    private Optional<ProxyTier> findBestAvailableTier(ProxyTier preferred, ProxyTier minimum) {
        // Try preferred first
        if (preferred.meetsMinimum(minimum) && !tierHealthMap.get(preferred).isCircuitOpen()) {
            return Optional.of(preferred);
        }
        
        // Fall back to higher tiers
        for (ProxyTier tier : ProxyTier.values()) {
            if (tier.meetsMinimum(minimum) && !tierHealthMap.get(tier).isCircuitOpen()) {
                return Optional.of(tier);
            }
        }
        
        // Last resort - use minimum even if circuit is open
        return Optional.of(minimum);
    }
    
    private ProxyTier getPreferredTier(OperationType operation) {
        return switch (operation) {
            case ACCOUNT_CREATION -> ProxyTier.RESIDENTIAL;
            case STREAMING -> ProxyTier.DATACENTER;
            case PLAYLIST_ADD -> ProxyTier.ISP;
            case FOLLOW -> ProxyTier.ISP;
            case SAVE -> ProxyTier.DATACENTER;
            default -> ProxyTier.DATACENTER;
        };
    }
    
    private boolean isProxyUsable(ProxyNodeEntity proxy) {
        return ProxyStatus.ONLINE.name().equals(proxy.getStatus()) 
            && proxy.getCurrentLoad() < proxy.getCapacity();
    }
    
    private ProxySelection createSelection(ProxyNodeEntity proxy, RoutingRequest request, boolean isSticky) {
        return new ProxySelection(
            proxy.getId(),
            proxy.getProxyUrl(),
            ProxyTier.valueOf(proxy.getTier()),
            proxy.getCountry(),
            isSticky,
            Instant.now().plusSeconds(300), // 5 min lease
            proxy  // Include full entity
        );
    }
    
    // =========================================================================
    // INNER CLASSES
    // =========================================================================
    
    /**
     * Proxy selection request.
     */
    public record RoutingRequest(
        OperationType operation,
        String targetCountry,
        String sessionId,
        int quantity,
        Double maxCostPerRequest
    ) {
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private OperationType operation;
            private String targetCountry;
            private String sessionId;
            private int quantity = 1;
            private Double maxCostPerRequest;
            
            public Builder operation(OperationType operation) { this.operation = operation; return this; }
            public Builder targetCountry(String targetCountry) { this.targetCountry = targetCountry; return this; }
            public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
            public Builder quantity(int quantity) { this.quantity = quantity; return this; }
            public Builder maxCostPerRequest(Double maxCostPerRequest) { this.maxCostPerRequest = maxCostPerRequest; return this; }
            
            public RoutingRequest build() {
                return new RoutingRequest(operation, targetCountry, sessionId, quantity, maxCostPerRequest);
            }
        }
    }
    
    /**
     * Selected proxy for use.
     */
    public record ProxySelection(
        UUID proxyId,
        String proxyUrl,
        ProxyTier tier,
        String country,
        boolean isSticky,
        Instant leaseExpires,
        ProxyNodeEntity proxy  // Include full entity for task processing
    ) {
        // Legacy constructor without entity (for backward compatibility)
        public ProxySelection(UUID proxyId, String proxyUrl, ProxyTier tier, 
                String country, boolean isSticky, Instant leaseExpires) {
            this(proxyId, proxyUrl, tier, country, isSticky, leaseExpires, null);
        }
    }
    
    /**
     * Proxy usage result for metrics.
     */
    public record ProxyResult(
        boolean success,
        int latencyMs,
        Integer errorCode,
        long bytesTransferred
    ) {
        public static ProxyResult success(int latencyMs, long bytes) {
            return new ProxyResult(true, latencyMs, null, bytes);
        }
        
        public static ProxyResult failure(int latencyMs, int errorCode) {
            return new ProxyResult(false, latencyMs, errorCode, 0);
        }
    }
    
    /**
     * Pool health status for monitoring.
     */
    public record PoolHealthStatus(
        ProxyTier tier,
        int nodeCount,
        int totalCapacity,
        int currentLoad,
        double successRate,
        int avgLatencyMs,
        boolean circuitOpen
    ) {
        public double getLoadPercent() {
            return totalCapacity > 0 ? (double) currentLoad / totalCapacity * 100.0 : 0.0;
        }
        
        public boolean isHealthy() {
            return !circuitOpen && successRate >= 0.8 && avgLatencyMs < 3000;
        }
    }
    
    /**
     * Scored proxy for selection.
     */
    private record ScoredProxy(
        ProxyNodeEntity proxy,
        double score,
        ProxyMetricsSnapshot metrics
    ) {}
    
    /**
     * In-memory metrics snapshot for a proxy.
     */
    private static class ProxyMetricsSnapshot {
        private final UUID proxyId;
        private long totalRequests = 0;
        private long successfulRequests = 0;
        private long totalLatencyMs = 0;
        private int consecutiveFailures = 0;
        private Instant lastSuccess;
        
        ProxyMetricsSnapshot(UUID proxyId) {
            this.proxyId = proxyId;
        }
        
        synchronized void recordResult(ProxyResult result) {
            totalRequests++;
            totalLatencyMs += result.latencyMs();
            
            if (result.success()) {
                successfulRequests++;
                consecutiveFailures = 0;
                lastSuccess = Instant.now();
            } else {
                consecutiveFailures++;
            }
        }
        
        double getSuccessRate() {
            return totalRequests > 0 ? (double) successfulRequests / totalRequests : 1.0;
        }
        
        int getAvgLatencyMs() {
            return totalRequests > 0 ? (int) (totalLatencyMs / totalRequests) : 0;
        }
        
        int getConsecutiveFailures() {
            return consecutiveFailures;
        }
        
        Instant getLastSuccess() {
            return lastSuccess;
        }
    }
    
    /**
     * Tier-level health tracking with circuit breaker.
     */
    private static class TierHealth {
        private final ProxyTier tier;
        private long totalRequests = 0;
        private long successfulRequests = 0;
        private long totalLatencyMs = 0;
        private int consecutiveFailures = 0;
        private boolean circuitOpen = false;
        private Instant circuitOpenedAt;
        
        TierHealth(ProxyTier tier) {
            this.tier = tier;
        }
        
        synchronized void recordResult(ProxyResult result) {
            totalRequests++;
            totalLatencyMs += result.latencyMs();
            
            if (result.success()) {
                successfulRequests++;
                consecutiveFailures = 0;
                
                // Auto-close circuit after success
                if (circuitOpen) {
                    circuitOpen = false;
                    circuitOpenedAt = null;
                }
            } else {
                consecutiveFailures++;
                
                // Open circuit after 10 consecutive failures
                if (consecutiveFailures >= 10 && !circuitOpen) {
                    circuitOpen = true;
                    circuitOpenedAt = Instant.now();
                }
            }
            
            // Auto-close circuit after 1 minute
            if (circuitOpen && circuitOpenedAt != null 
                && Duration.between(circuitOpenedAt, Instant.now()).toMinutes() >= 1) {
                circuitOpen = false;
                circuitOpenedAt = null;
                consecutiveFailures = 0;
            }
        }
        
        double getSuccessRate() {
            return totalRequests > 0 ? (double) successfulRequests / totalRequests : 1.0;
        }
        
        int getAvgLatencyMs() {
            return totalRequests > 0 ? (int) (totalLatencyMs / totalRequests) : 0;
        }
        
        boolean isCircuitOpen() {
            return circuitOpen;
        }
    }
    
    /**
     * Operation types with tier requirements.
     */
    public enum OperationType {
        ACCOUNT_CREATION(ProxyTier.RESIDENTIAL, true),
        STREAMING(ProxyTier.DATACENTER, false),
        PLAY_DELIVERY(ProxyTier.DATACENTER, false),  // 15k order task delivery
        PLAYLIST_ADD(ProxyTier.ISP, true),
        FOLLOW(ProxyTier.ISP, true),
        SAVE(ProxyTier.DATACENTER, true),
        SCRAPING(ProxyTier.DATACENTER, false);
        
        private final ProxyTier minimumTier;
        private final boolean requiresSticky;
        
        OperationType(ProxyTier minimumTier, boolean requiresSticky) {
            this.minimumTier = minimumTier;
            this.requiresSticky = requiresSticky;
        }
        
        public ProxyTier getMinimumTier() {
            return minimumTier;
        }
        
        public boolean requiresSticky() {
            return requiresSticky;
        }
    }
}
