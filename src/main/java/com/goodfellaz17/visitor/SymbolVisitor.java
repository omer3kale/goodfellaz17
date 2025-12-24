package com.goodfellaz17.visitor;

import com.goodfellaz17.symboltable.*;

/**
 * MontiCore-inspired Symbol Visitor.
 * 
 * Manual Ch.8.2: Visitors for symbol structures.
 * SymbolVisitor visits all symbol types in GOODFELLAZ17.
 * 
 * @param <T> The return type of visit methods
 */
public interface SymbolVisitor<T> extends Visitor<T> {
    
    /**
     * Visit a ServiceSymbol.
     */
    default T visit(ServiceSymbol symbol) {
        return null;
    }
    
    /**
     * Visit an ApiKeySymbol.
     */
    default T visit(ApiKeySymbol symbol) {
        return null;
    }
    
    /**
     * Visit an OrderSymbol.
     */
    default T visit(OrderSymbol symbol) {
        return null;
    }
    
    /**
     * Visit a Scope.
     */
    default T visit(Scope scope) {
        return null;
    }
    
    /**
     * Visit a GlobalScope.
     */
    default T visit(GlobalScope scope) {
        return visit((Scope) scope);
    }
    
    /**
     * Visit an ArtifactScope.
     */
    default T visit(ArtifactScope scope) {
        return visit((Scope) scope);
    }
}
