package com.goodfellaz17.application.proxy;

import com.goodfellaz17.domain.model.generated.*;
import com.goodfellaz17.infrastructure.persistence.generated.GeneratedProxyNodeRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * ProxyTaskDispatchService - Dispatches tasks to the proxy machine.
 * 
 * Phase 1 Architecture (from spec):
 * - Mac runs Goodfellaz17 (order management, billing, task breakdown)
 * - New laptop runs proxy process (task execution, metrics reporting)
 * - Communication via HTTP endpoints
 * 
 * Task Flow:
 * 1. OrderDeliveryWorker picks up task
 * 2. ProxyTaskDispatchService selects best proxy via health-based selection
 * 3. Dispatch task payload to proxy machine: {taskId, trackUrl, plays, proxyNodeId}
 * 4. Proxy machine executes task and reports result
 * 5. Result updates task status and proxy metrics
 * 
 * Selection Rules:
 * - Prefer HEALTHY (successRate >= 0.85)
 * - Fallback to DEGRADED (successRate >= 0.70) with logging
 * - Never select OFFLINE (successRate < 0.70)
 * - Tier preference: MOBILE > RESIDENTIAL > ISP > DATACENTER
 * - Within same health/tier: prefer lowest load
 * 
 * @author goodfellaz17
 * @since 1.0.0
 */
@Service
public class ProxyTaskDispatchService {
    
    private static final Logger log = LoggerFactory.getLogger(ProxyTaskDispatchService.class);
    
    // === Configuration ===
    private static final int SELECT_CANDIDATES = 5;
    private static final Duration DISPATCH_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration RESULT_TIMEOUT = Duration.ofSeconds(60);
    
    // === Dependencies ===
    private final GeneratedProxyNodeRepository proxyRepository;
    private final WebClient proxyMachineClient;
    
    // === Metrics ===
    private final Counter tasksDispatched;
    private final Counter tasksDispatchedDegraded;
    private final Counter tasksDispatchFailed;
    private final Counter proxySelectionFailed;
    private final Timer dispatchLatency;
    
    @Value("${goodfellaz17.proxy-machine.url:http://localhost:8081}")
    private String proxyMachineUrl;
    
    @Value("${goodfellaz17.proxy-machine.enabled:false}")
    private boolean proxyMachineEnabled;
    
    public ProxyTaskDispatchService(
            GeneratedProxyNodeRepository proxyRepository,
            WebClient.Builder webClientBuilder,
            MeterRegistry meterRegistry) {
        this.proxyRepository = proxyRepository;
        this.proxyMachineClient = webClientBuilder
            .baseUrl(proxyMachineUrl)
            .build();
        
        // Initialize metrics
        this.tasksDispatched = Counter.builder("proxy.tasks.dispatched")
            .description("Total tasks dispatched to proxy machine")
            .register(meterRegistry);
        this.tasksDispatchedDegraded = Counter.builder("proxy.tasks.dispatched.degraded")
            .description("Tasks dispatched using degraded proxies")
            .register(meterRegistry);
        this.tasksDispatchFailed = Counter.builder("proxy.tasks.dispatch.failed")
            .description("Task dispatch failures")
            .register(meterRegistry);
        this.proxySelectionFailed = Counter.builder("proxy.selection.failed")
            .description("Proxy selection failures (no available proxy)")
            .register(meterRegistry);
        this.dispatchLatency = Timer.builder("proxy.dispatch.latency")
            .description("Task dispatch latency")
            .register(meterRegistry);
    }
    
    // =========================================================================
    // PUBLIC API
    // =========================================================================
    
    /**
     * Select best proxy and dispatch task to proxy machine.
     * 
     * @param task The task to execute
     * @param trackUrl The Spotify track URL from the order
     * @return TaskDispatchResult with proxy selection and dispatch status
     */
    public Mono<TaskDispatchResult> dispatchTask(OrderTaskEntity task, String trackUrl) {
        log.debug("TASK_DISPATCH_START | taskId={} | orderId={} | plays={}",
            task.getId(), task.getOrderId(), task.getQuantity());
        
        Instant startTime = Instant.now();
        
        return selectBestProxy(task)
            .flatMap(proxy -> {
                // Log degraded proxy usage
                if (proxy.isDegraded()) {
                    log.warn("DEGRADED_PROXY_SELECTED | taskId={} | proxyId={} | tier={} | healthState={}",
                        task.getId(), proxy.getId(), proxy.getTier(), proxy.getHealthState());
                    tasksDispatchedDegraded.increment();
                }
                
                return dispatchToProxyMachine(task, trackUrl, proxy)
                    .map(response -> {
                        Duration latency = Duration.between(startTime, Instant.now());
                        dispatchLatency.record(latency);
                        tasksDispatched.increment();
                        
                        log.info("TASK_DISPATCHED | taskId={} | proxyId={} | tier={} | latencyMs={}",
                            task.getId(), proxy.getId(), proxy.getTier(), latency.toMillis());
                        
                        return TaskDispatchResult.success(task.getId(), proxy, response);
                    });
            })
            .switchIfEmpty(Mono.fromCallable(() -> {
                proxySelectionFailed.increment();
                log.error("PROXY_SELECTION_FAILED | taskId={} | reason=no_available_proxy", task.getId());
                return TaskDispatchResult.noProxy(task.getId());
            }))
            .onErrorResume(e -> {
                tasksDispatchFailed.increment();
                log.error("TASK_DISPATCH_FAILED | taskId={} | error={}", task.getId(), e.getMessage());
                return Mono.just(TaskDispatchResult.failed(task.getId(), e.getMessage()));
            });
    }
    
    /**
     * Select best available proxy without dispatching.
     * Useful for dry-run testing or manual proxy selection.
     */
    public Mono<ProxyNodeEntity> selectBestProxy(OrderTaskEntity task) {
        // Try HEALTHY proxies first
        return proxyRepository.findHealthyOnly(SELECT_CANDIDATES)
            .next()
            .switchIfEmpty(
                // Fallback to DEGRADED if no HEALTHY available
                proxyRepository.findBestByHealth(SELECT_CANDIDATES)
                    .filter(ProxyNodeEntity::isDegraded)
                    .next()
            );
    }
    
    /**
     * Select best proxy for a specific country.
     */
    public Mono<ProxyNodeEntity> selectBestProxyForCountry(String country) {
        return proxyRepository.findBestByHealthAndCountry(country, SELECT_CANDIDATES)
            .next();
    }
    
    /**
     * Report task execution result from proxy machine.
     * Updates proxy health state and metrics.
     */
    public Mono<Void> reportTaskResult(TaskExecutionResult result) {
        log.debug("TASK_RESULT_RECEIVED | taskId={} | proxyId={} | success={} | errorCode={}",
            result.taskId(), result.proxyId(), result.success(), result.errorCode());
        
        // Update proxy health state based on result
        if (!result.success()) {
            return handleFailedTask(result);
        }
        
        return Mono.empty();
    }
    
    // =========================================================================
    // PRIVATE METHODS
    // =========================================================================
    
    private Mono<ProxyDispatchResponse> dispatchToProxyMachine(OrderTaskEntity task, String trackUrl, ProxyNodeEntity proxy) {
        if (!proxyMachineEnabled) {
            // Simulate dispatch in development mode
            log.debug("MOCK_DISPATCH | proxyMachineEnabled=false | taskId={}", task.getId());
            return Mono.just(new ProxyDispatchResponse(
                task.getId(),
                proxy.getId(),
                "ACCEPTED",
                null
            ));
        }
        
        TaskDispatchPayload payload = new TaskDispatchPayload(
            task.getId(),
            trackUrl,
            task.getQuantity(),
            proxy.getId(),
            proxy.getPublicIp(),
            proxy.getPort(),
            proxy.getAuthUsername(),
            proxy.getAuthPassword(),
            proxy.getTier()
        );
        
        return proxyMachineClient.post()
            .uri("/api/v1/execute-task")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(ProxyDispatchResponse.class)
            .timeout(DISPATCH_TIMEOUT)
            .doOnError(e -> log.error("PROXY_MACHINE_ERROR | taskId={} | error={}", 
                task.getId(), e.getMessage()));
    }
    
    private Mono<Void> handleFailedTask(TaskExecutionResult result) {
        // Check if error warrants health state change
        if (shouldDegradeProxy(result)) {
            String newState = ProxyHealthState.DEGRADED.name();
            if (result.errorCode() == 403 || result.errorCode() == 429) {
                // Banned or rate limited - take offline
                newState = ProxyHealthState.OFFLINE.name();
            }
            
            log.warn("PROXY_HEALTH_DOWNGRADE | proxyId={} | errorCode={} | newState={}",
                result.proxyId(), result.errorCode(), newState);
            
            return proxyRepository.updateHealthState(result.proxyId(), newState).then();
        }
        
        return Mono.empty();
    }
    
    private boolean shouldDegradeProxy(TaskExecutionResult result) {
        // Consecutive failures or specific error codes trigger degradation
        return result.consecutiveFailures() >= 3 
            || result.errorCode() == 403 
            || result.errorCode() == 429;
    }
    
    // =========================================================================
    // DTOs
    // =========================================================================
    
    /**
     * Payload sent to proxy machine for task execution.
     */
    public record TaskDispatchPayload(
        UUID taskId,
        String trackUrl,
        int plays,
        UUID proxyNodeId,
        String proxyIp,
        int proxyPort,
        String proxyUsername,
        String proxyPassword,
        String proxyTier
    ) {}
    
    /**
     * Response from proxy machine after accepting task.
     */
    public record ProxyDispatchResponse(
        UUID taskId,
        UUID proxyId,
        String status,     // ACCEPTED, REJECTED
        String message
    ) {}
    
    /**
     * Result of task dispatch operation.
     */
    public record TaskDispatchResult(
        UUID taskId,
        boolean success,
        ProxyNodeEntity proxy,
        ProxyDispatchResponse response,
        String errorMessage
    ) {
        public static TaskDispatchResult success(UUID taskId, ProxyNodeEntity proxy, ProxyDispatchResponse response) {
            return new TaskDispatchResult(taskId, true, proxy, response, null);
        }
        
        public static TaskDispatchResult noProxy(UUID taskId) {
            return new TaskDispatchResult(taskId, false, null, null, "No proxy available");
        }
        
        public static TaskDispatchResult failed(UUID taskId, String error) {
            return new TaskDispatchResult(taskId, false, null, null, error);
        }
    }
    
    /**
     * Task execution result reported by proxy machine.
     */
    public record TaskExecutionResult(
        UUID taskId,
        UUID proxyId,
        boolean success,
        int playsDelivered,
        int errorCode,
        String errorMessage,
        int latencyMs,
        int consecutiveFailures
    ) {}
}
