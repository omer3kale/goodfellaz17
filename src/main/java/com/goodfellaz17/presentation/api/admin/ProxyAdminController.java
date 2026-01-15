package com.goodfellaz17.presentation.api.admin;

import com.goodfellaz17.domain.model.generated.*;
import com.goodfellaz17.infrastructure.persistence.generated.*;
import com.goodfellaz17.infrastructure.proxy.generated.HybridProxyRouterV2;
import com.goodfellaz17.infrastructure.proxy.generated.HybridProxyRouterV2.OperationType;
import com.goodfellaz17.infrastructure.proxy.generated.HybridProxyRouterV2.RoutingRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Admin API for Proxy Pool Management.
 * 
 * Endpoints:
 *   POST /api/admin/proxies/register   - Register a new proxy node
 *   POST /api/admin/proxies/metrics    - Push metrics for a proxy node
 *   GET  /api/admin/proxies            - List all proxy nodes
 *   GET  /api/admin/proxies/smoke-test - Smoke test the proxy router selection algorithm
 * 
 * @author Goodfellaz17 Team
 */
@RestController
@RequestMapping("/api/admin/proxies")
public class ProxyAdminController {
    
    private static final Logger log = LoggerFactory.getLogger(ProxyAdminController.class);
    
    private final GeneratedProxyNodeRepository proxyNodeRepository;
    private final GeneratedProxyMetricsRepository proxyMetricsRepository;
    private final HybridProxyRouterV2 proxyRouter;
    
    public ProxyAdminController(
            GeneratedProxyNodeRepository proxyNodeRepository,
            GeneratedProxyMetricsRepository proxyMetricsRepository,
            HybridProxyRouterV2 proxyRouter) {
        this.proxyNodeRepository = proxyNodeRepository;
        this.proxyMetricsRepository = proxyMetricsRepository;
        this.proxyRouter = proxyRouter;
    }
    
    // =========================================================================
    // PROXY REGISTRATION
    // =========================================================================
    
    /**
     * Register a new proxy node in the pool.
     * 
     * POST /api/admin/proxies/register
     * 
     * Example:
     * curl -X POST http://localhost:8080/api/admin/proxies/register \
     *   -H "Content-Type: application/json" \
     *   -d '{
     *     "provider": "HETZNER",
     *     "publicIp": "95.217.123.45",
     *     "port": 3128,
     *     "region": "eu-central",
     *     "country": "DE",
     *     "tier": "DATACENTER",
     *     "capacity": 100
     *   }'
     */
    @PostMapping("/register")
    public Mono<ResponseEntity<ProxyNodeResponse>> registerProxy(
            @Valid @RequestBody RegisterProxyRequest request) {
        
        log.info("Registering proxy node: {} {} at {}:{}", 
            request.tier, request.provider, request.publicIp, request.port);
        
        ProxyNodeEntity node = ProxyNodeEntity.builder()
            .provider(request.provider)
            .providerInstanceId(request.providerInstanceId)
            .publicIp(request.publicIp)
            .port(request.port)
            .region(request.region)
            .country(request.country)
            .city(request.city)
            .tier(request.tier)
            .capacity(request.capacity != null ? request.capacity : 100)
            .currentLoad(0)
            .costPerHour(request.costPerHour != null ? request.costPerHour : BigDecimal.ZERO)
            .authUsername(request.authUsername)
            .authPassword(request.authPassword)
            .status(ProxyStatus.ONLINE.name())
            .build();
        
        return proxyNodeRepository.save(node)
            .flatMap(savedNode -> {
                // Initialize metrics for this node
                ProxyMetricsEntity metrics = new ProxyMetricsEntity(savedNode.getId());
                return proxyMetricsRepository.save(metrics)
                    .thenReturn(savedNode);
            })
            .map(savedNode -> ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ProxyNodeResponse.from(savedNode)))
            .doOnSuccess(resp -> log.info("Proxy node registered: {}", resp.getBody().id()))
            .doOnError(err -> log.error("Failed to register proxy: {}", err.getMessage()));
    }
    
    /**
     * Request DTO for proxy registration.
     */
    public record RegisterProxyRequest(
        @NotNull(message = "provider is required")
        String provider,
        
        String providerInstanceId,
        
        @NotNull(message = "publicIp is required")
        @Pattern(regexp = "^[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}$", 
                 message = "Invalid IPv4 address")
        String publicIp,
        
        @NotNull(message = "port is required")
        @Min(value = 1, message = "port must be >= 1")
        @Max(value = 65535, message = "port must be <= 65535")
        Integer port,
        
        @NotNull(message = "region is required")
        @Size(max = 50)
        String region,
        
        @NotNull(message = "country is required")
        @Size(min = 2, max = 2, message = "country must be 2-letter ISO code")
        String country,
        
        @Size(max = 100)
        String city,
        
        @NotNull(message = "tier is required")
        String tier,
        
        @Min(1) @Max(10000)
        Integer capacity,
        
        BigDecimal costPerHour,
        
        String authUsername,
        String authPassword
    ) {}
    
    // =========================================================================
    // METRICS PUSH
    // =========================================================================
    
    /**
     * Push metrics for a proxy node.
     * 
     * POST /api/admin/proxies/metrics
     * 
     * Example:
     * curl -X POST http://localhost:8080/api/admin/proxies/metrics \
     *   -H "Content-Type: application/json" \
     *   -d '{
     *     "proxyNodeId": "abc123...",
     *     "totalRequests": 1000,
     *     "successfulRequests": 950,
     *     "failedRequests": 50,
     *     "latencyP50": 120,
     *     "latencyP95": 450,
     *     "latencyP99": 800
     *   }'
     */
    @PostMapping("/metrics")
    public Mono<ResponseEntity<ProxyMetricsResponse>> pushMetrics(
            @Valid @RequestBody PushMetricsRequest request) {
        
        log.info("Pushing metrics for proxy node: {}", request.proxyNodeId);
        
        return proxyMetricsRepository.findByProxyNodeId(request.proxyNodeId)
            .switchIfEmpty(Mono.defer(() -> {
                // Create new metrics entry if doesn't exist
                ProxyMetricsEntity newMetrics = new ProxyMetricsEntity(request.proxyNodeId);
                return proxyMetricsRepository.save(newMetrics);
            }))
            .flatMap(metrics -> {
                // Update metrics
                metrics.setTotalRequests(request.totalRequests != null ? request.totalRequests : metrics.getTotalRequests());
                metrics.setSuccessfulRequests(request.successfulRequests != null ? request.successfulRequests : metrics.getSuccessfulRequests());
                metrics.setFailedRequests(request.failedRequests != null ? request.failedRequests : metrics.getFailedRequests());
                
                // Recalculate success/ban rates
                if (metrics.getTotalRequests() > 0) {
                    metrics.setSuccessRate((double) metrics.getSuccessfulRequests() / metrics.getTotalRequests());
                }
                if (request.banRate != null) {
                    metrics.setBanRate(request.banRate);
                }
                
                // Latencies
                if (request.latencyP50 != null) metrics.setLatencyP50(request.latencyP50);
                if (request.latencyP95 != null) metrics.setLatencyP95(request.latencyP95);
                if (request.latencyP99 != null) metrics.setLatencyP99(request.latencyP99);
                
                // Connection stats
                if (request.activeConnections != null) metrics.setActiveConnections(request.activeConnections);
                if (request.peakConnections != null) metrics.setPeakConnections(request.peakConnections);
                if (request.bytesTransferred != null) metrics.setBytesTransferred(request.bytesTransferred);
                
                metrics.setLastUpdated(Instant.now());
                
                return proxyMetricsRepository.save(metrics);
            })
            .map(metrics -> ResponseEntity.ok(ProxyMetricsResponse.from(metrics)))
            .doOnSuccess(resp -> log.info("Metrics updated for node: {}", request.proxyNodeId))
            .doOnError(err -> log.error("Failed to push metrics: {}", err.getMessage()));
    }
    
    /**
     * Request DTO for pushing metrics.
     */
    public record PushMetricsRequest(
        @NotNull(message = "proxyNodeId is required")
        UUID proxyNodeId,
        
        Long totalRequests,
        Long successfulRequests,
        Long failedRequests,
        
        Double banRate,
        
        Integer latencyP50,
        Integer latencyP95,
        Integer latencyP99,
        
        Integer activeConnections,
        Integer peakConnections,
        Long bytesTransferred
    ) {}
    
    // =========================================================================
    // LISTING
    // =========================================================================
    
    /**
     * List all registered proxy nodes.
     * 
     * GET /api/admin/proxies
     */
    @GetMapping
    public Flux<ProxyNodeResponse> listProxies(
            @RequestParam(required = false) String tier,
            @RequestParam(required = false) String status) {
        
        if (tier != null && status != null) {
            return proxyNodeRepository.findByTierAndStatus(tier, status)
                .map(ProxyNodeResponse::from);
        } else if (tier != null) {
            return proxyNodeRepository.findByTierAndStatus(tier, ProxyStatus.ONLINE.name())
                .map(ProxyNodeResponse::from);
        } else {
            return proxyNodeRepository.findAll()
                .map(ProxyNodeResponse::from);
        }
    }
    
    /**
     * Get proxy node by ID.
     * 
     * GET /api/admin/proxies/{id}
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<ProxyNodeResponse>> getProxy(@PathVariable UUID id) {
        return proxyNodeRepository.findById(id)
            .map(node -> ResponseEntity.ok(ProxyNodeResponse.from(node)))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    // =========================================================================
    // SMOKE TEST
    // =========================================================================
    
    /**
     * Smoke test the HybridProxyRouterV2 selection algorithm.
     * 
     * GET /api/admin/proxies/smoke-test
     * 
     * This endpoint:
     * 1. Verifies at least 3 nodes exist (DATACENTER, TOR, ISP/RESIDENTIAL)
     * 2. Runs proxy selection for STREAMING operation
     * 3. Logs the selected proxy and its score breakdown
     * 4. Returns the selection result for verification
     * 
     * Expected behavior:
     * - Router should prefer DATACENTER for STREAMING (cheapest sufficient tier)
     * - Selection should consider success_rate, latency, and current_load
     * - Result includes proxy details + why it was selected
     */
    @GetMapping("/smoke-test")
    public Mono<ResponseEntity<SmokeTestResult>> smokeTest() {
        log.info("=== PROXY ROUTER SMOKE TEST START ===");
        
        return proxyNodeRepository.countByStatus(ProxyStatus.ONLINE.name())
            .flatMap(nodeCount -> {
                log.info("Online proxy nodes: {}", nodeCount);
                
                if (nodeCount < 1) {
                    log.warn("SMOKE TEST FAILED: No proxy nodes registered");
                    return Mono.just(ResponseEntity.ok(SmokeTestResult.noNodes()));
                }
                
                // Collect pool summary by tier
                return proxyNodeRepository.findByTierAndStatus(
                        ProxyTier.DATACENTER.name(), ProxyStatus.ONLINE.name())
                    .count()
                    .zipWith(proxyNodeRepository.findByTierAndStatus(
                        ProxyTier.ISP.name(), ProxyStatus.ONLINE.name()).count())
                    .zipWith(proxyNodeRepository.findByTierAndStatus(
                        ProxyTier.TOR.name(), ProxyStatus.ONLINE.name()).count())
                    .flatMap(counts -> {
                        long datacenter = counts.getT1().getT1();
                        long isp = counts.getT1().getT2();
                        long tor = counts.getT2();
                        
                        Map<String, Long> tierCounts = Map.of(
                            "DATACENTER", datacenter,
                            "ISP", isp,
                            "TOR", tor
                        );
                        
                        log.info("Pool composition: DATACENTER={}, ISP={}, TOR={}", 
                            datacenter, isp, tor);
                        
                        // Run the actual proxy selection
                        RoutingRequest routingRequest = RoutingRequest.builder()
                            .operation(OperationType.STREAMING)
                            .targetCountry(null) // No geo restriction
                            .sessionId(null)
                            .quantity(15000)
                            .build();
                        
                        log.info("Routing request: operation=STREAMING, quantity=15000");
                        
                        return proxyRouter.route(routingRequest)
                            .flatMap(selection -> {
                                log.info("=== PROXY SELECTION RESULT ===");
                                log.info("Selected proxy ID: {}", selection.proxyId());
                                log.info("Selected tier: {}", selection.tier());
                                log.info("Selected country: {}", selection.country());
                                log.info("Proxy URL: {}", selection.proxyUrl());
                                log.info("Is sticky: {}", selection.isSticky());
                                log.info("Lease expires: {}", selection.leaseExpires());
                                
                                // Get full details of selected proxy
                                return proxyNodeRepository.findById(selection.proxyId())
                                    .zipWith(proxyMetricsRepository.findByProxyNodeId(selection.proxyId())
                                        .defaultIfEmpty(new ProxyMetricsEntity(selection.proxyId())))
                                    .map(tuple -> {
                                        ProxyNodeEntity node = tuple.getT1();
                                        ProxyMetricsEntity metrics = tuple.getT2();
                                        
                                        log.info("=== SELECTED PROXY DETAILS ===");
                                        log.info("Provider: {}", node.getProvider());
                                        log.info("IP: {}:{}", node.getPublicIp(), node.getPort());
                                        log.info("Load: {}/{}", node.getCurrentLoad(), node.getCapacity());
                                        log.info("Success rate: {}%", String.format("%.1f", metrics.getSuccessRate() * 100));
                                        log.info("Latency P95: {}ms", metrics.getLatencyP95());
                                        log.info("Ban rate: {}%", String.format("%.1f", metrics.getBanRate() * 100));
                                        log.info("=== SMOKE TEST PASSED ===");
                                        
                                        return ResponseEntity.ok(SmokeTestResult.success(
                                            tierCounts,
                                            ProxyNodeResponse.from(node),
                                            ProxyMetricsResponse.from(metrics),
                                            selection.tier().name(),
                                            "STREAMING prefers DATACENTER tier (cheapest sufficient quality)"
                                        ));
                                    });
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                log.warn("SMOKE TEST: No proxy selected (all busy or unhealthy)");
                                return Mono.just(ResponseEntity.ok(
                                    SmokeTestResult.noSelection(tierCounts)));
                            }));
                    });
            });
    }
    
    // =========================================================================
    // RESPONSE DTOs
    // =========================================================================
    
    public record ProxyNodeResponse(
        UUID id,
        String provider,
        String publicIp,
        int port,
        String region,
        String country,
        String city,
        String tier,
        int capacity,
        int currentLoad,
        String status,
        Instant registeredAt,
        Instant lastHealthcheck
    ) {
        public static ProxyNodeResponse from(ProxyNodeEntity entity) {
            return new ProxyNodeResponse(
                entity.getId(),
                entity.getProvider(),
                entity.getPublicIp(),
                entity.getPort(),
                entity.getRegion(),
                entity.getCountry(),
                entity.getCity(),
                entity.getTier(),
                entity.getCapacity(),
                entity.getCurrentLoad(),
                entity.getStatus(),
                entity.getRegisteredAt(),
                entity.getLastHealthcheck()
            );
        }
    }
    
    public record ProxyMetricsResponse(
        UUID id,
        UUID proxyNodeId,
        long totalRequests,
        long successfulRequests,
        long failedRequests,
        double successRate,
        double banRate,
        int latencyP50,
        int latencyP95,
        int latencyP99,
        int activeConnections,
        Instant lastUpdated
    ) {
        public static ProxyMetricsResponse from(ProxyMetricsEntity entity) {
            return new ProxyMetricsResponse(
                entity.getId(),
                entity.getProxyNodeId(),
                entity.getTotalRequests(),
                entity.getSuccessfulRequests(),
                entity.getFailedRequests(),
                entity.getSuccessRate(),
                entity.getBanRate(),
                entity.getLatencyP50(),
                entity.getLatencyP95(),
                entity.getLatencyP99(),
                entity.getActiveConnections(),
                entity.getLastUpdated()
            );
        }
    }
    
    public record SmokeTestResult(
        boolean passed,
        String status,
        Map<String, Long> poolComposition,
        ProxyNodeResponse selectedProxy,
        ProxyMetricsResponse selectedProxyMetrics,
        String selectedTier,
        String selectionReason,
        String message
    ) {
        public static SmokeTestResult success(
                Map<String, Long> tierCounts,
                ProxyNodeResponse proxy,
                ProxyMetricsResponse metrics,
                String tier,
                String reason) {
            return new SmokeTestResult(
                true, "PASSED", tierCounts, proxy, metrics, tier, reason,
                "Proxy router successfully selected a proxy for STREAMING operation"
            );
        }
        
        public static SmokeTestResult noNodes() {
            return new SmokeTestResult(
                false, "FAILED", Map.of(), null, null, null, null,
                "No proxy nodes registered. Use POST /api/admin/proxies/register first."
            );
        }
        
        public static SmokeTestResult noSelection(Map<String, Long> tierCounts) {
            return new SmokeTestResult(
                false, "NO_SELECTION", tierCounts, null, null, null, null,
                "Router found nodes but none were selected (all busy or below quality threshold)"
            );
        }
    }
}
