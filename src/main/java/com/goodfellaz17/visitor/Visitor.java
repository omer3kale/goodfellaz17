package com.goodfellaz17.visitor;

/**
 * MontiCore-inspired Visitor interface.
 * 
 * Manual Ch.8.1: Visitors traverse AST/symbol structures.
 * Base interface for all visitors in GOODFELLAZ17.
 * 
 * @param <T> The return type of visit methods
 */
public interface Visitor<T> {
    
    /**
     * Get the real visitor instance (for delegation).
     * Manual Ch.8.22: realThis pattern for composition.
     */
    Visitor<T> getRealThis();
    
    /**
     * Set the real visitor instance.
     */
    void setRealThis(Visitor<T> realThis);
}
