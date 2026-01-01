package com.goodfellaz17.infrastructure.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Proxy Health Monitor.
 * 
 * Periodically checks proxy health by:
 * - TCP connectivity tests
 * - HTTP request tests
 * - Latency measurements
 * - Ban detection (429, 403 patterns)
 * 
 * Updates ProxyMetrics with results for routing decisions.
 */
@Component
public class ProxyHealthMonitor {
    
    private static final Logger log = LoggerFactory.getLogger(ProxyHealthMonitor.class);
    
    // Configuration
    private static final int HEALTH_CHECK_TIMEOUT_MS = 10_000;
    private static final int MAX_CONCURRENT_CHECKS = 50;
    private static final String TEST_URL = "https://open.spotify.com";
    
    // Dependencies
    private final HybridProxyRouter proxyRouter;
    private final TierCircuitBreaker circuitBreaker;
    
    // Health check executor
    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_CHECKS);
    
    // Statistics
    private final AtomicInteger totalChecks = new AtomicInteger(0);
    private final AtomicInteger healthyChecks = new AtomicInteger(0);
    private final AtomicInteger unhealthyChecks = new AtomicInteger(0);
    private volatile Instant lastCheckRun = null;
    
    // Registered proxies to monitor
    private final Map<String, ProxyEndpoint> registeredProxies = new ConcurrentHashMap<>();
    
    public ProxyHealthMonitor(HybridProxyRouter proxyRouter, TierCircuitBreaker circuitBreaker) {
        this.proxyRouter = proxyRouter;
        this.circuitBreaker = circuitBreaker;
    }
    
    /**
     * Register a proxy for health monitoring.
     */
    public void registerProxy(String proxyId, ProxyTier tier, String host, int port, 
                               String username, String password) {
        registeredProxies.put(proxyId, new ProxyEndpoint(proxyId, tier, host, port, username, password));
        log.info("Registered proxy for monitoring: {} ({}:{})", proxyId, host, port);
    }
    
    /**
     * Unregister a proxy from monitoring.
     */
    public void unregisterProxy(String proxyId) {
        registeredProxies.remove(proxyId);
        log.info("Unregistered proxy from monitoring: {}", proxyId);
    }
    
    /**
     * Scheduled health check - runs every 60 seconds.
     */
    @Scheduled(fixedDelay = 60000)
    public void runScheduledHealthChecks() {
        if (registeredProxies.isEmpty()) {
            log.debug("No proxies registered for health monitoring");
            return;
        }
        
        log.info("Starting health check for {} proxies", registeredProxies.size());
        lastCheckRun = Instant.now();
        
        List<CompletableFuture<HealthCheckResult>> futures = new ArrayList<>();
        
        for (ProxyEndpoint proxy : registeredProxies.values()) {
            CompletableFuture<HealthCheckResult> future = CompletableFuture
                .supplyAsync(() -> checkProxyHealth(proxy), executor)
                .orTimeout(HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> new HealthCheckResult(proxy.proxyId(), false, 
                    HEALTH_CHECK_TIMEOUT_MS, "Timeout: " + ex.getMessage()));
            
            futures.add(future);
        }
        
        // Wait for all checks to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                int healthy = 0;
                int unhealthy = 0;
                
                for (CompletableFuture<HealthCheckResult> future : futures) {
                    try {
                        HealthCheckResult result = future.get();
                        processHealthResult(result);
                        
                        if (result.healthy()) {
                            healthy++;
                        } else {
                            unhealthy++;
                        }
                    } catch (Exception e) {
                        unhealthy++;
                    }
                }
                
                totalChecks.addAndGet(healthy + unhealthy);
                healthyChecks.addAndGet(healthy);
                unhealthyChecks.addAndGet(unhealthy);
                
                log.info("Health check complete: {} healthy, {} unhealthy", healthy, unhealthy);
            });
    }
    
    /**
     * Check health of a single proxy.
     */
    private HealthCheckResult checkProxyHealth(ProxyEndpoint proxyEndpoint) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Create proxy connection
            Proxy proxy = new Proxy(Proxy.Type.HTTP, 
                new InetSocketAddress(proxyEndpoint.host(), proxyEndpoint.port()));
            
            URL url = new URL(TEST_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxy);
            
            // Set timeouts
            connection.setConnectTimeout(HEALTH_CHECK_TIMEOUT_MS / 2);
            connection.setReadTimeout(HEALTH_CHECK_TIMEOUT_MS / 2);
            connection.setRequestMethod("HEAD");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0");
            
            // Execute request
            int responseCode = connection.getResponseCode();
            long latency = System.currentTimeMillis() - startTime;
            
            // Evaluate response
            boolean healthy = responseCode >= 200 && responseCode < 400;
            String message = healthy ? "OK" : "HTTP " + responseCode;
            
            // Detect specific issues
            if (responseCode == 429) {
                message = "RATE_LIMITED";
            } else if (responseCode == 403) {
                message = "FORBIDDEN";
            } else if (responseCode == 407) {
                message = "PROXY_AUTH_REQUIRED";
            }
            
            connection.disconnect();
            
            return new HealthCheckResult(
                proxyEndpoint.proxyId(),
                healthy,
                latency,
                message
            );
            
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            return new HealthCheckResult(
                proxyEndpoint.proxyId(),
                false,
                latency,
                "Error: " + e.getClass().getSimpleName() + " - " + e.getMessage()
            );
        }
    }
    
    /**
     * Process health check result and update metrics.
     */
    private void processHealthResult(HealthCheckResult result) {
        ProxyEndpoint endpoint = registeredProxies.get(result.proxyId());
        if (endpoint == null) return;
        
        if (result.healthy()) {
            proxyRouter.recordSuccess(result.proxyId(), result.latencyMs(), 0);
        } else {
            // Determine error code for tracking
            int errorCode = switch (result.message()) {
                case "RATE_LIMITED" -> 429;
                case "FORBIDDEN" -> 403;
                case "PROXY_AUTH_REQUIRED" -> 407;
                default -> 500;
            };
            
            proxyRouter.recordFailure(result.proxyId(), result.latencyMs(), errorCode);
            
            // Check for ban pattern
            if (result.message().equals("FORBIDDEN") || result.message().equals("RATE_LIMITED")) {
                proxyRouter.recordBan(result.proxyId());
            }
        }
    }
    
    /**
     * Get health monitoring statistics.
     */
    public HealthStats getStats() {
        return new HealthStats(
            registeredProxies.size(),
            totalChecks.get(),
            healthyChecks.get(),
            unhealthyChecks.get(),
            lastCheckRun
        );
    }
    
    /**
     * Get health status by tier.
     */
    public Map<ProxyTier, TierHealthStatus> getHealthByTier() {
        Map<ProxyTier, List<ProxyEndpoint>> byTier = new EnumMap<>(ProxyTier.class);
        for (ProxyTier tier : ProxyTier.values()) {
            byTier.put(tier, new ArrayList<>());
        }
        
        for (ProxyEndpoint endpoint : registeredProxies.values()) {
            byTier.get(endpoint.tier()).add(endpoint);
        }
        
        Map<ProxyTier, TierHealthStatus> result = new EnumMap<>(ProxyTier.class);
        for (ProxyTier tier : ProxyTier.values()) {
            List<ProxyEndpoint> proxies = byTier.get(tier);
            result.put(tier, new TierHealthStatus(
                tier,
                proxies.size(),
                circuitBreaker.getState(tier),
                circuitBreaker.isAllowed(tier)
            ));
        }
        
        return result;
    }
    
    /**
     * Manually trigger health check (for testing/debugging).
     */
    public CompletableFuture<Map<String, HealthCheckResult>> runManualCheck() {
        Map<String, HealthCheckResult> results = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (ProxyEndpoint proxy : registeredProxies.values()) {
            CompletableFuture<Void> future = CompletableFuture
                .supplyAsync(() -> checkProxyHealth(proxy), executor)
                .thenAccept(result -> results.put(result.proxyId(), result));
            futures.add(future);
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> results);
    }
    
    // === Inner Records ===
    
    /**
     * Proxy endpoint configuration.
     */
    public record ProxyEndpoint(
        String proxyId,
        ProxyTier tier,
        String host,
        int port,
        String username,
        String password
    ) {}
    
    /**
     * Health check result.
     */
    public record HealthCheckResult(
        String proxyId,
        boolean healthy,
        long latencyMs,
        String message
    ) {}
    
    /**
     * Overall health statistics.
     */
    public record HealthStats(
        int registeredProxies,
        int totalChecks,
        int healthyChecks,
        int unhealthyChecks,
        Instant lastCheckRun
    ) {
        public double getHealthRate() {
            return totalChecks > 0 ? (double) healthyChecks / totalChecks : 0.0;
        }
    }
    
    /**
     * Health status for a tier.
     */
    public record TierHealthStatus(
        ProxyTier tier,
        int proxyCount,
        TierCircuitBreaker.CircuitState circuitState,
        boolean available
    ) {}
}
