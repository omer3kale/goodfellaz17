package com.goodfellaz17.symboltable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * MontiCore-inspired Symbol Table Creator.
 * 
 * Manual Ch.9.6.2: SymbolTableCreator builds symbol table from AST.
 * In GOODFELLAZ17: Creates symbols from domain objects.
 * 
 * Usage:
 * ```java
 * SymbolTableCreator creator = new SymbolTableCreator(globalScope);
 * creator.createServicesFromConfig(serviceConfigs);
 * creator.createApiKey(apiKey, userId, balance);
 * ```
 */
public class SymbolTableCreator {
    
    private static final Logger log = LoggerFactory.getLogger(SymbolTableCreator.class);
    
    private final GlobalScope globalScope;
    
    public SymbolTableCreator(GlobalScope globalScope) {
        this.globalScope = globalScope;
    }
    
    /**
     * Create a service symbol and add to global scope.
     */
    public ServiceSymbol createService(int publicId, String name, BigDecimal rate,
                                        int minOrder, int maxOrder, String category) {
        ServiceSymbol symbol = new ServiceSymbol(publicId, name, rate, minOrder, maxOrder, category);
        globalScope.addService(symbol);
        log.debug("Created ServiceSymbol: {}", symbol);
        return symbol;
    }
    
    /**
     * Create multiple services from configuration.
     */
    public void createServicesFromConfig(List<ServiceConfig> configs) {
        for (ServiceConfig config : configs) {
            createService(
                    config.publicId(),
                    config.name(),
                    config.rate(),
                    config.minOrder(),
                    config.maxOrder(),
                    config.category()
            );
        }
        log.info("Created {} services from config", configs.size());
    }
    
    /**
     * Create an API key symbol in tenant scope.
     */
    public ApiKeySymbol createApiKey(String apiKey, UUID userId, BigDecimal balance) {
        ArtifactScope tenantScope = globalScope.getOrCreateArtifactScope(apiKey);
        
        ApiKeySymbol symbol = new ApiKeySymbol(apiKey, userId, balance);
        tenantScope.add(symbol);
        
        log.debug("Created ApiKeySymbol: {}", symbol);
        return symbol;
    }
    
    /**
     * Create an order symbol in tenant scope.
     */
    public OrderSymbol createOrder(String apiKey, UUID orderId, int serviceId, 
                                    String trackUrl, int quantity) {
        ArtifactScope tenantScope = globalScope.getOrCreateArtifactScope(apiKey);
        
        OrderSymbol symbol = new OrderSymbol(orderId, serviceId, trackUrl, quantity);
        tenantScope.add(symbol);
        
        log.debug("Created OrderSymbol: {}", symbol);
        return symbol;
    }
    
    /**
     * Service configuration record.
     */
    public record ServiceConfig(
            int publicId,
            String name,
            BigDecimal rate,
            int minOrder,
            int maxOrder,
            String category
    ) {
        public static ServiceConfig of(int id, String name, double rate, 
                                        int min, int max, String category) {
            return new ServiceConfig(id, name, new BigDecimal(String.valueOf(rate)), 
                    min, max, category);
        }
    }
    
    /**
     * Get the global scope.
     */
    public GlobalScope getGlobalScope() {
        return globalScope;
    }
}
