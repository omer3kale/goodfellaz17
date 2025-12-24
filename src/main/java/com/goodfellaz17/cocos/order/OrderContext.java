package com.goodfellaz17.cocos.order;

import com.goodfellaz17.symboltable.ApiKeySymbol;
import com.goodfellaz17.symboltable.ServiceSymbol;

/**
 * Order context for CoCo validation.
 * 
 * Manual Ch.10.8: CoCos receive context with symbol table references.
 * OrderContext bundles all required information for order validation.
 */
public record OrderContext(
        ApiKeySymbol apiKey,
        ServiceSymbol service,
        String trackUrl,
        int quantity,
        int deliveryHours,
        int existingPlays  // For drip rate calculation
) {
    
    /**
     * Create a context for standard orders (24h delivery).
     */
    public static OrderContext standard(ApiKeySymbol apiKey, ServiceSymbol service, 
                                        String trackUrl, int quantity) {
        return new OrderContext(apiKey, service, trackUrl, quantity, 24, 0);
    }
    
    /**
     * Create a context for VIP orders (faster delivery).
     */
    public static OrderContext vip(ApiKeySymbol apiKey, ServiceSymbol service, 
                                   String trackUrl, int quantity) {
        return new OrderContext(apiKey, service, trackUrl, quantity, 6, 0);
    }
    
    /**
     * Create a context for drip feed orders.
     */
    public static OrderContext drip(ApiKeySymbol apiKey, ServiceSymbol service, 
                                    String trackUrl, int quantity, int deliveryHours) {
        return new OrderContext(apiKey, service, trackUrl, quantity, deliveryHours, 0);
    }
    
    /**
     * Builder for flexible context creation.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private ApiKeySymbol apiKey;
        private ServiceSymbol service;
        private String trackUrl;
        private int quantity;
        private int deliveryHours = 24;
        private int existingPlays = 0;
        
        public Builder apiKey(ApiKeySymbol apiKey) {
            this.apiKey = apiKey;
            return this;
        }
        
        public Builder service(ServiceSymbol service) {
            this.service = service;
            return this;
        }
        
        public Builder trackUrl(String trackUrl) {
            this.trackUrl = trackUrl;
            return this;
        }
        
        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }
        
        public Builder deliveryHours(int deliveryHours) {
            this.deliveryHours = deliveryHours;
            return this;
        }
        
        public Builder existingPlays(int existingPlays) {
            this.existingPlays = existingPlays;
            return this;
        }
        
        public OrderContext build() {
            return new OrderContext(apiKey, service, trackUrl, quantity, deliveryHours, existingPlays);
        }
    }
}
