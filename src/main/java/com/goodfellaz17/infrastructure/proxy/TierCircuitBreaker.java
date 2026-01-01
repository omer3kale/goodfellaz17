package com.goodfellaz17.infrastructure.proxy;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Circuit breaker for proxy pool tiers.
 * 
 * Implements the circuit breaker pattern at the tier level:
 * - CLOSED: Normal operation, requests pass through
 * - OPEN: Tier failing, all requests rejected
 * - HALF_OPEN: Testing if tier recovered
 * 
 * Triggers fallback to next tier when circuit opens.
 */
public class TierCircuitBreaker {
    
    public enum CircuitState {
        CLOSED,    // Normal operation
        OPEN,      // Failing, reject requests
        HALF_OPEN  // Testing recovery
    }
    
    /**
     * Circuit configuration per tier.
     */
    public record CircuitConfig(
        int failureThreshold,      // Failures to open circuit
        int successThreshold,      // Successes to close circuit (in half-open)
        Duration openDuration,     // Time to stay open before half-open
        Duration windowDuration    // Rolling window for failure counting
    ) {
        public static CircuitConfig defaultConfig() {
            return new CircuitConfig(10, 3, Duration.ofMinutes(5), Duration.ofMinutes(1));
        }
        
        public static CircuitConfig forTier(ProxyTier tier) {
            return switch (tier) {
                // Premium tiers: more tolerant (expensive to lose)
                case MOBILE -> new CircuitConfig(15, 2, Duration.ofMinutes(3), Duration.ofMinutes(2));
                case RESIDENTIAL -> new CircuitConfig(12, 2, Duration.ofMinutes(4), Duration.ofMinutes(2));
                
                // Mid tiers: standard
                case ISP -> new CircuitConfig(10, 3, Duration.ofMinutes(5), Duration.ofMinutes(1));
                case USER_ARBITRAGE -> new CircuitConfig(10, 3, Duration.ofMinutes(5), Duration.ofMinutes(1));
                
                // Low tiers: quick to fail (cheap, replaceable)
                case DATACENTER -> new CircuitConfig(8, 3, Duration.ofMinutes(3), Duration.ofSeconds(30));
                case TOR -> new CircuitConfig(5, 5, Duration.ofMinutes(10), Duration.ofSeconds(30));
            };
        }
    }
    
    /**
     * State for a single tier's circuit.
     */
    private static class TierCircuit {
        final ProxyTier tier;
        final CircuitConfig config;
        volatile CircuitState state = CircuitState.CLOSED;
        final AtomicInteger failureCount = new AtomicInteger(0);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger halfOpenAttempts = new AtomicInteger(0);
        volatile Instant lastFailure = null;
        volatile Instant lastSuccess = null;
        volatile Instant stateChangedAt = Instant.now();
        final Deque<Instant> recentFailures = new LinkedList<>();
        
        TierCircuit(ProxyTier tier) {
            this.tier = tier;
            this.config = CircuitConfig.forTier(tier);
        }
        
        synchronized void recordSuccess() {
            lastSuccess = Instant.now();
            failureCount.set(0);
            
            if (state == CircuitState.HALF_OPEN) {
                int successes = successCount.incrementAndGet();
                if (successes >= config.successThreshold()) {
                    transitionTo(CircuitState.CLOSED);
                }
            }
        }
        
        synchronized void recordFailure() {
            Instant now = Instant.now();
            lastFailure = now;
            
            // Add to rolling window
            recentFailures.addLast(now);
            
            // Remove failures outside window
            Instant windowStart = now.minus(config.windowDuration());
            while (!recentFailures.isEmpty() && recentFailures.peekFirst().isBefore(windowStart)) {
                recentFailures.pollFirst();
            }
            
            if (state == CircuitState.CLOSED) {
                if (recentFailures.size() >= config.failureThreshold()) {
                    transitionTo(CircuitState.OPEN);
                }
            } else if (state == CircuitState.HALF_OPEN) {
                // Any failure in half-open reopens circuit
                transitionTo(CircuitState.OPEN);
            }
        }
        
        void transitionTo(CircuitState newState) {
            state = newState;
            stateChangedAt = Instant.now();
            successCount.set(0);
            halfOpenAttempts.set(0);
            if (newState == CircuitState.CLOSED) {
                recentFailures.clear();
            }
        }
        
        boolean shouldAllowRequest() {
            switch (state) {
                case CLOSED:
                    return true;
                    
                case OPEN:
                    // Check if should transition to half-open
                    if (Duration.between(stateChangedAt, Instant.now())
                            .compareTo(config.openDuration()) >= 0) {
                        transitionTo(CircuitState.HALF_OPEN);
                        return true;
                    }
                    return false;
                    
                case HALF_OPEN:
                    // Allow limited requests
                    return halfOpenAttempts.incrementAndGet() <= config.successThreshold() + 2;
                    
                default:
                    return false;
            }
        }
    }
    
    // Circuit state per tier
    private final Map<ProxyTier, TierCircuit> circuits = new ConcurrentHashMap<>();
    
    // Fallback chain: if tier fails, try next in chain
    private final Map<ProxyTier, List<ProxyTier>> fallbackChains;
    
    public TierCircuitBreaker() {
        // Initialize circuits for all tiers
        for (ProxyTier tier : ProxyTier.values()) {
            circuits.put(tier, new TierCircuit(tier));
        }
        
        // Define fallback chains
        fallbackChains = Map.of(
            ProxyTier.MOBILE, List.of(ProxyTier.RESIDENTIAL, ProxyTier.ISP, ProxyTier.USER_ARBITRAGE),
            ProxyTier.RESIDENTIAL, List.of(ProxyTier.ISP, ProxyTier.USER_ARBITRAGE, ProxyTier.DATACENTER),
            ProxyTier.ISP, List.of(ProxyTier.RESIDENTIAL, ProxyTier.USER_ARBITRAGE, ProxyTier.DATACENTER),
            ProxyTier.DATACENTER, List.of(ProxyTier.TOR, ProxyTier.ISP),
            ProxyTier.TOR, List.of(ProxyTier.DATACENTER, ProxyTier.USER_ARBITRAGE),
            ProxyTier.USER_ARBITRAGE, List.of(ProxyTier.RESIDENTIAL, ProxyTier.ISP, ProxyTier.DATACENTER)
        );
    }
    
    /**
     * Check if requests should be allowed for this tier.
     */
    public boolean isAllowed(ProxyTier tier) {
        TierCircuit circuit = circuits.get(tier);
        return circuit != null && circuit.shouldAllowRequest();
    }
    
    /**
     * Record successful request for tier.
     */
    public void recordSuccess(ProxyTier tier) {
        TierCircuit circuit = circuits.get(tier);
        if (circuit != null) {
            circuit.recordSuccess();
        }
    }
    
    /**
     * Record failed request for tier.
     */
    public void recordFailure(ProxyTier tier) {
        TierCircuit circuit = circuits.get(tier);
        if (circuit != null) {
            circuit.recordFailure();
        }
    }
    
    /**
     * Get current state of a tier's circuit.
     */
    public CircuitState getState(ProxyTier tier) {
        TierCircuit circuit = circuits.get(tier);
        return circuit != null ? circuit.state : CircuitState.OPEN;
    }
    
    /**
     * Get fallback tiers if primary tier is unavailable.
     */
    public List<ProxyTier> getFallbacks(ProxyTier primary) {
        return fallbackChains.getOrDefault(primary, List.of());
    }
    
    /**
     * Get best available tier from preference list.
     * 
     * @param preferred Preferred tier (may be unavailable)
     * @param minimum Minimum acceptable tier quality
     * @return Best available tier, or empty if all circuits open
     */
    public Optional<ProxyTier> getBestAvailableTier(ProxyTier preferred, ProxyTier minimum) {
        // Try preferred first
        if (isAllowed(preferred) && preferred.meetsRequirement(minimum)) {
            return Optional.of(preferred);
        }
        
        // Try fallbacks in order
        for (ProxyTier fallback : getFallbacks(preferred)) {
            if (isAllowed(fallback) && fallback.meetsRequirement(minimum)) {
                return Optional.of(fallback);
            }
        }
        
        // Last resort: any available tier meeting minimum
        return Arrays.stream(ProxyTier.values())
            .filter(t -> isAllowed(t) && t.meetsRequirement(minimum))
            .max(Comparator.comparingDouble(ProxyTier::getQualityScore));
    }
    
    /**
     * Force reset a tier's circuit (manual intervention).
     */
    public void reset(ProxyTier tier) {
        TierCircuit circuit = circuits.get(tier);
        if (circuit != null) {
            circuit.transitionTo(CircuitState.CLOSED);
        }
    }
    
    /**
     * Get status summary of all circuits.
     */
    public Map<ProxyTier, CircuitStatus> getAllStatus() {
        Map<ProxyTier, CircuitStatus> status = new EnumMap<>(ProxyTier.class);
        for (var entry : circuits.entrySet()) {
            TierCircuit circuit = entry.getValue();
            status.put(entry.getKey(), new CircuitStatus(
                circuit.state,
                circuit.recentFailures.size(),
                circuit.lastSuccess,
                circuit.lastFailure,
                circuit.stateChangedAt
            ));
        }
        return status;
    }
    
    public record CircuitStatus(
        CircuitState state,
        int recentFailures,
        Instant lastSuccess,
        Instant lastFailure,
        Instant stateChangedAt
    ) {}
}
