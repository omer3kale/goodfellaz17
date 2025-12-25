package com.goodfellaz17.visitor;

import com.goodfellaz17.symboltable.*;

/**
 * MontiCore-inspired DelegatorVisitor.
 * 
 * Manual Ch.8.3, 8.24: DelegatorVisitor composes multiple visitors.
 * Allows combining visitors for different symbol types.
 * 
 * Features:
 * - realThis pattern (Manual Ch.8.22)
 * - Delegation to sub-visitors
 * - Override capability for specific symbols
 * 
 * @param <T> The return type of visit methods
 */
public abstract class DelegatorVisitor<T> implements SymbolVisitor<T> {
    
    protected SymbolVisitor<T> realThis = this;
    protected SymbolVisitor<T> serviceVisitor;
    protected SymbolVisitor<T> apiKeyVisitor;
    protected SymbolVisitor<T> orderVisitor;
    protected SymbolVisitor<T> scopeVisitor;
    
    @Override
    public Visitor<T> getRealThis() {
        return realThis;
    }
    
    @Override
    public void setRealThis(Visitor<T> realThis) {
        this.realThis = (SymbolVisitor<T>) realThis;
    }
    
    /**
     * Set the service visitor delegate.
     */
    public void setServiceVisitor(SymbolVisitor<T> visitor) {
        this.serviceVisitor = visitor;
        visitor.setRealThis(this.realThis);
    }
    
    /**
     * Set the API key visitor delegate.
     */
    public void setApiKeyVisitor(SymbolVisitor<T> visitor) {
        this.apiKeyVisitor = visitor;
        visitor.setRealThis(this.realThis);
    }
    
    /**
     * Set the order visitor delegate.
     */
    public void setOrderVisitor(SymbolVisitor<T> visitor) {
        this.orderVisitor = visitor;
        visitor.setRealThis(this.realThis);
    }
    
    /**
     * Set the scope visitor delegate.
     */
    public void setScopeVisitor(SymbolVisitor<T> visitor) {
        this.scopeVisitor = visitor;
        visitor.setRealThis(this.realThis);
    }
    
    @Override
    public T visit(ServiceSymbol symbol) {
        if (serviceVisitor != null) {
            return serviceVisitor.visit(symbol);
        }
        return null;
    }
    
    @Override
    public T visit(ApiKeySymbol symbol) {
        if (apiKeyVisitor != null) {
            return apiKeyVisitor.visit(symbol);
        }
        return null;
    }
    
    @Override
    public T visit(OrderSymbol symbol) {
        if (orderVisitor != null) {
            return orderVisitor.visit(symbol);
        }
        return null;
    }
    
    @Override
    public T visit(Scope scope) {
        if (scopeVisitor != null) {
            return scopeVisitor.visit(scope);
        }
        return null;
    }
}
