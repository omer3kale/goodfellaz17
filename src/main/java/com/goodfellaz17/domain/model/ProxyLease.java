package com.goodfellaz17.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an acquired proxy connection lease.
 * Value object holding proxy details and metadata for a single routing decision.
 */
public record ProxyLease(
    String leaseId,
    String sourceName,
    String host,
    int port,
    ProxyType type,
    String country,
    double riskLevel,
    Instant acquiredAt,
    Instant expiresAt,
    Map<String, String> metadata
) {
    
    public enum ProxyType {
        HTTP, HTTPS, SOCKS4, SOCKS5
    }
    
    /**
     * Create a new lease with generated ID and timestamps.
     */
    public static ProxyLease create(
        String sourceName,
        String host,
        int port,
        ProxyType type,
        String country,
        double riskLevel,
        long ttlSeconds,
        Map<String, String> metadata
    ) {
        Instant now = Instant.now();
        return new ProxyLease(
            UUID.randomUUID().toString(),
            sourceName,
            host,
            port,
            type,
            country,
            riskLevel,
            now,
            now.plusSeconds(ttlSeconds),
            metadata != null ? Map.copyOf(metadata) : Map.of()
        );
    }
    
    /**
     * Check if this lease is still valid.
     */
    public boolean isValid() {
        return Instant.now().isBefore(expiresAt);
    }
    
    /**
     * Get connection URL based on proxy type.
     */
    public String getConnectionUrl() {
        String scheme = switch (type) {
            case HTTP -> "http";
            case HTTPS -> "https";
            case SOCKS4 -> "socks4";
            case SOCKS5 -> "socks5";
        };
        return scheme + "://" + host + ":" + port;
    }
    
    /**
     * Human-readable route description for logging/UI.
     */
    public String getRouteDescription() {
        return String.format("Route: %s (%s)", sourceName, country != null ? country : "GLOBAL");
    }
}
