package com.goodfellaz17.symboltable;

/**
 * MontiCore-inspired Symbol base interface.
 * 
 * Manual Ch.9: Symbols represent identifiable entities in a language.
 * In GOODFELLAZ17: Services, API Keys, Orders are symbols.
 * 
 * @see <a href="https://monticore.de">MontiCore Reference Manual</a>
 */
public interface Symbol {
    
    /**
     * Get the unique name of this symbol.
     */
    String getName();
    
    /**
     * Get the kind of this symbol (e.g., SERVICE, API_KEY, ORDER).
     */
    SymbolKind getKind();
    
    /**
     * Get the enclosing scope of this symbol.
     */
    Scope getEnclosingScope();
    
    /**
     * Set the enclosing scope of this symbol.
     */
    void setEnclosingScope(Scope scope);
}
