package com.goodfellaz17.infrastructure.proxy;

import com.goodfellaz17.domain.model.OrderContext;
import com.goodfellaz17.domain.model.ProxyLease;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AWS/Cloud proxy source for premium geo-targeted traffic.
 * 
 * Configuration-driven: expects operator to configure EC2/VPS proxy endpoints.
 * Does NOT spawn instances - that's ops/DevOps concern, not backend logic.
 */
public class AwsProxySource extends AbstractProxySource {
    
    private final List<ProxyEndpoint> endpoints;
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    
    public record ProxyEndpoint(String host, int port, String auth) {}
    
    public AwsProxySource(
        boolean enabled,
        int capacityPerDay,
        double costPer1k,
        List<String> supportedGeos,
        List<ProxyEndpoint> endpoints
    ) {
        super(
            "aws",
            "AWS Cloud",
            enabled,
            capacityPerDay,
            costPer1k,
            0.1,  // Low risk - reliable cloud infrastructure
            true, // Premium source
            supportedGeos,
            300   // 5 minute lease TTL
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
        ProxyEndpoint endpoint = endpoints.get(index);
        
        return ProxyLease.create(
            name,
            endpoint.host(),
            endpoint.port(),
            ProxyLease.ProxyType.SOCKS5,
            ctx.targetCountry(),
            riskLevel,
            leaseTtlSeconds,
            Map.of(
                "source", "aws",
                "endpoint_index", String.valueOf(index),
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
