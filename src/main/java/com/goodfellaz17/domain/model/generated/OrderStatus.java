package com.goodfellaz17.domain.model.generated;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * Order lifecycle status.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
public enum OrderStatus {
    
    /** Order created, awaiting processing */
    PENDING("pending"),
    
    /** Validating target URL and initial counts */
    VALIDATING("validating"),
    
    /** Actively delivering streams/engagement */
    RUNNING("running"),
    
    /** All quantity delivered successfully */
    COMPLETED("completed"),
    
    /** Partially delivered, remainder refunded */
    PARTIAL("partial"),
    
    /** Failed to deliver, full refund */
    FAILED("failed"),
    
    /** Refunded after completion/partial */
    REFUNDED("refunded"),
    
    /** Cancelled by user before processing */
    CANCELLED("cancelled");
    
    private final String value;
    
    OrderStatus(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == REFUNDED || this == CANCELLED;
    }
    
    public boolean isActive() {
        return this == PENDING || this == VALIDATING || this == RUNNING;
    }
    
    public static OrderStatus fromValue(String value) {
        for (OrderStatus status : values()) {
            if (status.value.equals(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown OrderStatus: " + value);
    }
}
