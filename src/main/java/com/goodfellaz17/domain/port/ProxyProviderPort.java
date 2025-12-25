package com.goodfellaz17.domain.port;

import com.goodfellaz17.domain.model.GeoTarget;
import com.goodfellaz17.domain.model.Proxy;

import java.util.List;

/**
 * Port interface for external proxy providers.
 * Implementations connect to real residential proxy services like BrightData, SOAX, etc.
 * 
 * NO MOCKS IN PROD: Implementations must fetch real proxies or fail loudly.
 */
public interface ProxyProviderPort {
    
    /**
     * Provider name for logging/monitoring.
     */
    String getProviderName();
    
    /**
     * Fetch proxies for a specific geo target.
     * @param geo Target geography (USA, EU, WORLDWIDE)
     * @return List of real proxies from the provider
     * @throws ProxyProviderException if provider API fails
     */
    List<Proxy> fetchProxies(GeoTarget geo);
    
    /**
     * Refresh a specific proxy that failed health check.
     * @param proxyId The proxy to refresh
     * @return New proxy to replace the unhealthy one
     */
    Proxy refreshProxy(String proxyId);
    
    /**
     * Check if provider is configured and accessible.
     */
    boolean isAvailable();
    
    /**
     * Get remaining quota/capacity from provider.
     */
    ProviderQuota getQuota();
    
    /**
     * Provider quota information.
     */
    record ProviderQuota(
        long remainingRequests,
        long remainingBandwidthMb,
        long resetTimestamp
    ) {
        public static ProviderQuota unlimited() {
            return new ProviderQuota(Long.MAX_VALUE, Long.MAX_VALUE, 0);
        }
    }
    
    /**
     * Exception for provider failures.
     */
    class ProxyProviderException extends RuntimeException {
        public ProxyProviderException(String message) {
            super(message);
        }
        public ProxyProviderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
