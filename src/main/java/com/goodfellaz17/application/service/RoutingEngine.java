package com.goodfellaz17.application.service;

import com.goodfellaz17.domain.exception.NoCapacityException;
import com.goodfellaz17.domain.model.*;
import com.goodfellaz17.domain.port.ProxyStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routing engine that ties service definitions to proxy selection.
 * 
 * This is the main entry point for order routing:
 * 1. Determine RoutingProfile from service ID
 * 2. Build OrderContext with priority, geo, quantity
 * 3. Call ProxyStrategy.selectProxy() to get a lease
 * 4. Return lease with routing metadata for fulfillment
 * 
 * The frontend never sees this - all routing is backend-only.
 */
@Service
public class RoutingEngine {
    
    private static final Logger log = LoggerFactory.getLogger(RoutingEngine.class);
    
    private final ProxyStrategy proxyStrategy;
    private final Map<String, RoutingProfile> serviceProfiles;
    
    public RoutingEngine(ProxyStrategy proxyStrategy) {
        this.proxyStrategy = proxyStrategy;
        this.serviceProfiles = new ConcurrentHashMap<>();
        initializeServiceProfiles();
    }
    
    /**
     * Initialize routing profiles for all known services.
     * In production, this could load from database or config.
     */
    private void initializeServiceProfiles() {
        // === PLAYS ===
        // Worldwide plays - basic, not geo sensitive
        registerProfile("plays_ww", RoutingProfile.DEFAULT);
        registerProfile("plays_worldwide", RoutingProfile.DEFAULT);
        
        // USA plays - premium, geo sensitive
        registerProfile("plays_usa", RoutingProfile.premium("US"));
        registerProfile("plays_us", RoutingProfile.premium("US"));
        
        // UK plays - premium, geo sensitive
        registerProfile("plays_uk", RoutingProfile.premium("UK"));
        
        // Chart plays - elite, mobile preferred
        registerProfile("plays_chart", RoutingProfile.elite());
        registerProfile("plays_elite", RoutingProfile.elite());
        
        // High-volume drip plays
        registerProfile("plays_drip", RoutingProfile.highVolume());
        registerProfile("plays_bulk", RoutingProfile.highVolume());
        
        // === LISTENERS ===
        registerProfile("listeners_ww", RoutingProfile.DEFAULT);
        registerProfile("listeners_usa", RoutingProfile.premium("US"));
        registerProfile("listeners_uk", RoutingProfile.premium("UK"));
        
        // === FOLLOWERS ===
        registerProfile("followers", RoutingProfile.DEFAULT);
        registerProfile("followers_premium", RoutingProfile.premium());
        
        // === SAVES ===
        registerProfile("saves", RoutingProfile.DEFAULT);
        registerProfile("saves_premium", RoutingProfile.premium());
        
        // === PLAYLIST ===
        registerProfile("playlist_plays", RoutingProfile.DEFAULT);
        registerProfile("playlist_followers", RoutingProfile.DEFAULT);
        
        // === MOBILE EMULATION ===
        registerProfile("mobile_plays", RoutingProfile.mobileEmulation());
        registerProfile("mobile_streams", RoutingProfile.mobileEmulation());
        
        log.info("Initialized {} service routing profiles", serviceProfiles.size());
    }
    
    /**
     * Register a routing profile for a service ID.
     */
    public void registerProfile(String serviceId, RoutingProfile profile) {
        serviceProfiles.put(serviceId.toLowerCase(), profile);
    }
    
    /**
     * Get routing profile for a service (defaults to BASIC if unknown).
     */
    public RoutingProfile getProfile(String serviceId) {
        return serviceProfiles.getOrDefault(
            serviceId != null ? serviceId.toLowerCase() : "",
            RoutingProfile.DEFAULT
        );
    }
    
    /**
     * Route an order and acquire a proxy lease.
     * 
     * @param orderId Order identifier
     * @param serviceId Service type identifier
     * @param serviceName Human-readable service name (for logging)
     * @param targetCountry Target country code (or null for global)
     * @param quantity Requested quantity
     * @return Proxy lease for fulfillment
     * @throws NoCapacityException if no suitable source has capacity
     */
    public ProxyLease route(
        String orderId,
        String serviceId,
        String serviceName,
        String targetCountry,
        int quantity
    ) throws NoCapacityException {
        
        RoutingProfile profile = getProfile(serviceId);
        
        OrderContext ctx = OrderContext.builder()
            .orderId(orderId)
            .serviceId(serviceId)
            .serviceName(serviceName)
            .routingProfile(profile)
            .targetCountry(targetCountry)
            .quantity(quantity)
            .build();
        
        log.info("Routing order {} (service: {}, priority: {}, geo: {}, qty: {})",
            orderId, serviceName, profile.priority(), targetCountry, quantity);
        
        ProxyLease lease = proxyStrategy.selectProxy(ctx);
        
        log.info("Order {} routed via {} ({})",
            orderId, lease.sourceName(), lease.getRouteDescription());
        
        return lease;
    }
    
    /**
     * Route with full OrderContext (for advanced use cases).
     */
    public ProxyLease route(OrderContext ctx) throws NoCapacityException {
        return proxyStrategy.selectProxy(ctx);
    }
    
    /**
     * Get aggregate stats for monitoring.
     */
    public ProxyStrategy.AggregateStats getStats() {
        return proxyStrategy.getAggregateStats();
    }
    
    /**
     * Get human-readable route hint for a service (for optional UI display).
     * This is the only thing the frontend might see - a friendly label.
     */
    public String getRouteHint(String serviceId) {
        RoutingProfile profile = getProfile(serviceId);
        return switch (profile.priority()) {
            case ELITE -> "Elite Route";
            case PREMIUM -> "Premium Route";
            case HIGH_VOLUME -> "High Volume";
            case MOBILE_EMULATION -> "Mobile Route";
            case BASIC -> "Standard Route";
        };
    }
}
