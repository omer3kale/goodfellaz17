package com.goodfellaz17.infrastructure.proxy.health;

import com.goodfellaz17.domain.model.Proxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.net.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Real proxy health checker - tests actual connectivity.
 * 
 * Uses HTTP probe to verify proxy can reach external endpoints.
 * Marks proxies unhealthy after 3 consecutive failures.
 */
@Service
public class ProxyHealthChecker {
    
    private static final Logger log = LoggerFactory.getLogger(ProxyHealthChecker.class);
    
    // Test endpoint that returns minimal data
    private static final String HEALTH_CHECK_URL = "https://httpbin.org/ip";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    
    private final Map<String, Proxy> monitoredProxies = new ConcurrentHashMap<>();
    
    @Value("${proxy.health.enabled:true}")
    private boolean healthCheckEnabled;
    
    @Value("${proxy.health.interval-seconds:60}")
    private int healthCheckIntervalSeconds;
    
    /**
     * Register a proxy for health monitoring.
     */
    public void monitor(Proxy proxy) {
        if (proxy.getId() != null) {
            monitoredProxies.put(proxy.getId(), proxy);
        }
    }
    
    /**
     * Remove proxy from monitoring.
     */
    public void unmonitor(String proxyId) {
        monitoredProxies.remove(proxyId);
    }
    
    /**
     * Check health of a single proxy - real HTTP connectivity test.
     */
    public Mono<Boolean> checkHealth(Proxy proxy) {
        return Mono.fromCallable(() -> performHealthCheck(proxy))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(15))
                .doOnNext(healthy -> {
                    if (healthy) {
                        proxy.markHealthy();
                        log.debug("Proxy {} healthy", proxy.getId());
                    } else {
                        proxy.markUnhealthy();
                        log.warn("Proxy {} failed health check (failures: {})", 
                                proxy.getId(), proxy.getFailureCount());
                    }
                })
                .onErrorResume(e -> {
                    proxy.markUnhealthy();
                    log.warn("Proxy {} health check error: {}", proxy.getId(), e.getMessage());
                    return Mono.just(false);
                });
    }
    
    /**
     * Batch health check - returns count of healthy proxies.
     */
    public Mono<Long> checkHealthBatch(List<Proxy> proxies) {
        return Mono.fromCallable(() -> 
            proxies.parallelStream()
                    .map(this::performHealthCheck)
                    .filter(Boolean::booleanValue)
                    .count()
        ).subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * Scheduled health check for all monitored proxies.
     */
    @Scheduled(fixedRateString = "${proxy.health.interval-seconds:60}000")
    public void scheduledHealthCheck() {
        if (!healthCheckEnabled || monitoredProxies.isEmpty()) {
            return;
        }
        
        log.info("Running scheduled health check for {} proxies", monitoredProxies.size());
        
        monitoredProxies.values().parallelStream()
                .forEach(proxy -> {
                    boolean healthy = performHealthCheck(proxy);
                    if (healthy) {
                        proxy.markHealthy();
                    } else {
                        proxy.markUnhealthy();
                        if (!proxy.isHealthy()) {
                            log.warn("Proxy {} marked unhealthy after {} failures",
                                    proxy.getId(), proxy.getFailureCount());
                        }
                    }
                });
        
        long unhealthyCount = monitoredProxies.values().stream()
                .filter(p -> !p.isHealthy())
                .count();
        
        if (unhealthyCount > 0) {
            log.warn("{}/{} monitored proxies are unhealthy", 
                    unhealthyCount, monitoredProxies.size());
        }
    }
    
    /**
     * Get current health status of all monitored proxies.
     */
    public Map<String, Boolean> getHealthStatus() {
        Map<String, Boolean> status = new ConcurrentHashMap<>();
        monitoredProxies.forEach((id, proxy) -> status.put(id, proxy.isHealthy()));
        return status;
    }
    
    /**
     * Perform actual HTTP connectivity test through proxy.
     */
    private boolean performHealthCheck(Proxy proxy) {
        HttpURLConnection conn = null;
        try {
            // Configure proxy
            java.net.Proxy httpProxy = new java.net.Proxy(
                    proxy.getType() == Proxy.ProxyType.SOCKS5 || proxy.getType() == Proxy.ProxyType.SOCKS4
                            ? java.net.Proxy.Type.SOCKS
                            : java.net.Proxy.Type.HTTP,
                    new InetSocketAddress(proxy.getHost(), proxy.getPort())
            );
            
            // Set up authentication if needed
            if (proxy.getUsername() != null && !proxy.getUsername().isEmpty()) {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(
                                proxy.getUsername(),
                                proxy.getPassword().toCharArray()
                        );
                    }
                });
            }
            
            URL url = new URI(HEALTH_CHECK_URL).toURL();
            conn = (HttpURLConnection) url.openConnection(httpProxy);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            
            int responseCode = conn.getResponseCode();
            boolean success = responseCode >= 200 && responseCode < 400;
            
            if (success) {
                log.trace("Proxy {} returned HTTP {}", proxy.getId(), responseCode);
            } else {
                log.debug("Proxy {} returned HTTP {}", proxy.getId(), responseCode);
            }
            
            return success;
            
        } catch (SocketTimeoutException e) {
            log.debug("Proxy {} timeout: {}", proxy.getId(), e.getMessage());
            return false;
        } catch (IOException e) {
            log.debug("Proxy {} IO error: {}", proxy.getId(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.debug("Proxy {} error: {}", proxy.getId(), e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
