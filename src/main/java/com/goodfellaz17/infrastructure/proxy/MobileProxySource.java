package com.goodfellaz17.infrastructure.proxy;

import com.goodfellaz17.domain.model.OrderContext;
import com.goodfellaz17.domain.model.ProxyLease;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mobile/Phone Farm proxy source for elite mobile-like traffic.
 * 
 * DESIGNED TO WORK WITHOUT PHONES TODAY:
 * - When enabled=false or capacity=0, always reports "no capacity"
 * - The hybrid strategy will gracefully fall back to other sources
 * - On Friday when 107 phones are ready, just update config
 * 
 * Expects a mobile proxy gateway (external to Java) that:
 * - Manages device connections (ADB, USB)
 * - Exposes a single HTTP/SOCKS endpoint for the backend
 */
public class MobileProxySource extends AbstractProxySource {
    
    private final List<GatewayEndpoint> gatewayEndpoints;
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    
    public record GatewayEndpoint(String host, int port) {}
    
    public MobileProxySource(
        boolean enabled,
        int capacityPerDay,
        double costPer1k,
        List<String> supportedGeos,
        List<GatewayEndpoint> gatewayEndpoints
    ) {
        super(
            "mobile",
            "Mobile Farm",
            enabled,
            capacityPerDay,
            costPer1k,
            0.05,  // Very low risk - most human-like traffic
            true,  // Premium source - highest quality
            supportedGeos,
            600    // 10 minute lease TTL
        );
        this.gatewayEndpoints = gatewayEndpoints != null ? List.copyOf(gatewayEndpoints) : List.of();
    }
    
    @Override
    public boolean hasCapacity() {
        // No capacity if disabled, zero capacity configured, or no endpoints
        if (!enabled || capacityPerDay == 0 || gatewayEndpoints.isEmpty()) {
            return false;
        }
        return super.hasCapacity();
    }
    
    @Override
    protected ProxyLease createLease(OrderContext ctx) {
        if (gatewayEndpoints.isEmpty()) {
            // This shouldn't happen if hasCapacity() is checked, but safety first
            throw new IllegalStateException("No mobile gateway endpoints configured");
        }
        
        // Round-robin gateway selection
        int index = Math.abs(roundRobinIndex.getAndIncrement() % gatewayEndpoints.size());
        GatewayEndpoint gateway = gatewayEndpoints.get(index);
        
        return ProxyLease.create(
            name,
            gateway.host(),
            gateway.port(),
            ProxyLease.ProxyType.SOCKS5,
            ctx.targetCountry(),
            riskLevel,
            leaseTtlSeconds,
            Map.of(
                "source", "mobile",
                "gateway_index", String.valueOf(index),
                "mobile_like", "true",
                "order_id", ctx.orderId() != null ? ctx.orderId() : "",
                "service", ctx.serviceName() != null ? ctx.serviceName() : ""
            )
        );
    }
    
    /**
     * Get gateway count for monitoring.
     */
    public int getGatewayCount() {
        return gatewayEndpoints.size();
    }
    
    /**
     * Check if mobile source is ready (has gateways configured).
     */
    public boolean isReady() {
        return enabled && capacityPerDay > 0 && !gatewayEndpoints.isEmpty();
    }
}
