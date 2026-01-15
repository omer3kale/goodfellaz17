package com.goodfellaz17.application.service.generated;

import com.goodfellaz17.domain.model.generated.*;
import com.goodfellaz17.infrastructure.persistence.generated.*;
import com.goodfellaz17.infrastructure.proxy.generated.HybridProxyRouterV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * Service: ProxyManagementService
 * 
 * Orchestrates the proxy pool including health monitoring, load balancing,
 * and automatic failover. Integrates with HybridProxyRouterV2 for selection.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
@Service
public class GeneratedProxyManagementService {
    
    private static final Logger log = LoggerFactory.getLogger(GeneratedProxyManagementService.class);
    
    private static final double MIN_HEALTHY_SUCCESS_RATE = 0.7;
    private static final double MAX_HEALTHY_BAN_RATE = 0.1;
    private static final int MAX_LATENCY_MS = 5000;
    
    private final GeneratedProxyNodeRepository proxyNodeRepository;
    private final GeneratedProxyMetricsRepository metricsRepository;
    private final GeneratedDeviceNodeRepository deviceNodeRepository;
    private final GeneratedTorCircuitRepository torCircuitRepository;
    private final HybridProxyRouterV2 proxyRouter;
    
    // In-memory tracking for real-time metrics
    private final ConcurrentHashMap<UUID, ProxyNodeHealth> healthCache = new ConcurrentHashMap<>();
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    
    public GeneratedProxyManagementService(
            GeneratedProxyNodeRepository proxyNodeRepository,
            GeneratedProxyMetricsRepository metricsRepository,
            GeneratedDeviceNodeRepository deviceNodeRepository,
            GeneratedTorCircuitRepository torCircuitRepository,
            HybridProxyRouterV2 proxyRouter) {
        this.proxyNodeRepository = proxyNodeRepository;
        this.metricsRepository = metricsRepository;
        this.deviceNodeRepository = deviceNodeRepository;
        this.torCircuitRepository = torCircuitRepository;
        this.proxyRouter = proxyRouter;
    }
    
    /**
     * Select optimal proxy for a request based on geo profile and tier preference.
     */
    public Mono<ProxySelection> selectProxy(GeoProfile geoProfile, ProxyTier preferredTier) {
        return proxyRouter.selectProxy(geoProfile, preferredTier)
            .map(node -> {
                // Also select a device fingerprint for the request
                return deviceNodeRepository.findRandomHealthyByPlatform(
                        selectPlatformForGeo(geoProfile), MAX_HEALTHY_BAN_RATE)
                    .map(device -> new ProxySelection(node, device, null))
                    .defaultIfEmpty(new ProxySelection(node, null, null));
            })
            .flatMap(mono -> mono)
            .doOnSuccess(selection -> 
                log.debug("Selected proxy {} for geo={}", 
                    selection.proxyNode().getId(), geoProfile));
    }
    
    /**
     * Select proxy with Tor circuit for maximum anonymity.
     */
    public Mono<ProxySelection> selectProxyWithTor(GeoProfile geoProfile) {
        return Mono.zip(
            proxyRouter.selectProxy(geoProfile, ProxyTier.PREMIUM),
            torCircuitRepository.findActiveByCountry(mapGeoToCountry(geoProfile))
                .switchIfEmpty(torCircuitRepository.findActiveHighBandwidth(1).next()),
            deviceNodeRepository.findRandomHealthyDevices(MAX_HEALTHY_BAN_RATE, 1).next()
        ).map(tuple -> new ProxySelection(tuple.getT1(), tuple.getT3(), tuple.getT2()));
    }
    
    /**
     * Record successful request through proxy.
     */
    public Mono<Void> recordSuccess(UUID proxyNodeId, long latencyMs, long bytesTransferred) {
        totalRequests.incrementAndGet();
        successfulRequests.incrementAndGet();
        
        return metricsRepository.recordSuccess(proxyNodeId, bytesTransferred)
            .then(Mono.fromRunnable(() -> {
                healthCache.compute(proxyNodeId, (id, health) -> {
                    if (health == null) health = new ProxyNodeHealth();
                    health.recordSuccess(latencyMs);
                    return health;
                });
            }));
    }
    
    /**
     * Record failed request through proxy.
     */
    public Mono<Void> recordFailure(UUID proxyNodeId, int errorCode) {
        totalRequests.incrementAndGet();
        
        return metricsRepository.recordFailure(proxyNodeId)
            .then(Mono.fromRunnable(() -> {
                healthCache.compute(proxyNodeId, (id, health) -> {
                    if (health == null) health = new ProxyNodeHealth();
                    health.recordFailure(errorCode);
                    return health;
                });
                
                // Check if proxy should be circuit-broken
                ProxyNodeHealth health = healthCache.get(proxyNodeId);
                if (health != null && health.shouldCircuitBreak()) {
                    proxyRouter.circuitBreak(proxyNodeId);
                }
            }));
    }
    
    /**
     * Get pool health summary.
     */
    public Mono<PoolHealthSummary> getPoolHealth() {
        return Mono.zip(
            proxyNodeRepository.countByStatus(ProxyStatus.ACTIVE.name()),
            proxyNodeRepository.countByStatus(ProxyStatus.DEGRADED.name()),
            proxyNodeRepository.countByStatus(ProxyStatus.OFFLINE.name()),
            metricsRepository.getPoolSummary()
                .map(arr -> arr != null && arr.length > 0 ? arr : new Object[]{0L, 0L, 0.0, 0.0, 0L})
        ).map(tuple -> new PoolHealthSummary(
            tuple.getT1().intValue(),
            tuple.getT2().intValue(),
            tuple.getT3().intValue(),
            totalRequests.get(),
            successfulRequests.get(),
            calculateOverallSuccessRate()
        ));
    }
    
    /**
     * Get detailed metrics for a specific proxy.
     */
    public Mono<ProxyDetailedMetrics> getProxyMetrics(UUID proxyNodeId) {
        return Mono.zip(
            proxyNodeRepository.findById(proxyNodeId),
            metricsRepository.findByProxyNodeId(proxyNodeId)
        ).map(tuple -> new ProxyDetailedMetrics(
            tuple.getT1(),
            tuple.getT2(),
            healthCache.get(proxyNodeId)
        ));
    }
    
    /**
     * Find proxies needing attention (degraded performance).
     */
    public Flux<ProxyNodeEntity> findProxiesNeedingAttention() {
        return proxyNodeRepository.findByStatus(ProxyStatus.DEGRADED.name())
            .concatWith(metricsRepository.findUnhealthyMetrics(MIN_HEALTHY_SUCCESS_RATE)
                .flatMap(metrics -> proxyNodeRepository.findById(metrics.getProxyNodeId())));
    }
    
    /**
     * Rotate Tor circuits that need rotation.
     */
    @Scheduled(fixedDelay = 600000) // Every 10 minutes
    public void rotateExpiredTorCircuits() {
        torCircuitRepository.findCircuitsNeedingRotation()
            .flatMap(circuit -> {
                // Generate new circuit details (in production, call Tor control port)
                String newCircuitId = UUID.randomUUID().toString().substring(0, 16);
                String newExitIp = "10.0.0." + (int)(Math.random() * 255);
                
                return torCircuitRepository.rotateCircuit(
                    circuit.getId(), newCircuitId, newExitIp, circuit.getExitCountry());
            })
            .subscribe(
                null,
                e -> log.error("Failed to rotate Tor circuits", e),
                () -> log.debug("Completed Tor circuit rotation check")
            );
    }
    
    /**
     * Disable high ban rate device fingerprints.
     */
    @Scheduled(fixedDelay = 3600000) // Every hour
    public void disableHighBanRateDevices() {
        deviceNodeRepository.disableHighBanRateDevices(0.15)
            .count()
            .subscribe(count -> {
                if (count > 0) {
                    log.warn("Disabled {} device fingerprints due to high ban rate", count);
                }
            });
    }
    
    /**
     * Reset metrics windows daily.
     */
    @Scheduled(cron = "0 0 0 * * *") // Midnight daily
    public void resetDailyMetrics() {
        Instant threshold = Instant.now().minus(Duration.ofDays(1));
        metricsRepository.resetOldMetricsWindows(threshold)
            .subscribe(count -> log.info("Reset {} proxy metrics windows", count));
        
        // Reset in-memory counters
        totalRequests.set(0);
        successfulRequests.set(0);
        healthCache.clear();
    }
    
    // === Helper Methods ===
    
    private String selectPlatformForGeo(GeoProfile geoProfile) {
        return switch (geoProfile) {
            case USA, EUROPE, WORLDWIDE -> "Windows";
            case LATAM, MIDDLE_EAST -> "Android";
            case ASIA -> Math.random() > 0.5 ? "Android" : "iOS";
            case PREMIUM_TIER -> "macOS";
        };
    }
    
    private String mapGeoToCountry(GeoProfile geoProfile) {
        return switch (geoProfile) {
            case USA -> "US";
            case EUROPE -> "DE";
            case ASIA -> "JP";
            case LATAM -> "BR";
            case MIDDLE_EAST -> "AE";
            case WORLDWIDE, PREMIUM_TIER -> "US";
        };
    }
    
    private double calculateOverallSuccessRate() {
        long total = totalRequests.get();
        if (total == 0) return 1.0;
        return (double) successfulRequests.get() / total;
    }
    
    // === Inner Classes ===
    
    /**
     * Combined proxy selection with optional device and Tor circuit.
     */
    public record ProxySelection(
        ProxyNodeEntity proxyNode,
        DeviceNodeEntity deviceNode,
        TorCircuitEntity torCircuit
    ) {
        public boolean hasTor() {
            return torCircuit != null;
        }
        
        public boolean hasDevice() {
            return deviceNode != null;
        }
    }
    
    /**
     * Pool health summary.
     */
    public record PoolHealthSummary(
        int activeProxies,
        int degradedProxies,
        int offlineProxies,
        long totalRequests,
        long successfulRequests,
        double successRate
    ) {
        public int totalProxies() {
            return activeProxies + degradedProxies + offlineProxies;
        }
        
        public double healthyPercentage() {
            int total = totalProxies();
            if (total == 0) return 0.0;
            return (double) activeProxies / total;
        }
    }
    
    /**
     * Detailed metrics for a single proxy.
     */
    public record ProxyDetailedMetrics(
        ProxyNodeEntity node,
        ProxyMetricsEntity metrics,
        ProxyNodeHealth realtimeHealth
    ) {}
    
    /**
     * In-memory health tracking per proxy.
     */
    public static class ProxyNodeHealth {
        private long successCount = 0;
        private long failureCount = 0;
        private long totalLatencyMs = 0;
        private int consecutiveFailures = 0;
        private Instant lastFailure = null;
        
        public synchronized void recordSuccess(long latencyMs) {
            successCount++;
            totalLatencyMs += latencyMs;
            consecutiveFailures = 0;
        }
        
        public synchronized void recordFailure(int errorCode) {
            failureCount++;
            consecutiveFailures++;
            lastFailure = Instant.now();
        }
        
        public double getSuccessRate() {
            long total = successCount + failureCount;
            if (total == 0) return 1.0;
            return (double) successCount / total;
        }
        
        public long getAverageLatencyMs() {
            if (successCount == 0) return 0;
            return totalLatencyMs / successCount;
        }
        
        public boolean shouldCircuitBreak() {
            return consecutiveFailures >= 5 || getSuccessRate() < 0.5;
        }
    }
}
