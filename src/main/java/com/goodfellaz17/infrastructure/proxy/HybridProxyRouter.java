package com.goodfellaz17.infrastructure.proxy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * HybridProxyRouter - The Brain of Proxy Selection.
 * 
 * Routes requests to optimal proxies based on:
 * - Operation type (account creation, streaming, etc.)
 * - Proxy health metrics (success rate, latency, bans)
 * - Cost efficiency (cheaper is better when quality allows)
 * - Geo requirements (target country matching)
 * - Circuit breaker state (avoid failing tiers)
 * 
 * Selection Algorithm:
 * 1. Determine operation requirements (min tier, geo, sticky)
 * 2. Filter available proxies (circuit closed, healthy)
 * 3. Score each proxy using weighted algorithm
 * 4. Select best proxy (or random from top N for distribution)
 * 5. Track selection for metrics
 */
@Component
public class HybridProxyRouter {
    
    private static final Logger log = LoggerFactory.getLogger(HybridProxyRouter.class);
    
    // Configuration
    private static final int TOP_N_RANDOM_SELECTION = 3; // Pick random from top 3
    private static final double MIN_SCORE_THRESHOLD = 0.3; // Minimum acceptable score
    
    // Dependencies
    private final ProxyScorer scorer;
    private final TierCircuitBreaker circuitBreaker;
    
    // Proxy pools by tier
    private final Map<ProxyTier, List<ProxyMetrics>> proxyPools = new ConcurrentHashMap<>();
    
    // Metrics cache (proxy_id -> metrics)
    private final Cache<String, ProxyMetrics> metricsCache;
    
    // Sticky session tracking (session_id -> proxy_id)
    private final Cache<String, String> stickySessions;
    
    // Selection statistics
    private final Map<ProxyTier, Long> selectionCounts = new ConcurrentHashMap<>();
    private final Map<OperationType, Long> operationCounts = new ConcurrentHashMap<>();
    
    public HybridProxyRouter(ProxyScorer scorer, TierCircuitBreaker circuitBreaker) {
        this.scorer = scorer;
        this.circuitBreaker = circuitBreaker;
        
        // Initialize caches
        this.metricsCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();
        
        this.stickySessions = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();
        
        // Initialize empty pools
        for (ProxyTier tier : ProxyTier.values()) {
            proxyPools.put(tier, Collections.synchronizedList(new ArrayList<>()));
            selectionCounts.put(tier, 0L);
        }
        
        for (OperationType op : OperationType.values()) {
            operationCounts.put(op, 0L);
        }
    }
    
    /**
     * Route a request to the best available proxy.
     * 
     * @param request Routing request with operation and context
     * @return Selected proxy, or empty if none available
     */
    public Optional<ProxySelection> route(RoutingRequest request) {
        log.debug("Routing request: operation={}, geo={}, sticky={}",
            request.operation(), request.targetCountry(), request.sessionId());
        
        // Track operation
        operationCounts.merge(request.operation(), 1L, Long::sum);
        
        // Check sticky session first
        if (request.sessionId() != null && request.operation().requiresSticky()) {
            String stickyProxyId = stickySessions.getIfPresent(request.sessionId());
            if (stickyProxyId != null) {
                ProxyMetrics stickyProxy = metricsCache.getIfPresent(stickyProxyId);
                if (stickyProxy != null && stickyProxy.isAvailable()) {
                    log.debug("Returning sticky proxy: {}", stickyProxyId);
                    return Optional.of(createSelection(stickyProxy, request, true));
                }
            }
        }
        
        // Determine minimum tier requirement
        ProxyTier minTier = request.operation().getMinimumTier();
        
        // Get preferred tier based on operation
        ProxyTier preferredTier = getPreferredTier(request.operation());
        
        // Find best available tier (considering circuit breakers)
        Optional<ProxyTier> activeTier = circuitBreaker.getBestAvailableTier(preferredTier, minTier);
        
        if (activeTier.isEmpty()) {
            log.warn("No available tier for operation {} (min: {})", request.operation(), minTier);
            return Optional.empty();
        }
        
        // Get all proxies from available tiers
        List<ProxyMetrics> candidates = getCandidates(activeTier.get(), minTier, request.targetCountry());
        
        if (candidates.isEmpty()) {
            log.warn("No proxy candidates for tier {} and above", activeTier.get());
            return Optional.empty();
        }
        
        // Score all candidates
        ProxyScorer.ScoringContext scoringContext = new ProxyScorer.ScoringContext(
            request.operation(),
            request.targetCountry(),
            request.quantity(),
            request.maxCostPerRequest()
        );
        
        List<ScoredProxy> scoredProxies = candidates.stream()
            .map(proxy -> new ScoredProxy(proxy, scorer.calculateScore(proxy, scoringContext)))
            .filter(sp -> sp.score() >= MIN_SCORE_THRESHOLD)
            .sorted(Comparator.comparingDouble(ScoredProxy::score).reversed())
            .limit(TOP_N_RANDOM_SELECTION)
            .collect(Collectors.toList());
        
        if (scoredProxies.isEmpty()) {
            log.warn("No proxies meet minimum score threshold {}", MIN_SCORE_THRESHOLD);
            return Optional.empty();
        }
        
        // Select from top N (weighted random)
        ScoredProxy selected = selectWeightedRandom(scoredProxies);
        
        // Track selection
        selectionCounts.merge(selected.proxy().getTier(), 1L, Long::sum);
        
        // Create selection result
        ProxySelection selection = createSelection(selected.proxy(), request, false);
        
        // Register sticky session if needed
        if (request.sessionId() != null && request.operation().requiresSticky()) {
            stickySessions.put(request.sessionId(), selected.proxy().getProxyId());
        }
        
        log.info("Selected proxy: {} (tier={}, score={:.3f}, op={})",
            selected.proxy().getProxyId(), selected.proxy().getTier(),
            selected.score(), request.operation());
        
        return Optional.of(selection);
    }
    
    /**
     * Record success for a proxy selection.
     */
    public void recordSuccess(String proxyId, long latencyMs, long bytesTransferred) {
        ProxyMetrics metrics = metricsCache.getIfPresent(proxyId);
        if (metrics != null) {
            metrics.recordSuccess(latencyMs, bytesTransferred);
            circuitBreaker.recordSuccess(metrics.getTier());
        }
    }
    
    /**
     * Record failure for a proxy selection.
     */
    public void recordFailure(String proxyId, long latencyMs, int errorCode) {
        ProxyMetrics metrics = metricsCache.getIfPresent(proxyId);
        if (metrics != null) {
            metrics.recordFailure(latencyMs, errorCode);
            circuitBreaker.recordFailure(metrics.getTier());
        }
    }
    
    /**
     * Record ban for a proxy.
     */
    public void recordBan(String proxyId) {
        ProxyMetrics metrics = metricsCache.getIfPresent(proxyId);
        if (metrics != null) {
            metrics.recordBan();
            circuitBreaker.recordFailure(metrics.getTier());
        }
    }
    
    /**
     * Register a new proxy in the pool.
     */
    public void registerProxy(String proxyId, ProxyTier tier, String host, int port) {
        ProxyMetrics metrics = new ProxyMetrics(proxyId, tier, host, port);
        metricsCache.put(proxyId, metrics);
        proxyPools.get(tier).add(metrics);
        log.info("Registered proxy: {} (tier={})", proxyId, tier);
    }
    
    /**
     * Remove a proxy from the pool.
     */
    public void removeProxy(String proxyId) {
        ProxyMetrics metrics = metricsCache.getIfPresent(proxyId);
        if (metrics != null) {
            proxyPools.get(metrics.getTier()).remove(metrics);
            metricsCache.invalidate(proxyId);
            log.info("Removed proxy: {}", proxyId);
        }
    }
    
    /**
     * Get pool statistics.
     */
    public PoolStatistics getStatistics() {
        Map<ProxyTier, TierStats> tierStats = new EnumMap<>(ProxyTier.class);
        
        for (ProxyTier tier : ProxyTier.values()) {
            List<ProxyMetrics> pool = proxyPools.get(tier);
            long total = pool.size();
            long healthy = pool.stream().filter(ProxyMetrics::isAvailable).count();
            double avgHealth = pool.stream()
                .mapToDouble(ProxyMetrics::getHealthScore)
                .average().orElse(0.0);
            
            tierStats.put(tier, new TierStats(
                total, healthy, avgHealth,
                circuitBreaker.getState(tier),
                selectionCounts.getOrDefault(tier, 0L)
            ));
        }
        
        return new PoolStatistics(tierStats, operationCounts, stickySessions.estimatedSize());
    }
    
    // === Private Helpers ===
    
    private ProxyTier getPreferredTier(OperationType operation) {
        return switch (operation) {
            case ACCOUNT_CREATION -> ProxyTier.MOBILE;
            case EMAIL_VERIFICATION -> ProxyTier.RESIDENTIAL;
            case STREAM_OPERATION, PLAYLIST_OPERATION, FOLLOW_OPERATION -> ProxyTier.RESIDENTIAL;
            case INITIAL_QUERY -> ProxyTier.DATACENTER;
            case HEALTH_CHECK -> ProxyTier.DATACENTER;
        };
    }
    
    private List<ProxyMetrics> getCandidates(ProxyTier startTier, ProxyTier minTier, String targetCountry) {
        List<ProxyMetrics> candidates = new ArrayList<>();
        
        // Collect from startTier and all tiers that meet minimum
        for (ProxyTier tier : ProxyTier.values()) {
            if (tier.meetsRequirement(minTier) && circuitBreaker.isAllowed(tier)) {
                candidates.addAll(
                    proxyPools.get(tier).stream()
                        .filter(ProxyMetrics::isAvailable)
                        .collect(Collectors.toList())
                );
            }
        }
        
        return candidates;
    }
    
    private ScoredProxy selectWeightedRandom(List<ScoredProxy> scored) {
        if (scored.size() == 1) {
            return scored.get(0);
        }
        
        // Weighted random selection (higher score = higher chance)
        double totalScore = scored.stream().mapToDouble(ScoredProxy::score).sum();
        double random = Math.random() * totalScore;
        double cumulative = 0;
        
        for (ScoredProxy sp : scored) {
            cumulative += sp.score();
            if (random <= cumulative) {
                return sp;
            }
        }
        
        return scored.get(0); // Fallback
    }
    
    private ProxySelection createSelection(ProxyMetrics proxy, RoutingRequest request, boolean isSticky) {
        return new ProxySelection(
            proxy.getProxyId(),
            proxy.getTier(),
            proxy.getHost(),
            proxy.getPort(),
            request.operation(),
            proxy.getHealthScore(),
            proxy.getTier().getCostPerGb(),
            isSticky
        );
    }
    
    // === Inner Records ===
    
    private record ScoredProxy(ProxyMetrics proxy, double score) {}
    
    /**
     * Routing request input.
     */
    public record RoutingRequest(
        OperationType operation,
        String targetCountry,
        String sessionId,       // For sticky sessions
        int quantity,           // Remaining quantity for order
        double maxCostPerRequest // Budget constraint
    ) {
        public static RoutingRequest forOperation(OperationType op) {
            return new RoutingRequest(op, null, null, 1, 0.10);
        }
        
        public static RoutingRequest forOperationWithGeo(OperationType op, String country) {
            return new RoutingRequest(op, country, null, 1, 0.10);
        }
        
        public static RoutingRequest sticky(OperationType op, String sessionId) {
            return new RoutingRequest(op, null, sessionId, 1, 0.10);
        }
    }
    
    /**
     * Proxy selection result.
     */
    public record ProxySelection(
        String proxyId,
        ProxyTier tier,
        String host,
        int port,
        OperationType operation,
        double healthScore,
        double estimatedCostPerGb,
        boolean isSticky
    ) {
        public String getConnectionUrl() {
            return "http://" + host + ":" + port;
        }
    }
    
    /**
     * Statistics for a single tier.
     */
    public record TierStats(
        long totalProxies,
        long healthyProxies,
        double averageHealth,
        TierCircuitBreaker.CircuitState circuitState,
        long totalSelections
    ) {}
    
    /**
     * Overall pool statistics.
     */
    public record PoolStatistics(
        Map<ProxyTier, TierStats> tierStats,
        Map<OperationType, Long> operationCounts,
        long activeSessions
    ) {}
}
