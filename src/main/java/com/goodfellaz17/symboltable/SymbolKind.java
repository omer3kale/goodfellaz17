package com.goodfellaz17.symboltable;

/**
 * MontiCore-inspired Symbol Kind enumeration.
 * 
 * Manual Ch.9: Each symbol has a kind that categorizes its type.
 * Used for type-safe symbol resolution in scopes.
 */
public enum SymbolKind {
    
    /**
     * Service symbol: Spotify Plays, Monthly Listeners, etc.
     */
    SERVICE("service"),
    
    /**
     * API Key symbol: Tenant identifier with balance.
     */
    API_KEY("api_key"),
    
    /**
     * Order symbol: Active order instance.
     */
    ORDER("order"),
    
    /**
     * Geo Target symbol: Geographic region (USA, EU, WW).
     */
    GEO_TARGET("geo_target"),
    
    /**
     * Proxy symbol: Residential proxy entry.
     */
    PROXY("proxy");
    
    private final String name;
    
    SymbolKind(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public static SymbolKind fromString(String name) {
        for (SymbolKind kind : values()) {
            if (kind.name.equalsIgnoreCase(name)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown symbol kind: " + name);
    }
}
