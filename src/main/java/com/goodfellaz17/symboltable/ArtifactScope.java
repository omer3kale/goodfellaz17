package com.goodfellaz17.symboltable;

/**
 * MontiCore-inspired ArtifactScope.
 * 
 * Manual Ch.9.3.3: ArtifactScope represents a single compilation unit.
 * In GOODFELLAZ17: Each API key gets its own ArtifactScope for tenant isolation.
 * 
 * Features:
 * - Tenant-scoped symbol resolution
 * - Import management from GlobalScope
 * - Shadowing for local overrides
 */
public class ArtifactScope extends Scope {
    
    private final String apiKey;
    private final GlobalScope globalScope;
    
    public ArtifactScope(String apiKey, GlobalScope globalScope) {
        super("artifact:" + apiKey, globalScope);
        this.apiKey = apiKey;
        this.globalScope = globalScope;
        this.setShadowing(true);  // Local symbols shadow globals
    }
    
    /**
     * Get the API key for this artifact scope.
     */
    public String getApiKey() {
        return apiKey;
    }
    
    /**
     * Get the global scope reference.
     */
    public GlobalScope getGlobalScope() {
        return globalScope;
    }
    
    /**
     * Resolve a service by ID (delegates to global scope).
     */
    public ServiceSymbol resolveService(int serviceId) {
        return globalScope.resolveService(serviceId);
    }
    
    /**
     * Check if this scope has an active API key.
     */
    public boolean hasActiveApiKey() {
        return this.<ApiKeySymbol>resolveLocally(apiKey, SymbolKind.API_KEY)
                   .map(ApiKeySymbol::isActive)
                   .orElse(false);
    }
    
    @Override
    public String toString() {
        return String.format("ArtifactScope[apiKey=%s..., symbols=%d]",
                apiKey.substring(0, Math.min(8, apiKey.length())), size());
    }
}
