package com.goodfellaz17.presentation.api;

import com.goodfellaz17.application.proxy.ProxyTaskDispatchService;
import com.goodfellaz17.application.proxy.ProxyTaskDispatchService.TaskExecutionResult;
import com.goodfellaz17.domain.model.generated.*;
import com.goodfellaz17.infrastructure.persistence.generated.GeneratedProxyNodeRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * ProxyMetricsController - API endpoints for proxy machine communication.
 * 
 * Phase 1 Architecture (from spec):
 * - Proxy machine calls these endpoints to:
 *   1. Report task execution results
 *   2. Update proxy metrics (success rate, latency, etc.)
 *   3. Report health status changes
 * 
 * All money logic stays in Order & Billing BC.
 * Proxy process only updates technical metrics.
 * 
 * @author goodfellaz17
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/proxy-metrics")
public class ProxyMetricsController {
    
    private static final Logger log = LoggerFactory.getLogger(ProxyMetricsController.class);
    
    private final ProxyTaskDispatchService dispatchService;
    private final GeneratedProxyNodeRepository proxyRepository;
    private final Counter metricsReported;
    private final Counter healthUpdates;
    
    public ProxyMetricsController(
            ProxyTaskDispatchService dispatchService,
            GeneratedProxyNodeRepository proxyRepository,
            MeterRegistry meterRegistry) {
        this.dispatchService = dispatchService;
        this.proxyRepository = proxyRepository;
        
        this.metricsReported = Counter.builder("proxy.metrics.reported")
            .description("Proxy metrics reports received")
            .register(meterRegistry);
        this.healthUpdates = Counter.builder("proxy.health.updates")
            .description("Proxy health state updates")
            .register(meterRegistry);
    }
    
    // =========================================================================
    // TASK RESULT REPORTING
    // =========================================================================
    
    /**
     * Report task execution result from proxy machine.
     * 
     * Called by proxy machine after task completes (success or failure).
     * Updates proxy metrics and triggers health state recalculation.
     */
    @PostMapping("/task-result")
    public Mono<ResponseEntity<TaskResultResponse>> reportTaskResult(
            @RequestBody TaskResultRequest request) {
        
        log.info("TASK_RESULT_REPORTED | taskId={} | proxyId={} | success={} | playsDelivered={}",
            request.taskId, request.proxyId, request.success, request.playsDelivered);
        
        TaskExecutionResult result = new TaskExecutionResult(
            request.taskId,
            request.proxyId,
            request.success,
            request.playsDelivered,
            request.errorCode,
            request.errorMessage,
            request.latencyMs,
            request.consecutiveFailures
        );
        
        return dispatchService.reportTaskResult(result)
            .then(Mono.just(ResponseEntity.ok(
                new TaskResultResponse("ACCEPTED", "Task result recorded")
            )))
            .doOnSuccess(r -> metricsReported.increment());
    }
    
    // =========================================================================
    // METRICS BATCH UPDATE
    // =========================================================================
    
    /**
     * Batch update proxy metrics from proxy machine.
     * 
     * Called periodically (e.g., every 30s) with aggregated metrics.
     */
    @PostMapping("/batch")
    public Mono<ResponseEntity<BatchUpdateResponse>> batchUpdateMetrics(
            @RequestBody MetricsBatchRequest request) {
        
        log.info("METRICS_BATCH_RECEIVED | proxyCount={} | timestamp={}",
            request.metrics.size(), request.timestamp);
        
        // Process each proxy's metrics update
        return processMetricsBatch(request.metrics)
            .then(Mono.just(ResponseEntity.ok(
                new BatchUpdateResponse("ACCEPTED", request.metrics.size())
            )));
    }
    
    private Mono<Void> processMetricsBatch(List<ProxyMetricsUpdate> metrics) {
        return reactor.core.publisher.Flux.fromIterable(metrics)
            .flatMap(this::updateProxyMetrics)
            .then();
    }
    
    private Mono<Void> updateProxyMetrics(ProxyMetricsUpdate update) {
        // Compute health state from success rate
        ProxyHealthState newState = ProxyHealthState.fromSuccessRate(update.successRate);
        
        log.debug("PROXY_METRICS_UPDATE | proxyId={} | successRate={} | newHealthState={}",
            update.proxyId, update.successRate, newState);
        
        return proxyRepository.updateHealthState(update.proxyId, newState.name())
            .doOnSuccess(proxy -> {
                if (proxy != null) {
                    healthUpdates.increment();
                    log.info("PROXY_HEALTH_UPDATED | proxyId={} | healthState={} | successRate={}",
                        proxy.getId(), proxy.getHealthState(), update.successRate);
                }
            })
            .then();
    }
    
    // =========================================================================
    // PROXY HEALTH ENDPOINTS
    // =========================================================================
    
    /**
     * Get current health state of all proxies.
     * Used by proxy machine to sync state on startup.
     */
    @GetMapping("/health-states")
    public Mono<ResponseEntity<HealthStatesResponse>> getHealthStates() {
        return proxyRepository.findAll()
            .map(proxy -> new ProxyHealthSummary(
                proxy.getId(),
                proxy.getPublicIp(),
                proxy.getTier(),
                proxy.getHealthState(),
                proxy.getStatus(),
                proxy.getCurrentLoad(),
                proxy.getCapacity()
            ))
            .collectList()
            .map(summaries -> ResponseEntity.ok(new HealthStatesResponse(
                summaries.size(),
                summaries.stream().filter(s -> "HEALTHY".equals(s.healthState)).count(),
                summaries.stream().filter(s -> "DEGRADED".equals(s.healthState)).count(),
                summaries.stream().filter(s -> "OFFLINE".equals(s.healthState)).count(),
                summaries
            )));
    }
    
    /**
     * Force health state update for a specific proxy.
     * Used for manual intervention or testing.
     */
    @PutMapping("/{proxyId}/health-state")
    public Mono<ResponseEntity<ProxyHealthSummary>> updateHealthState(
            @PathVariable UUID proxyId,
            @RequestBody HealthStateUpdateRequest request) {
        
        log.info("HEALTH_STATE_MANUAL_UPDATE | proxyId={} | newState={} | reason={}",
            proxyId, request.healthState, request.reason);
        
        return proxyRepository.updateHealthState(proxyId, request.healthState)
            .map(proxy -> ResponseEntity.ok(new ProxyHealthSummary(
                proxy.getId(),
                proxy.getPublicIp(),
                proxy.getTier(),
                proxy.getHealthState(),
                proxy.getStatus(),
                proxy.getCurrentLoad(),
                proxy.getCapacity()
            )))
            .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }
    
    /**
     * Get proxy selection stats for monitoring.
     */
    @GetMapping("/selection-stats")
    public Mono<ResponseEntity<SelectionStats>> getSelectionStats() {
        return Mono.zip(
            proxyRepository.countByHealthState("HEALTHY"),
            proxyRepository.countByHealthState("DEGRADED"),
            proxyRepository.countByHealthState("OFFLINE"),
            proxyRepository.countByStatus("ONLINE")
        ).map(tuple -> ResponseEntity.ok(new SelectionStats(
            tuple.getT1(), // healthy
            tuple.getT2(), // degraded
            tuple.getT3(), // offline
            tuple.getT4()  // online (operational status)
        )));
    }
    
    // =========================================================================
    // DTOs
    // =========================================================================
    
    // --- Request DTOs ---
    
    public record TaskResultRequest(
        UUID taskId,
        UUID proxyId,
        boolean success,
        int playsDelivered,
        int errorCode,
        String errorMessage,
        int latencyMs,
        int consecutiveFailures
    ) {}
    
    public record MetricsBatchRequest(
        Instant timestamp,
        List<ProxyMetricsUpdate> metrics
    ) {}
    
    public record ProxyMetricsUpdate(
        UUID proxyId,
        double successRate,
        double banRate,
        int latencyP50,
        int latencyP95,
        long totalRequests,
        long successfulRequests,
        long failedRequests,
        int activeConnections
    ) {}
    
    public record HealthStateUpdateRequest(
        String healthState,  // HEALTHY, DEGRADED, OFFLINE
        String reason        // Manual override reason
    ) {}
    
    // --- Response DTOs ---
    
    public record TaskResultResponse(
        String status,
        String message
    ) {}
    
    public record BatchUpdateResponse(
        String status,
        int processedCount
    ) {}
    
    public record HealthStatesResponse(
        long totalProxies,
        long healthyCount,
        long degradedCount,
        long offlineCount,
        List<ProxyHealthSummary> proxies
    ) {}
    
    public record ProxyHealthSummary(
        UUID proxyId,
        String ip,
        String tier,
        String healthState,
        String status,
        int currentLoad,
        int capacity
    ) {}
    
    public record SelectionStats(
        long healthyProxies,
        long degradedProxies,
        long offlineProxies,
        long onlineProxies
    ) {}
}
