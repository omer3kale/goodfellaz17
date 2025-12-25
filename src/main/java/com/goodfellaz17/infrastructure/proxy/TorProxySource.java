package com.goodfellaz17.infrastructure.proxy;

import com.goodfellaz17.domain.model.OrderContext;
import com.goodfellaz17.domain.model.ProxyLease;
import com.goodfellaz17.domain.model.RoutingProfile;
import com.goodfellaz17.domain.model.ServicePriority;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tor proxy source for high-volume global traffic.
 * 
 * MULTI-PORT ROTATION: Each port = different Tor circuit = different IP!
 * 
 * Docker setup:
 * docker run -d --name tor-proxy \
 *   -p 9050:9050 -p 9051:9051 -p 9052:9052 -p 9060:9060 \
 *   dperson/torproxy -country US,DE,GB,CA -enableDelPort
 * 
 * Configuration-driven: operator sets host/ports and capacity based on their Tor setup.
 */
public class TorProxySource extends AbstractProxySource {
    
    private final String socksHost;
    private final int[] socksPorts;
    private final AtomicInteger portIndex = new AtomicInteger(0);
    
    public TorProxySource(
        boolean enabled,
        int capacityPerDay,
        double costPer1k,
        List<String> supportedGeos,
        String socksHost,
        int[] socksPorts
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
        this.socksPorts = socksPorts != null && socksPorts.length > 0 
            ? socksPorts 
            : new int[]{9050, 9051, 9052, 9060};
    }
    
    // Backwards compatible constructor
    public TorProxySource(
        boolean enabled,
        int capacityPerDay,
        double costPer1k,
        List<String> supportedGeos,
        String socksHost,
        int socksPort
    ) {
        this(enabled, capacityPerDay, costPer1k, supportedGeos, socksHost, 
             new int[]{socksPort > 0 ? socksPort : 9050});
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
        // Rotate through ports - each port = different IP!
        int port = getNextPort();
        
        return ProxyLease.create(
            name,
            socksHost,
            port,
            ProxyLease.ProxyType.SOCKS5,
            "GLOBAL", // Tor doesn't guarantee specific geo
            riskLevel,
            leaseTtlSeconds,
            Map.of(
                "source", "tor",
                "circuit", "port-" + port,
                "rotation", "round-robin",
                "order_id", ctx.orderId() != null ? ctx.orderId() : "",
                "service", ctx.serviceName() != null ? ctx.serviceName() : ""
            )
        );
    }
    
    /**
     * Round-robin port rotation for IP diversity.
     */
    private int getNextPort() {
        int idx = portIndex.getAndIncrement() % socksPorts.length;
        return socksPorts[idx];
    }
    
    /**
     * Random port selection for unpredictable rotation.
     */
    public int getRandomPort() {
        return socksPorts[ThreadLocalRandom.current().nextInt(socksPorts.length)];
    }
    
    /**
     * Get Tor SOCKS endpoint info for monitoring.
     */
    public String getSocksEndpoint() {
        return socksHost + ":" + String.join(",", 
            java.util.Arrays.stream(socksPorts)
                .mapToObj(String::valueOf)
                .toArray(String[]::new));
    }
    
    /**
     * Get all configured ports.
     */
    public int[] getSocksPorts() {
        return socksPorts.clone();
    }
    
    /**
     * Get port count for capacity estimation.
     */
    public int getPortCount() {
        return socksPorts.length;
    }
}
