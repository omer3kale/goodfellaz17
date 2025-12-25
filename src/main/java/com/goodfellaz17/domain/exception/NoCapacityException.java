package com.goodfellaz17.domain.exception;

/**
 * Thrown when no proxy source has available capacity to fulfill a routing request.
 * The routing layer should handle this by either delaying the segment or failing gracefully.
 */
public class NoCapacityException extends RuntimeException {
    
    private final String orderId;
    private final String serviceId;
    private final String attemptedSources;
    
    public NoCapacityException(String message) {
        super(message);
        this.orderId = null;
        this.serviceId = null;
        this.attemptedSources = null;
    }
    
    public NoCapacityException(String orderId, String serviceId, String attemptedSources) {
        super(String.format(
            "No capacity available for order %s (service: %s). Attempted sources: %s",
            orderId, serviceId, attemptedSources
        ));
        this.orderId = orderId;
        this.serviceId = serviceId;
        this.attemptedSources = attemptedSources;
    }
    
    public String getOrderId() {
        return orderId;
    }
    
    public String getServiceId() {
        return serviceId;
    }
    
    public String getAttemptedSources() {
        return attemptedSources;
    }
}
