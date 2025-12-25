package com.goodfellaz17.domain.model;

import java.time.Instant;

/**
 * Domain Value Object - Residential proxy configuration.
 * 
 * Supports BrightData, SOAX, and other residential proxy providers.
 * Health status tracked for automatic pool management.
 */
public class Proxy {
    
    private final String id;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String country;
    private final ProxyType type;
    private final String provider;
    
    // Health tracking
    private volatile boolean healthy = true;
    private volatile int failureCount = 0;
    private volatile Instant lastHealthCheck = Instant.now();
    private volatile Instant lastUsed = null;
    
    public enum ProxyType {
        HTTP, HTTPS, SOCKS4, SOCKS5
    }
    
    private Proxy(Builder builder) {
        this.id = builder.id;
        this.host = builder.host;
        this.port = builder.port;
        this.username = builder.username;
        this.password = builder.password;
        this.country = builder.country;
        this.type = builder.type;
        this.provider = builder.provider;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public String getId() { return id; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getCountry() { return country; }
    public ProxyType getType() { return type; }
    public String getProvider() { return provider; }
    public boolean isHealthy() { return healthy; }
    public int getFailureCount() { return failureCount; }
    public Instant getLastHealthCheck() { return lastHealthCheck; }
    public Instant getLastUsed() { return lastUsed; }
    
    // Health management
    public void markHealthy() {
        this.healthy = true;
        this.failureCount = 0;
        this.lastHealthCheck = Instant.now();
    }
    
    public void markUnhealthy() {
        this.failureCount++;
        if (this.failureCount >= 3) {
            this.healthy = false;
        }
        this.lastHealthCheck = Instant.now();
    }
    
    public void markUsed() {
        this.lastUsed = Instant.now();
    }
    
    public String format() {
        if (username != null && !username.isEmpty()) {
            return String.format("%s:%s@%s:%d", username, password, host, port);
        }
        return String.format("%s:%d", host, port);
    }
    
    public String toSeleniumFormat() {
        String scheme = (type == ProxyType.HTTP || type == ProxyType.HTTPS) ? "http" : "socks5";
        if (username != null && !username.isEmpty()) {
            return String.format("%s://%s:%s@%s:%d", scheme, username, password, host, port);
        }
        return String.format("%s://%s:%d", scheme, host, port);
    }
    
    /**
     * Convert to GeoTarget for compatibility with existing code.
     */
    public GeoTarget toGeoTarget() {
        if (country == null) return GeoTarget.WORLDWIDE;
        return switch (country.toUpperCase()) {
            case "US", "USA" -> GeoTarget.USA;
            case "GB", "UK", "DE", "FR", "NL", "IT", "ES" -> GeoTarget.EU;
            default -> GeoTarget.WORLDWIDE;
        };
    }
    
    public static class Builder {
        private String id;
        private String host;
        private int port;
        private String username;
        private String password;
        private String country;
        private ProxyType type = ProxyType.HTTP;
        private String provider;
        
        public Builder id(String id) { this.id = id; return this; }
        public Builder host(String host) { this.host = host; return this; }
        public Builder port(int port) { this.port = port; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder password(String password) { this.password = password; return this; }
        public Builder country(String country) { this.country = country; return this; }
        public Builder type(ProxyType type) { this.type = type; return this; }
        public Builder provider(String provider) { this.provider = provider; return this; }
        
        public Proxy build() {
            return new Proxy(this);
        }
    }
}
