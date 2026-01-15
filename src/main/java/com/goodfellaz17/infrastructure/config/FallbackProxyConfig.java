package com.goodfellaz17.infrastructure.config;

import com.goodfellaz17.domain.model.OrderContext;
import com.goodfellaz17.domain.model.ProxyLease;
import com.goodfellaz17.domain.port.ProxySource;
import com.goodfellaz17.domain.port.ProxyStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Fallback proxy configuration for when hybrid proxy is not enabled.
 * 
 * This provides a stub implementation so the application can start
 * without full proxy infrastructure configured.
 */
@Configuration
public class FallbackProxyConfig {
    
    private static final Logger log = LoggerFactory.getLogger(FallbackProxyConfig.class);
    
    @Bean
    @ConditionalOnMissingBean(ProxyStrategy.class)
    public ProxyStrategy stubProxyStrategy() {
        log.warn("Using stub ProxyStrategy - proxy.hybrid.enabled is not set to true");
        return new StubProxyStrategy();
    }
    
    /**
     * Stub implementation that returns mock leases.
     * Orders will be created but proxy execution is not functional.
     */
    static class StubProxyStrategy implements ProxyStrategy {
        
        private static final Logger log = LoggerFactory.getLogger(StubProxyStrategy.class);
        
        @Override
        public ProxyLease selectProxy(OrderContext ctx) {
            log.info("StubProxyStrategy: Creating mock lease for order {}", ctx.orderId());
            return ProxyLease.create(
                    "STUB",
                    "127.0.0.1",
                    8888,
                    ProxyLease.ProxyType.HTTP,
                    "LOCAL",
                    0.0,
                    300, // 5 min TTL
                    Map.of("mode", "stub")
            );
        }
        
        @Override
        public void release(ProxyLease lease) {
            log.debug("StubProxyStrategy: Releasing lease {}", lease.leaseId());
        }
        
        @Override
        public List<ProxySource> getSources() {
            return List.of();
        }
        
        @Override
        public List<ProxySource> getAvailableSources() {
            return List.of();
        }
        
        @Override
        public AggregateStats getAggregateStats() {
            return new AggregateStats(0, 0, 0, 0, 0);
        }
    }
}
