package com.goodfellaz17.symboltable;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * MontiCore-inspired Symbol Table - Spring-managed singleton.
 * 
 * Manual Ch.9: Symbol tables provide symbol resolution across scopes.
 * SmmSymbolTable is the main entry point for all symbol operations.
 * 
 * Features:
 * - O(1) service lookup (ConcurrentHashMap)
 * - Tenant isolation via ArtifactScopes
 * - Thread-safe for production use
 * 
 * Architecture (Manual Ch.9.3):
 * ```
 * GlobalScope
 * ├── ServiceSymbol (id=1, "Spotify Plays WW")
 * ├── ServiceSymbol (id=2, "Spotify Plays USA")
 * └── ArtifactScope (apiKey="abc123")
 *     ├── ApiKeySymbol (balance=$50.00)
 *     └── OrderSymbol (order #1)
 * ```
 */
@Component
public class SmmSymbolTable {
    
    private static final Logger log = LoggerFactory.getLogger(SmmSymbolTable.class);
    
    private final GlobalScope globalScope;
    
    public SmmSymbolTable() {
        this.globalScope = new GlobalScope();
    }
    
    /**
     * Initialize symbol table with default services.
     * Called after Spring context initialization.
     */
    @PostConstruct
    public void initialize() {
        globalScope.initializeDefaultServices();
        log.info("SmmSymbolTable initialized: {}", globalScope);
    }
    
    /**
     * Get the global scope.
     */
    public GlobalScope getGlobalScope() {
        return globalScope;
    }
    
    // ==================== Service Resolution ====================
    
    /**
     * Resolve a service by ID.
     * O(1) lookup via ConcurrentHashMap.
     */
    public ServiceSymbol resolveService(int serviceId) {
        return globalScope.resolveService(serviceId);
    }
    
    /**
     * Get all available services.
     */
    public List<ServiceSymbol> getAllServices() {
        return globalScope.getAllServices();
    }
    
    /**
     * Check if a service exists.
     */
    public boolean serviceExists(int serviceId) {
        return globalScope.containsLocally(String.valueOf(serviceId), SymbolKind.SERVICE);
    }
    
    // ==================== Tenant (API Key) Management ====================
    
    /**
     * Get or create a scope for an API key.
     * Manual Ch.9.3.3: ArtifactScope per tenant.
     */
    public ArtifactScope getTenantScope(String apiKey) {
        return globalScope.getOrCreateArtifactScope(apiKey);
    }
    
    /**
     * Get tenant scope if it exists.
     */
    public Optional<ArtifactScope> findTenantScope(String apiKey) {
        return globalScope.getArtifactScope(apiKey);
    }
    
    /**
     * Remove a tenant scope.
     */
    public void removeTenantScope(String apiKey) {
        globalScope.removeArtifactScope(apiKey);
    }
    
    /**
     * Get number of active tenants.
     */
    public int getTenantCount() {
        return globalScope.getTenantCount();
    }
    
    // ==================== Order Tracking ====================
    
    /**
     * Add an order to a tenant's scope.
     */
    public void trackOrder(String apiKey, OrderSymbol order) {
        ArtifactScope scope = getTenantScope(apiKey);
        scope.add(order);
        log.debug("Tracking order {} for tenant {}...", order.getOrderId(), apiKey.substring(0, 8));
    }
    
    /**
     * Resolve an order from a tenant's scope.
     */
    public Optional<OrderSymbol> resolveOrder(String apiKey, String orderId) {
        return findTenantScope(apiKey)
                .flatMap(scope -> scope.resolveLocally(orderId, SymbolKind.ORDER));
    }
    
    /**
     * Get all orders for a tenant.
     */
    public List<OrderSymbol> getTenantOrders(String apiKey) {
        return findTenantScope(apiKey)
                .map(scope -> scope.<OrderSymbol>resolveManyLocally(SymbolKind.ORDER))
                .orElse(List.of());
    }
    
    // ==================== Statistics ====================
    
    /**
     * Get symbol table statistics.
     */
    public Statistics getStatistics() {
        return new Statistics(
                globalScope.getAllServices().size(),
                globalScope.getTenantCount(),
                globalScope.getAllArtifactScopes().stream()
                           .mapToInt(Scope::size)
                           .sum()
        );
    }
    
    public record Statistics(int serviceCount, int tenantCount, int totalSymbols) {
        @Override
        public String toString() {
            return String.format("Statistics[services=%d, tenants=%d, total=%d]",
                    serviceCount, tenantCount, totalSymbols);
        }
    }
}
