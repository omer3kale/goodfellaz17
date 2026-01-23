package com.goodfellaz17.infrastructure.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * ProxyExecutorClient - Calls real proxy nodes via HTTP.
 * 
 * This replaces simulated execution in OrderDeliveryWorker when
 * real proxy infrastructure is available.
 * 
 * Contract (matches proxy-node.sh):
 * - POST /execute with {taskId, orderId, plays, trackUrl}
 * - Returns {success: boolean, taskId, plays, nodeId, message}
 * 
 * Configuration:
 * - app.proxy.base-url: Base URL of proxy (default: http://localhost:9090)
 * - app.proxy.enabled: Enable real proxy calls (default: false)
 * 
 * Design Constraints (FROZEN from thesis):
 * - MUST NOT modify order invariants (INV-1 through INV-6)
 * - Success/failure results feed into existing OrderProgressUpdater
 * - Proxy failures trigger existing retry/backoff logic
 * 
 * @author Phase 3: Self-hosted proxy infrastructure
 * @since 1.0.0
 */
@Service
public class ProxyExecutorClient {
    
    private static final Logger log = LoggerFactory.getLogger(ProxyExecutorClient.class);
    
    private final WebClient webClient;
    private final String proxyBaseUrl;
    private final boolean proxyEnabled;
    private final Duration timeout;
    
    public ProxyExecutorClient(
            WebClient.Builder builder,
            @Value("${app.proxy.base-url:http://localhost:9090}") String proxyBaseUrl,
            @Value("${app.proxy.enabled:false}") boolean proxyEnabled,
            @Value("${app.proxy.timeout-ms:5000}") long timeoutMs) {
        this.webClient = builder
            .baseUrl(proxyBaseUrl)
            .build();
        this.proxyBaseUrl = proxyBaseUrl;
        this.proxyEnabled = proxyEnabled;
        this.timeout = Duration.ofMillis(timeoutMs);
        
        log.info("ProxyExecutorClient initialized | baseUrl={} | enabled={} | timeout={}ms",
            proxyBaseUrl, proxyEnabled, timeoutMs);
    }
    
    /**
     * Check if real proxy execution is enabled.
     * When disabled, OrderDeliveryWorker uses simulated execution.
     */
    public boolean isEnabled() {
        return proxyEnabled;
    }
    
    /**
     * Execute a delivery task via the real proxy node.
     * 
     * @param taskId Unique task identifier
     * @param orderId Parent order ID
     * @param plays Number of plays to deliver
     * @param trackUrl Spotify track URL (optional)
     * @param proxyId ID of the selected proxy (for routing/logging)
     * @return Execution result with success status
     */
    public Mono<ProxyExecutionResult> executeTask(
            String taskId, 
            String orderId, 
            int plays,
            String trackUrl,
            UUID proxyId) {
        
        String proxyIdStr = proxyId != null ? proxyId.toString() : "unknown";
        
        if (!proxyEnabled) {
            log.debug("Proxy execution disabled, returning simulated success | taskId={}", taskId);
            return Mono.just(new ProxyExecutionResult(true, taskId, plays, "simulated", "Proxy disabled"));
        }
        
        Map<String, Object> payload = Map.of(
            "taskId", taskId,
            "orderId", orderId,
            "plays", plays,
            "trackUrl", trackUrl != null ? trackUrl : "",
            "proxyId", proxyIdStr
        );
        
        log.info("PROXY_EXECUTE_START | taskId={} | orderId={} | plays={} | proxyId={}", 
            taskId, orderId, plays, proxyIdStr);
        
        return webClient
            .post()
            .uri("/execute")
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(Map.class)
            .timeout(timeout)
            .map(response -> {
                boolean success = (boolean) response.getOrDefault("success", false);
                String nodeId = (String) response.getOrDefault("nodeId", "unknown");
                String message = (String) response.getOrDefault("message", "");
                int deliveredPlays = response.containsKey("plays") 
                    ? ((Number) response.get("plays")).intValue() 
                    : plays;
                
                return new ProxyExecutionResult(success, taskId, deliveredPlays, nodeId, message);
            })
            .doOnNext(result -> {
                if (result.success()) {
                    log.info("PROXY_EXECUTE_SUCCESS | taskId={} | nodeId={} | plays={}", 
                        taskId, result.nodeId(), result.plays());
                } else {
                    log.warn("PROXY_EXECUTE_FAILED | taskId={} | nodeId={} | message={}", 
                        taskId, result.nodeId(), result.message());
                }
            })
            .onErrorResume(error -> {
                log.error("PROXY_EXECUTE_ERROR | taskId={} | error={}", taskId, error.getMessage());
                return Mono.just(new ProxyExecutionResult(
                    false, taskId, 0, "error", error.getMessage()
                ));
            });
    }
    
    /**
     * Check proxy node health.
     * 
     * @return Health status from proxy /health endpoint
     */
    public Mono<ProxyHealthStatus> checkHealth() {
        return webClient
            .get()
            .uri("/health")
            .retrieve()
            .bodyToMono(Map.class)
            .timeout(Duration.ofSeconds(2))
            .map(response -> new ProxyHealthStatus(
                (String) response.getOrDefault("status", "UNKNOWN"),
                (String) response.getOrDefault("nodeId", "unknown"),
                proxyBaseUrl
            ))
            .onErrorResume(error -> Mono.just(new ProxyHealthStatus(
                "OFFLINE", "unknown", proxyBaseUrl
            )));
    }
    
    /**
     * Result of proxy task execution.
     */
    public record ProxyExecutionResult(
        boolean success,
        String taskId,
        int plays,
        String nodeId,
        String message
    ) {}
    
    /**
     * Proxy health status.
     */
    public record ProxyHealthStatus(
        String status,
        String nodeId,
        String url
    ) {}
}
