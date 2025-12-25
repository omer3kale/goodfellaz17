package com.goodfellaz17.infrastructure.proxy;

import com.goodfellaz17.domain.model.OrderContext;
import com.goodfellaz17.domain.model.ProxyLease;
import com.goodfellaz17.domain.model.RoutingProfile;
import com.goodfellaz17.domain.model.ServicePriority;

import java.util.List;
import java.util.Map;

/**
 * Tor proxy source for high-volume global traffic.
 * 
 * Assumes there is a Tor SOCKS proxy running externally (not managed by Java).
 * Tor itself handles exit rotation; we just point at the SOCKS endpoint.
 * 
 * Configuration-driven: operator sets host/port and capacity based on their Tor setup.
 */
public class TorProxySource extends AbstractProxySource {
    
    private final String socksHost;
    private final int socksPort;
    
    public TorProxySource(
        boolean enabled,
        int capacityPerDay,
        double costPer1k,
        List<String> supportedGeos,
        String socksHost,
        int socksPort
    ) {
        super(
            "tor",
            "Tor Network",
            enabled,
            capacityPerDay,
            costPer1k,
            0.6,   // Medium-high risk - can be flagged by sophisticated detection
            false, // Not premium - high volume, not guaranteed quality
            supportedGeos,
            120    // 2 minute lease TTL (Tor circuits rotate)
        );
        this.socksHost = socksHost != null ? socksHost : "127.0.0.1";
        this.socksPort = socksPort > 0 ? socksPort : 9050;
    }
    
    @Override
    public boolean supportsProfile(RoutingProfile profile) {
        // Tor should NOT be used for elite/premium services
        if (profile.priority() == ServicePriority.ELITE || 
            profile.priority() == ServicePriority.PREMIUM ||
            profile.priority() == ServicePriority.MOBILE_EMULATION) {
            return false;
        }
        // Tor is good for high-volume and basic
        return profile.allowsRisk(riskLevel);
    }
    
    @Override
    protected ProxyLease createLease(OrderContext ctx) {
        return ProxyLease.create(
            name,
            socksHost,
            socksPort,
            ProxyLease.ProxyType.SOCKS5,
            "GLOBAL", // Tor doesn't guarantee specific geo
            riskLevel,
            leaseTtlSeconds,
            Map.of(
                "source", "tor",
                "circuit", "auto-rotate",
                "order_id", ctx.orderId() != null ? ctx.orderId() : "",
                "service", ctx.serviceName() != null ? ctx.serviceName() : ""
            )
        );
    }
    
    /**
     * Get Tor SOCKS endpoint info for monitoring.
     */
    public String getSocksEndpoint() {
        return socksHost + ":" + socksPort;
    }
}
