package com.goodfellaz17.domain.model;

import java.util.Optional;

/**
 * Context object passed to the routing layer for proxy selection.
 * Contains all information needed to make a routing decision.
 */
public record OrderContext(
    String orderId,
    String serviceId,
    String serviceName,
    RoutingProfile routingProfile,
    String targetCountry,
    int quantity,
    Optional<String> preferredSource,
    boolean isRetry
) {
    
    /**
     * Builder for fluent construction.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String orderId;
        private String serviceId;
        private String serviceName;
        private RoutingProfile routingProfile = RoutingProfile.DEFAULT;
        private String targetCountry;
        private int quantity;
        private String preferredSource;
        private boolean isRetry = false;
        
        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }
        
        public Builder serviceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }
        
        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }
        
        public Builder routingProfile(RoutingProfile profile) {
            this.routingProfile = profile;
            return this;
        }
        
        public Builder targetCountry(String country) {
            this.targetCountry = country;
            return this;
        }
        
        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }
        
        public Builder preferredSource(String source) {
            this.preferredSource = source;
            return this;
        }
        
        public Builder isRetry(boolean retry) {
            this.isRetry = retry;
            return this;
        }
        
        public OrderContext build() {
            return new OrderContext(
                orderId,
                serviceId,
                serviceName,
                routingProfile,
                targetCountry,
                quantity,
                Optional.ofNullable(preferredSource),
                isRetry
            );
        }
    }
    
    /**
     * Check if this context requires geo-specific routing.
     */
    public boolean requiresGeo() {
        return routingProfile.geoSensitive() && targetCountry != null && !targetCountry.isBlank();
    }
    
    /**
     * Check if this context prefers mobile sources.
     */
    public boolean prefersMobile() {
        return routingProfile.needsMobileLikeBehavior();
    }
    
    /**
     * Get the service priority for routing decisions.
     */
    public ServicePriority getPriority() {
        return routingProfile.priority();
    }
}
