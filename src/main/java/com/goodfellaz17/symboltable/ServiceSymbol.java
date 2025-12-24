package com.goodfellaz17.symboltable;

import java.math.BigDecimal;

/**
 * Service Symbol - Represents an SMM service in the symbol table.
 * 
 * Manual Ch.9.6: Symbols encapsulate domain-specific attributes.
 * ServiceSymbol captures Perfect Panel service properties.
 * 
 * Examples:
 * - Spotify Plays WW ($0.50/1k, min 100, max 10M)
 * - Spotify Monthly USA ($1.90/1k, min 500, max 50k)
 */
public class ServiceSymbol implements Symbol {
    
    private final int publicId;          // Perfect Panel service ID
    private final String name;           // Display name
    private final BigDecimal rate;       // $/1000 rate
    private final int minOrder;          // Minimum order quantity
    private final int maxOrder;          // Maximum order quantity
    private final String category;       // Category (spotify, youtube, etc.)
    private final boolean isActive;      // Service availability
    private Scope enclosingScope;
    
    public ServiceSymbol(int publicId, String name, BigDecimal rate, 
                         int minOrder, int maxOrder, String category) {
        this(publicId, name, rate, minOrder, maxOrder, category, true);
    }
    
    public ServiceSymbol(int publicId, String name, BigDecimal rate, 
                         int minOrder, int maxOrder, String category, boolean isActive) {
        this.publicId = publicId;
        this.name = name;
        this.rate = rate;
        this.minOrder = minOrder;
        this.maxOrder = maxOrder;
        this.category = category;
        this.isActive = isActive;
    }
    
    /**
     * Calculate cost for a given quantity.
     */
    public BigDecimal calculateCost(int quantity) {
        return rate.multiply(BigDecimal.valueOf(quantity))
                   .divide(BigDecimal.valueOf(1000));
    }
    
    /**
     * Validate order quantity against service limits.
     */
    public boolean isValidQuantity(int quantity) {
        return quantity >= minOrder && quantity <= maxOrder;
    }
    
    // Symbol interface implementation
    
    @Override
    public String getName() {
        return String.valueOf(publicId);  // Use publicId as symbol name for lookup
    }
    
    @Override
    public SymbolKind getKind() {
        return SymbolKind.SERVICE;
    }
    
    @Override
    public Scope getEnclosingScope() {
        return enclosingScope;
    }
    
    @Override
    public void setEnclosingScope(Scope scope) {
        this.enclosingScope = scope;
    }
    
    // Getters
    
    public int getPublicId() {
        return publicId;
    }
    
    public String getDisplayName() {
        return name;
    }
    
    public BigDecimal getRate() {
        return rate;
    }
    
    public int getMinOrder() {
        return minOrder;
    }
    
    public int getMaxOrder() {
        return maxOrder;
    }
    
    public String getCategory() {
        return category;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    @Override
    public String toString() {
        return String.format("ServiceSymbol[id=%d, name=%s, rate=$%.2f, min=%d, max=%d]",
                publicId, name, rate, minOrder, maxOrder);
    }
}
