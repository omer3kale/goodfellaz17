package com.goodfellaz17.cocos;

/**
 * MontiCore-inspired Context Condition (CoCo) interface.
 * 
 * Manual Ch.10: CoCos define semantic constraints that must hold.
 * They validate domain rules at compile-time or runtime.
 * 
 * In GOODFELLAZ17, CoCos validate:
 * - Order quantities (min/max limits)
 * - Spotify compliance (drip rate, duration)
 * - API key balance (sufficient funds)
 * 
 * @param <T> The type of element to validate
 */
public interface CoCo<T> {
    
    /**
     * Check if the element satisfies this context condition.
     * 
     * @param element The element to validate
     * @throws CoCoViolationException if validation fails
     */
    void check(T element) throws CoCoViolationException;
    
    /**
     * Get the unique error code for this CoCo.
     * Format: 0xGFLXX where XX is the error number.
     */
    String getErrorCode();
    
    /**
     * Get a human-readable description of this CoCo.
     */
    String getDescription();
}
