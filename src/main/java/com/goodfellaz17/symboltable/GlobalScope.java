package com.goodfellaz17.symboltable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MontiCore-inspired GlobalScope.
 * 
 * Manual Ch.9.3.1: GlobalScope is the top-level scope containing all symbols.
 * In GOODFELLAZ17: Contains all services, manages artifact scopes per API key.
 * 
 * Features:
 * - O(1) service lookup via ConcurrentHashMap
 * - API key scope management (tenant isolation)
 * - Thread-safe for concurrent access
 */
public class GlobalScope extends Scope {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalScope.class);
    
    private final Map<String, ArtifactScope> artifactScopes;
    
    public GlobalScope() {
        super("global");
        this.artifactScopes = new ConcurrentHashMap<>();
        log.info("GlobalScope initialized");
    }
    
    /**
     * Add a service to the global scope.
     */
    public void addService(ServiceSymbol service) {
        add(service);
        log.debug("Added service: {}", service);
    }
    
    /**
     * Resolve a service by its public ID.
     * 
     * @param serviceId Perfect Panel service ID
     * @return ServiceSymbol or throws if not found
     */
    public ServiceSymbol resolveService(int serviceId) {
        return this.<ServiceSymbol>resolveLocally(String.valueOf(serviceId), SymbolKind.SERVICE)
                   .orElseThrow(() -> new ServiceNotFoundException(serviceId));
    }
    
    /**
     * Get all available services.
     */
    public List<ServiceSymbol> getAllServices() {
        return resolveManyLocally(SymbolKind.SERVICE);
    }
    
    /**
     * Get or create an artifact scope for an API key.
     * Manual Ch.9.3.3: Each API key gets isolated scope.
     */
    public ArtifactScope getOrCreateArtifactScope(String apiKey) {
        return artifactScopes.computeIfAbsent(apiKey, key -> {
            ArtifactScope scope = new ArtifactScope(key, this);
            addSubScope(scope);
            log.info("Created ArtifactScope for API key: {}...", key.substring(0, 8));
            return scope;
        });
    }
    
    /**
     * Get an existing artifact scope.
     */
    public Optional<ArtifactScope> getArtifactScope(String apiKey) {
        return Optional.ofNullable(artifactScopes.get(apiKey));
    }
    
    /**
     * Remove an artifact scope (API key deactivation).
     */
    public void removeArtifactScope(String apiKey) {
        ArtifactScope removed = artifactScopes.remove(apiKey);
        if (removed != null) {
            log.info("Removed ArtifactScope for API key: {}...", apiKey.substring(0, 8));
        }
    }
    
    /**
     * Get all artifact scopes.
     */
    public Collection<ArtifactScope> getAllArtifactScopes() {
        return Collections.unmodifiableCollection(artifactScopes.values());
    }
    
    /**
     * Get number of active tenants.
     */
    public int getTenantCount() {
        return artifactScopes.size();
    }
    
    /**
     * Initialize default services.
     * Called at startup to populate the service catalog.
     */
    public void initializeDefaultServices() {
        // Spotify Services
        addService(new ServiceSymbol(1, "Spotify Plays Worldwide", 
                new BigDecimal("0.50"), 100, 10_000_000, "spotify"));
        addService(new ServiceSymbol(2, "Spotify Plays USA", 
                new BigDecimal("0.90"), 100, 5_000_000, "spotify"));
        addService(new ServiceSymbol(3, "Spotify Monthly Listeners USA", 
                new BigDecimal("1.90"), 500, 50_000, "spotify"));
        addService(new ServiceSymbol(4, "Spotify Monthly Listeners Global", 
                new BigDecimal("1.50"), 500, 100_000, "spotify"));
        addService(new ServiceSymbol(5, "Spotify Followers", 
                new BigDecimal("2.00"), 100, 100_000, "spotify"));
        addService(new ServiceSymbol(6, "Spotify Saves", 
                new BigDecimal("1.00"), 100, 500_000, "spotify"));
        addService(new ServiceSymbol(7, "Spotify Playlist Followers", 
                new BigDecimal("1.50"), 100, 50_000, "spotify"));
        
        // Premium Drip Services
        addService(new ServiceSymbol(10, "Spotify Plays Drip Feed", 
                new BigDecimal("0.60"), 1000, 10_000_000, "spotify_drip"));
        addService(new ServiceSymbol(11, "Spotify Monthly Drip USA", 
                new BigDecimal("2.50"), 1000, 50_000, "spotify_drip"));
        
        log.info("Initialized {} default services", getAllServices().size());
    }
    
    @Override
    public String toString() {
        return String.format("GlobalScope[services=%d, tenants=%d]",
                resolveManyLocally(SymbolKind.SERVICE).size(), artifactScopes.size());
    }
    
    /**
     * Exception thrown when a service is not found.
     */
    public static class ServiceNotFoundException extends RuntimeException {
        private final int serviceId;
        
        public ServiceNotFoundException(int serviceId) {
            super("Service not found: " + serviceId);
            this.serviceId = serviceId;
        }
        
        public int getServiceId() {
            return serviceId;
        }
    }
}
