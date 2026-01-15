package com.goodfellaz17.presentation.api.admin;

import com.goodfellaz17.application.testing.FailureInjectionService;
import com.goodfellaz17.application.worker.OrderDeliveryWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * FailureInjectionController - Admin API for stress testing.
 * 
 * ONLY AVAILABLE IN local/dev/test PROFILES.
 * 
 * Provides endpoints to:
 * - Enable/disable failure injection
 * - Configure failure rates
 * - Ban/unban proxies
 * - Pause/resume execution
 * - View injection metrics
 * 
 * @author goodfellaz17
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/admin/testing")
@Profile({"local", "dev", "test"})
public class FailureInjectionController {
    
    private static final Logger log = LoggerFactory.getLogger(FailureInjectionController.class);
    
    private final OrderDeliveryWorker deliveryWorker;
    
    public FailureInjectionController(OrderDeliveryWorker deliveryWorker) {
        this.deliveryWorker = deliveryWorker;
        log.warn("⚠️  FailureInjectionController loaded - stress testing endpoints available");
    }
    
    // =========================================================================
    // STATUS
    // =========================================================================
    
    /**
     * GET /api/admin/testing/status
     * 
     * Get current failure injection status and metrics.
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<StatusResponse>> getStatus() {
        FailureInjectionService service = deliveryWorker.getFailureInjectionService();
        
        if (service == null) {
            return Mono.just(ResponseEntity.ok(new StatusResponse(
                false,
                "FailureInjectionService not available",
                null,
                null
            )));
        }
        
        return Mono.just(ResponseEntity.ok(new StatusResponse(
            true,
            service.isEnabled() ? "CHAOS MODE ACTIVE" : "Normal mode",
            service.getStatus(),
            deliveryWorker.getMetrics()
        )));
    }
    
    // =========================================================================
    // CONTROL
    // =========================================================================
    
    /**
     * POST /api/admin/testing/enable
     * 
     * Enable failure injection (master switch).
     */
    @PostMapping("/enable")
    public Mono<ResponseEntity<ControlResponse>> enable() {
        FailureInjectionService service = deliveryWorker.getFailureInjectionService();
        
        if (service == null) {
            return Mono.just(ResponseEntity.badRequest()
                .body(new ControlResponse(false, "FailureInjectionService not available")));
        }
        
        service.enable();
        log.warn("🔥 FAILURE_INJECTION_ENABLED via admin API");
        
        return Mono.just(ResponseEntity.ok(new ControlResponse(
            true, 
            "Failure injection ENABLED - chaos mode activated!"
        )));
    }
    
    /**
     * POST /api/admin/testing/disable
     * 
     * Disable failure injection (master switch).
     */
    @PostMapping("/disable")
    public Mono<ResponseEntity<ControlResponse>> disable() {
        FailureInjectionService service = deliveryWorker.getFailureInjectionService();
        
        if (service == null) {
            return Mono.just(ResponseEntity.badRequest()
                .body(new ControlResponse(false, "FailureInjectionService not available")));
        }
        
        service.disable();
        log.info("✅ FAILURE_INJECTION_DISABLED via admin API");
        
        return Mono.just(ResponseEntity.ok(new ControlResponse(
            true, 
            "Failure injection DISABLED - normal mode restored"
        )));
    }
    
    /**
     * POST /api/admin/testing/reset
     * 
     * Reset all injection settings to defaults.
     */
    @PostMapping("/reset")
    public Mono<ResponseEntity<ControlResponse>> reset() {
        FailureInjectionService service = deliveryWorker.getFailureInjectionService();
        
        if (service == null) {
            return Mono.just(ResponseEntity.badRequest()
                .body(new ControlResponse(false, "FailureInjectionService not available")));
        }
        
        service.reset();
        log.info("✅ FAILURE_INJECTION_RESET via admin API");
        
        return Mono.just(ResponseEntity.ok(new ControlResponse(
            true, 
            "All injection settings reset to defaults"
        )));
    }
    
    // =========================================================================
    // CONFIGURATION
    // =========================================================================
    
    /**
     * POST /api/admin/testing/config
     * 
     * Configure failure injection parameters.
     */
    @PostMapping("/config")
    public Mono<ResponseEntity<ControlResponse>> configure(@RequestBody ConfigRequest request) {
        FailureInjectionService service = deliveryWorker.getFailureInjectionService();
        
        if (service == null) {
            return Mono.just(ResponseEntity.badRequest()
                .body(new ControlResponse(false, "FailureInjectionService not available")));
        }
        
        StringBuilder changes = new StringBuilder();
        
        if (request.failurePercentage != null) {
            service.setFailurePercentage(request.failurePercentage);
            changes.append("failurePercentage=").append(request.failurePercentage).append("; ");
        }
        
        if (request.timeoutPercentage != null) {
            service.setTimeoutPercentage(request.timeoutPercentage);
            changes.append("timeoutPercentage=").append(request.timeoutPercentage).append("; ");
        }
        
        if (request.latencyMs != null) {
            service.setLatencyMs(request.latencyMs);
            changes.append("latencyMs=").append(request.latencyMs).append("; ");
        }
        
        log.info("INJECTION_CONFIG_UPDATED | {}", changes);
        
        return Mono.just(ResponseEntity.ok(new ControlResponse(
            true, 
            "Configuration updated: " + changes
        )));
    }
    
    // =========================================================================
    // PAUSE/RESUME
    // =========================================================================
    
    /**
     * POST /api/admin/testing/pause
     * 
     * Pause all task executions.
     */
    @PostMapping("/pause")
    public Mono<ResponseEntity<ControlResponse>> pause() {
        FailureInjectionService service = deliveryWorker.getFailureInjectionService();
        
        if (service == null) {
            return Mono.just(ResponseEntity.badRequest()
                .body(new ControlResponse(false, "FailureInjectionService not available")));
        }
        
        service.enable(); // Must be enabled first
        service.pause();
        log.warn("⏸️  EXECUTION_PAUSED via admin API");
        
        return Mono.just(ResponseEntity.ok(new ControlResponse(
            true, 
            "Execution PAUSED - all task processing halted"
        )));
    }
    
    /**
     * POST /api/admin/testing/resume
     * 
     * Resume task executions.
     */
    @PostMapping("/resume")
    public Mono<ResponseEntity<ControlResponse>> resume() {
        FailureInjectionService service = deliveryWorker.getFailureInjectionService();
        
        if (service == null) {
            return Mono.just(ResponseEntity.badRequest()
                .body(new ControlResponse(false, "FailureInjectionService not available")));
        }
        
        service.resume();
        log.info("▶️  EXECUTION_RESUMED via admin API");
        
        return Mono.just(ResponseEntity.ok(new ControlResponse(
            true, 
            "Execution RESUMED - task processing continues"
        )));
    }
    
    // =========================================================================
    // PROXY BANNING
    // =========================================================================
    
    /**
     * POST /api/admin/testing/ban-proxy/{proxyId}
     * 
     * Ban a proxy (permanent until cleared).
     */
    @PostMapping("/ban-proxy/{proxyId}")
    public Mono<ResponseEntity<ControlResponse>> banProxy(
            @PathVariable UUID proxyId,
            @RequestParam(required = false) Long durationSeconds) {
        
        FailureInjectionService service = deliveryWorker.getFailureInjectionService();
        
        if (service == null) {
            return Mono.just(ResponseEntity.badRequest()
                .body(new ControlResponse(false, "FailureInjectionService not available")));
        }
        
        service.enable(); // Must be enabled first
        
        if (durationSeconds != null && durationSeconds > 0) {
            service.banProxyTemporarily(proxyId, Duration.ofSeconds(durationSeconds));
            log.warn("🚫 PROXY_TEMP_BANNED | proxyId={} | duration={}s", proxyId, durationSeconds);
            return Mono.just(ResponseEntity.ok(new ControlResponse(
                true, 
                "Proxy " + proxyId + " banned for " + durationSeconds + " seconds"
            )));
        } else {
            service.banProxy(proxyId);
            log.warn("🚫 PROXY_BANNED | proxyId={}", proxyId);
            return Mono.just(ResponseEntity.ok(new ControlResponse(
                true, 
                "Proxy " + proxyId + " permanently banned"
            )));
        }
    }
    
    /**
     * POST /api/admin/testing/unban-proxy/{proxyId}
     * 
     * Unban a proxy.
     */
    @PostMapping("/unban-proxy/{proxyId}")
    public Mono<ResponseEntity<ControlResponse>> unbanProxy(@PathVariable UUID proxyId) {
        FailureInjectionService service = deliveryWorker.getFailureInjectionService();
        
        if (service == null) {
            return Mono.just(ResponseEntity.badRequest()
                .body(new ControlResponse(false, "FailureInjectionService not available")));
        }
        
        service.unbanProxy(proxyId);
        log.info("✅ PROXY_UNBANNED | proxyId={}", proxyId);
        
        return Mono.just(ResponseEntity.ok(new ControlResponse(
            true, 
            "Proxy " + proxyId + " unbanned"
        )));
    }
    
    /**
     * POST /api/admin/testing/clear-bans
     * 
     * Clear all proxy bans.
     */
    @PostMapping("/clear-bans")
    public Mono<ResponseEntity<ControlResponse>> clearBans() {
        FailureInjectionService service = deliveryWorker.getFailureInjectionService();
        
        if (service == null) {
            return Mono.just(ResponseEntity.badRequest()
                .body(new ControlResponse(false, "FailureInjectionService not available")));
        }
        
        service.clearAllBans();
        log.info("✅ ALL_BANS_CLEARED via admin API");
        
        return Mono.just(ResponseEntity.ok(new ControlResponse(
            true, 
            "All proxy bans cleared"
        )));
    }
    
    // =========================================================================
    // CHAOS PRESETS
    // =========================================================================
    
    /**
     * POST /api/admin/testing/preset/mild
     * 
     * Mild chaos: 10% failures, no latency.
     */
    @PostMapping("/preset/mild")
    public Mono<ResponseEntity<ControlResponse>> presetMild() {
        return applyPreset("mild", 10, 0, 0);
    }
    
    /**
     * POST /api/admin/testing/preset/moderate
     * 
     * Moderate chaos: 25% failures, 5% timeouts, 100ms latency.
     */
    @PostMapping("/preset/moderate")
    public Mono<ResponseEntity<ControlResponse>> presetModerate() {
        return applyPreset("moderate", 25, 5, 100);
    }
    
    /**
     * POST /api/admin/testing/preset/severe
     * 
     * Severe chaos: 50% failures, 15% timeouts, 500ms latency.
     */
    @PostMapping("/preset/severe")
    public Mono<ResponseEntity<ControlResponse>> presetSevere() {
        return applyPreset("severe", 50, 15, 500);
    }
    
    /**
     * POST /api/admin/testing/preset/catastrophic
     * 
     * Catastrophic: 80% failures, 30% timeouts, 2000ms latency.
     * Use for extreme stress testing only!
     */
    @PostMapping("/preset/catastrophic")
    public Mono<ResponseEntity<ControlResponse>> presetCatastrophic() {
        return applyPreset("catastrophic", 80, 30, 2000);
    }
    
    private Mono<ResponseEntity<ControlResponse>> applyPreset(
            String name, int failurePct, int timeoutPct, long latencyMs) {
        
        FailureInjectionService service = deliveryWorker.getFailureInjectionService();
        
        if (service == null) {
            return Mono.just(ResponseEntity.badRequest()
                .body(new ControlResponse(false, "FailureInjectionService not available")));
        }
        
        service.enable();
        service.setFailurePercentage(failurePct);
        service.setTimeoutPercentage(timeoutPct);
        service.setLatencyMs(latencyMs);
        
        log.warn("🎲 CHAOS_PRESET_APPLIED | preset={} | failures={}% | timeouts={}% | latency={}ms", 
            name, failurePct, timeoutPct, latencyMs);
        
        return Mono.just(ResponseEntity.ok(new ControlResponse(
            true, 
            String.format("Preset '%s' applied: %d%% failures, %d%% timeouts, %dms latency",
                name, failurePct, timeoutPct, latencyMs)
        )));
    }
    
    // =========================================================================
    // DTOs
    // =========================================================================
    
    public record StatusResponse(
        boolean available,
        String message,
        FailureInjectionService.InjectionStatus injectionStatus,
        OrderDeliveryWorker.WorkerMetrics workerMetrics
    ) {}
    
    public record ControlResponse(
        boolean success,
        String message
    ) {}
    
    public record ConfigRequest(
        Integer failurePercentage,
        Integer timeoutPercentage,
        Long latencyMs
    ) {}
}
