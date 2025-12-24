package com.goodfellaz17.domain.model;

/**
 * Domain Value Object - Residential proxy configuration.
 * 
 * BrightData/SOAX residential IPs for geo-targeting.
 */
public record Proxy(
        String host,
        int port,
        String username,
        String password,
        GeoTarget region,
        boolean residential
) {
    public String format() {
        if (username != null && !username.isEmpty()) {
            return String.format("%s:%s@%s:%d", username, password, host, port);
        }
        return String.format("%s:%d", host, port);
    }
    
    public String toSeleniumFormat() {
        return String.format("http://%s:%d", host, port);
    }
    
    public boolean isHealthy() {
        // TODO: Implement health check ping
        return true;
    }
}
