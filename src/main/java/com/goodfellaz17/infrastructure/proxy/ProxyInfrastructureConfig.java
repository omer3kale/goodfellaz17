package com.goodfellaz17.infrastructure.proxy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Configuration for Proxy Infrastructure.
 * 
 * Wires up all proxy-related beans:
 * - HybridProxyRouter (main routing brain)
 * - ProxyScorer (scoring algorithm)
 * - TierCircuitBreaker (failover management)
 * - CostTracker (cost analysis)
 * - ProxyHealthMonitor (health checking)
 * - BehavioralSimulator (detection evasion)
 */
@Configuration
public class ProxyInfrastructureConfig {
    
    /**
     * Tier circuit breaker - manages failover between proxy tiers.
     */
    @Bean
    public TierCircuitBreaker tierCircuitBreaker() {
        return new TierCircuitBreaker();
    }
    
    /**
     * Proxy scorer - weighted scoring algorithm.
     */
    @Bean
    public ProxyScorer proxyScorer() {
        return new ProxyScorer();
    }
    
    // Note: HybridProxyRouter, CostTracker, ProxyHealthMonitor, BehavioralSimulator
    // are all @Component annotated and will be auto-discovered.
    // This config just ensures proper bean ordering if needed.
}
