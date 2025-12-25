package com.goodfellaz17.infrastructure.proxy;

import com.goodfellaz17.domain.model.OrderContext;
import com.goodfellaz17.domain.model.ProxyLease;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * P2P/Residential proxy source for mid-tier natural traffic.
 * 
 * Points at a commercial or P2P residential proxy provider.
 * Configuration-driven: operator sets gateway endpoint and capacity based on their plan.
 */
public class P2pProxySource extends AbstractProxySource {
    
    private final List<GatewayEndpoint> endpoints;
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    
    public record GatewayEndpoint(String host, int port, String username, String password) {}
    
    public P2pProxySource(
        boolean enabled,
        int capacityPerDay,
        double costPer1k,
        List<String> supportedGeos,
        List<GatewayEndpoint> endpoints
    ) {
        super(
            "p2p",
            "Residential P2P",
            enabled,
            capacityPerDay,
            costPer1k,
            0.3,   // Medium risk - residential but shared
            false, // Not premium - mid-tier
            supportedGeos,
            180    // 3 minute lease TTL
        );
        this.endpoints = endpoints != null ? List.copyOf(endpoints) : List.of();
    }
    
    @Override
    public boolean hasCapacity() {
        return super.hasCapacity() && !endpoints.isEmpty();
    }
    
    @Override
    protected ProxyLease createLease(OrderContext ctx) {
        // Round-robin endpoint selection
        int index = Math.abs(roundRobinIndex.getAndIncrement() % endpoints.size());
        GatewayEndpoint endpoint = endpoints.get(index);
        
        // For residential providers that support geo-targeting, 
        // the country might be passed as part of the auth or endpoint
        String targetGeo = ctx.targetCountry() != null ? ctx.targetCountry() : "GLOBAL";
        
        return ProxyLease.create(
            name,
            endpoint.host(),
            endpoint.port(),
            ProxyLease.ProxyType.HTTP, // Most residential providers use HTTP
            targetGeo,
            riskLevel,
            leaseTtlSeconds,
            Map.of(
                "source", "p2p",
                "endpoint_index", String.valueOf(index),
                "has_auth", String.valueOf(endpoint.username() != null),
                "order_id", ctx.orderId() != null ? ctx.orderId() : "",
                "service", ctx.serviceName() != null ? ctx.serviceName() : ""
            )
        );
    }
    
    /**
     * Get endpoint count for monitoring.
     */
    public int getEndpointCount() {
        return endpoints.size();
    }
}
