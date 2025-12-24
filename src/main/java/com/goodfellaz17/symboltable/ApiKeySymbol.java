package com.goodfellaz17.symboltable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * API Key Symbol - Represents a tenant in the symbol table.
 * 
 * Manual Ch.9.6: Symbols encapsulate domain-specific attributes.
 * ApiKeySymbol captures Perfect Panel tenant properties.
 * 
 * Used for:
 * - Tenant isolation (scope hierarchy)
 * - Balance tracking
 * - Rate limiting
 */
public class ApiKeySymbol implements Symbol {
    
    private final String apiKey;          // Unique API key
    private final UUID userId;            // Associated user ID
    private BigDecimal balance;           // Current balance
    private final Instant createdAt;
    private Instant lastUsedAt;
    private boolean isActive;
    private Scope enclosingScope;
    
    public ApiKeySymbol(String apiKey, UUID userId, BigDecimal balance) {
        this.apiKey = apiKey;
        this.userId = userId;
        this.balance = balance;
        this.createdAt = Instant.now();
        this.lastUsedAt = Instant.now();
        this.isActive = true;
    }
    
    /**
     * Check if balance is sufficient for order.
     */
    public boolean hasSufficientBalance(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }
    
    /**
     * Deduct balance for order.
     */
    public void deductBalance(BigDecimal amount) {
        if (!hasSufficientBalance(amount)) {
            throw new IllegalStateException("Insufficient balance: " + balance + " < " + amount);
        }
        this.balance = balance.subtract(amount);
        this.lastUsedAt = Instant.now();
    }
    
    /**
     * Add balance (deposit/refund).
     */
    public void addBalance(BigDecimal amount) {
        this.balance = balance.add(amount);
        this.lastUsedAt = Instant.now();
    }
    
    /**
     * Deactivate this API key.
     */
    public void deactivate() {
        this.isActive = false;
    }
    
    // Symbol interface implementation
    
    @Override
    public String getName() {
        return apiKey;
    }
    
    @Override
    public SymbolKind getKind() {
        return SymbolKind.API_KEY;
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
    
    public String getApiKey() {
        return apiKey;
    }
    
    public UUID getUserId() {
        return userId;
    }
    
    public BigDecimal getBalance() {
        return balance;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    @Override
    public String toString() {
        return String.format("ApiKeySymbol[key=%s, balance=$%.2f, active=%b]",
                apiKey.substring(0, 8) + "...", balance, isActive);
    }
}
