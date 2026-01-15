package com.goodfellaz17.application.testing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * FailureInjectionService - Controlled chaos for stress testing.
 * 
 * ONLY ACTIVE IN local/dev PROFILES - completely disabled in production.
 * 
 * Capabilities:
 * - Random task execution failures (configurable percentage)
 * - Proxy ban simulation (specific proxyNodeIds)
 * - Network timeout simulation
 * - Execution pause/resume
 * - Controlled latency injection
 * 
 * All injected failures are logged for observability.
 * 
 * @author goodfellaz17
 * @since 1.0.0
 */
@Service
@Profile({"local", "dev", "test"})
public class FailureInjectionService {
    
    private static final Logger log = LoggerFactory.getLogger(FailureInjectionService.class);
    
    // === Configuration ===
    
    /** Master switch - must be explicitly enabled */
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    
    /** Percentage of executions to fail randomly (0-100) */
    private final AtomicInteger failurePercentage = new AtomicInteger(0);
    
    /** Simulated network timeout percentage (0-100) */
    private final AtomicInteger timeoutPercentage = new AtomicInteger(0);
    
    /** Simulated latency in milliseconds */
    private final AtomicLong latencyMs = new AtomicLong(0);
    
    /** Pause all executions */
    private final AtomicBoolean paused = new AtomicBoolean(false);
    
    /** Banned proxy IDs (simulate proxy bans) */
    private final Set<UUID> bannedProxies = ConcurrentHashMap.newKeySet();
    
    /** Banned proxy IDs with expiry time */
    private final Map<UUID, Instant> temporaryBans = new ConcurrentHashMap<>();
    
    // === Metrics ===
    
    private final AtomicLong injectedFailures = new AtomicLong(0);
    private final AtomicLong injectedTimeouts = new AtomicLong(0);
    private final AtomicLong injectedLatency = new AtomicLong(0);
    private final AtomicLong proxyBanHits = new AtomicLong(0);
    private final AtomicLong pauseWaits = new AtomicLong(0);
    
    @Value("${spring.profiles.active:prod}")
    private String activeProfile;
    
    public FailureInjectionService() {
        log.warn("⚠️  FAILURE_INJECTION_SERVICE_LOADED | This service is for testing only!");
    }
    
    // =========================================================================
    // CONTROL API
    // =========================================================================
    
    /**
     * Enable failure injection (master switch).
     */
    public void enable() {
        if (isProductionProfile()) {
            log.error("FAILURE_INJECTION_BLOCKED | Cannot enable in production!");
            return;
        }
        enabled.set(true);
        log.warn("🔥 FAILURE_INJECTION_ENABLED | Chaos mode activated!");
    }
    
    /**
     * Disable failure injection (master switch).
     */
    public void disable() {
        enabled.set(false);
        log.info("✅ FAILURE_INJECTION_DISABLED | Normal mode restored");
    }
    
    /**
     * Check if failure injection is enabled.
     */
    public boolean isEnabled() {
        return enabled.get() && !isProductionProfile();
    }
    
    /**
     * Set random failure percentage (0-100).
     */
    public void setFailurePercentage(int percentage) {
        failurePercentage.set(Math.max(0, Math.min(100, percentage)));
        log.info("FAILURE_PERCENTAGE_SET | percentage={}", failurePercentage.get());
    }
    
    /**
     * Set network timeout percentage (0-100).
     */
    public void setTimeoutPercentage(int percentage) {
        timeoutPercentage.set(Math.max(0, Math.min(100, percentage)));
        log.info("TIMEOUT_PERCENTAGE_SET | percentage={}", timeoutPercentage.get());
    }
    
    /**
     * Set simulated latency in milliseconds.
     */
    public void setLatencyMs(long ms) {
        latencyMs.set(Math.max(0, ms));
        log.info("LATENCY_SET | latencyMs={}", latencyMs.get());
    }
    
    /**
     * Pause all executions.
     */
    public void pause() {
        paused.set(true);
        log.warn("⏸️  EXECUTION_PAUSED | All task executions are paused");
    }
    
    /**
     * Resume executions.
     */
    public void resume() {
        paused.set(false);
        log.info("▶️  EXECUTION_RESUMED | Task executions resumed");
    }
    
    /**
     * Ban a proxy permanently (until cleared).
     */
    public void banProxy(UUID proxyId) {
        bannedProxies.add(proxyId);
        log.warn("🚫 PROXY_BANNED | proxyId={}", proxyId);
    }
    
    /**
     * Ban a proxy temporarily.
     */
    public void banProxyTemporarily(UUID proxyId, Duration duration) {
        temporaryBans.put(proxyId, Instant.now().plus(duration));
        log.warn("⏰ PROXY_TEMP_BANNED | proxyId={} | duration={}", proxyId, duration);
    }
    
    /**
     * Unban a proxy.
     */
    public void unbanProxy(UUID proxyId) {
        bannedProxies.remove(proxyId);
        temporaryBans.remove(proxyId);
        log.info("✅ PROXY_UNBANNED | proxyId={}", proxyId);
    }
    
    /**
     * Clear all proxy bans.
     */
    public void clearAllBans() {
        bannedProxies.clear();
        temporaryBans.clear();
        log.info("✅ ALL_BANS_CLEARED");
    }
    
    /**
     * Reset all settings to defaults.
     */
    public void reset() {
        enabled.set(false);
        failurePercentage.set(0);
        timeoutPercentage.set(0);
        latencyMs.set(0);
        paused.set(false);
        bannedProxies.clear();
        temporaryBans.clear();
        resetMetrics();
        log.info("✅ FAILURE_INJECTION_RESET | All settings cleared");
    }
    
    /**
     * Reset metrics only.
     */
    public void resetMetrics() {
        injectedFailures.set(0);
        injectedTimeouts.set(0);
        injectedLatency.set(0);
        proxyBanHits.set(0);
        pauseWaits.set(0);
    }
    
    // =========================================================================
    // INJECTION HOOKS (called by worker)
    // =========================================================================
    
    /**
     * Check if execution should be paused.
     * Returns a Mono that delays if paused.
     */
    public Mono<Void> checkPause() {
        if (!isEnabled()) {
            return Mono.empty();
        }
        
        if (paused.get()) {
            pauseWaits.incrementAndGet();
            log.debug("EXECUTION_PAUSED_WAIT | Waiting for resume...");
            
            // Poll every second until unpaused
            return Mono.defer(() -> {
                if (paused.get()) {
                    return Mono.delay(Duration.ofSeconds(1)).then(checkPause());
                }
                return Mono.empty();
            });
        }
        
        return Mono.empty();
    }
    
    /**
     * Check if a proxy is banned.
     */
    public boolean isProxyBanned(UUID proxyId) {
        if (!isEnabled() || proxyId == null) {
            return false;
        }
        
        // Check permanent ban
        if (bannedProxies.contains(proxyId)) {
            proxyBanHits.incrementAndGet();
            log.debug("PROXY_BAN_HIT | proxyId={} | type=permanent", proxyId);
            return true;
        }
        
        // Check temporary ban
        Instant expiry = temporaryBans.get(proxyId);
        if (expiry != null) {
            if (Instant.now().isBefore(expiry)) {
                proxyBanHits.incrementAndGet();
                log.debug("PROXY_BAN_HIT | proxyId={} | type=temporary | expiresAt={}", proxyId, expiry);
                return true;
            } else {
                // Expired - remove
                temporaryBans.remove(proxyId);
            }
        }
        
        return false;
    }
    
    /**
     * Possibly inject a random failure.
     * Returns true if execution should fail.
     */
    public boolean shouldInjectFailure() {
        if (!isEnabled() || failurePercentage.get() == 0) {
            return false;
        }
        
        boolean shouldFail = ThreadLocalRandom.current().nextInt(100) < failurePercentage.get();
        
        if (shouldFail) {
            injectedFailures.incrementAndGet();
            log.debug("FAILURE_INJECTED | count={}", injectedFailures.get());
        }
        
        return shouldFail;
    }
    
    /**
     * Possibly inject a timeout.
     * Returns true if execution should timeout.
     */
    public boolean shouldInjectTimeout() {
        if (!isEnabled() || timeoutPercentage.get() == 0) {
            return false;
        }
        
        boolean shouldTimeout = ThreadLocalRandom.current().nextInt(100) < timeoutPercentage.get();
        
        if (shouldTimeout) {
            injectedTimeouts.incrementAndGet();
            log.debug("TIMEOUT_INJECTED | count={}", injectedTimeouts.get());
        }
        
        return shouldTimeout;
    }
    
    /**
     * Get the latency to inject (may be 0).
     */
    public Mono<Void> injectLatency() {
        if (!isEnabled()) {
            return Mono.empty();
        }
        
        long delay = latencyMs.get();
        if (delay > 0) {
            injectedLatency.incrementAndGet();
            return Mono.delay(Duration.ofMillis(delay)).then();
        }
        
        return Mono.empty();
    }
    
    /**
     * Combined injection check - returns failure reason or null if should proceed.
     */
    public InjectionResult checkInjections(UUID proxyId, UUID taskId) {
        if (!isEnabled()) {
            return InjectionResult.proceed();
        }
        
        // Check pause first
        if (paused.get()) {
            return InjectionResult.paused();
        }
        
        // Check proxy ban
        if (isProxyBanned(proxyId)) {
            return InjectionResult.proxyBanned(proxyId);
        }
        
        // Check random failure
        if (shouldInjectFailure()) {
            return InjectionResult.randomFailure();
        }
        
        // Check timeout
        if (shouldInjectTimeout()) {
            return InjectionResult.timeout();
        }
        
        return InjectionResult.proceed();
    }
    
    // =========================================================================
    // METRICS API
    // =========================================================================
    
    /**
     * Get current status and metrics.
     */
    public InjectionStatus getStatus() {
        return new InjectionStatus(
            isEnabled(),
            failurePercentage.get(),
            timeoutPercentage.get(),
            latencyMs.get(),
            paused.get(),
            bannedProxies.size(),
            temporaryBans.size(),
            getMetrics()
        );
    }
    
    /**
     * Get injection metrics.
     */
    public InjectionMetrics getMetrics() {
        return new InjectionMetrics(
            injectedFailures.get(),
            injectedTimeouts.get(),
            injectedLatency.get(),
            proxyBanHits.get(),
            pauseWaits.get()
        );
    }
    
    // =========================================================================
    // HELPERS
    // =========================================================================
    
    private boolean isProductionProfile() {
        return activeProfile != null && 
               (activeProfile.contains("prod") || activeProfile.contains("production"));
    }
    
    // =========================================================================
    // DATA RECORDS
    // =========================================================================
    
    public record InjectionResult(
        boolean shouldProceed,
        String failureReason,
        FailureType failureType
    ) {
        public static InjectionResult proceed() {
            return new InjectionResult(true, null, null);
        }
        
        public static InjectionResult paused() {
            return new InjectionResult(false, "Execution paused", FailureType.PAUSED);
        }
        
        public static InjectionResult proxyBanned(UUID proxyId) {
            return new InjectionResult(false, 
                "Proxy banned: " + proxyId, FailureType.PROXY_BANNED);
        }
        
        public static InjectionResult randomFailure() {
            return new InjectionResult(false, 
                "Injected random failure", FailureType.RANDOM_FAILURE);
        }
        
        public static InjectionResult timeout() {
            return new InjectionResult(false, 
                "Injected network timeout", FailureType.TIMEOUT);
        }
    }
    
    public enum FailureType {
        PAUSED,
        PROXY_BANNED,
        RANDOM_FAILURE,
        TIMEOUT
    }
    
    public record InjectionStatus(
        boolean enabled,
        int failurePercentage,
        int timeoutPercentage,
        long latencyMs,
        boolean paused,
        int permanentBans,
        int temporaryBans,
        InjectionMetrics metrics
    ) {}
    
    public record InjectionMetrics(
        long injectedFailures,
        long injectedTimeouts,
        long injectedLatency,
        long proxyBanHits,
        long pauseWaits
    ) {}
}
