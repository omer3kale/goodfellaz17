package com.goodfellaz17.symboltable;

import java.util.UUID;

/**
 * Order Symbol - Represents an active order in the symbol table.
 * 
 * Manual Ch.9.6: Symbols encapsulate domain-specific attributes.
 * OrderSymbol tracks order progress for real-time resolution.
 */
public class OrderSymbol implements Symbol {
    
    private final UUID orderId;
    private final int serviceId;
    private final String trackUrl;
    private final int quantity;
    private int delivered;
    private String status;
    private Scope enclosingScope;
    
    public OrderSymbol(UUID orderId, int serviceId, String trackUrl, int quantity) {
        this.orderId = orderId;
        this.serviceId = serviceId;
        this.trackUrl = trackUrl;
        this.quantity = quantity;
        this.delivered = 0;
        this.status = "PENDING";
    }
    
    /**
     * Get remaining quantity to deliver.
     */
    public int getRemaining() {
        return Math.max(0, quantity - delivered);
    }
    
    /**
     * Get progress percentage.
     */
    public double getProgress() {
        if (quantity == 0) return 100.0;
        return (double) delivered / quantity * 100.0;
    }
    
    /**
     * Update delivered count.
     */
    public void addDelivered(int count) {
        this.delivered += count;
        if (delivered >= quantity) {
            this.status = "COMPLETED";
        }
    }
    
    /**
     * Update status.
     */
    public void setStatus(String status) {
        this.status = status;
    }
    
    // Symbol interface implementation
    
    @Override
    public String getName() {
        return orderId.toString();
    }
    
    @Override
    public SymbolKind getKind() {
        return SymbolKind.ORDER;
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
    
    public UUID getOrderId() {
        return orderId;
    }
    
    public int getServiceId() {
        return serviceId;
    }
    
    public String getTrackUrl() {
        return trackUrl;
    }
    
    public int getQuantity() {
        return quantity;
    }
    
    public int getDelivered() {
        return delivered;
    }
    
    public String getStatus() {
        return status;
    }
    
    @Override
    public String toString() {
        return String.format("OrderSymbol[id=%s, service=%d, progress=%.1f%%]",
                orderId.toString().substring(0, 8), serviceId, getProgress());
    }
}
