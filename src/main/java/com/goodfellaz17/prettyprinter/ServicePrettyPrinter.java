package com.goodfellaz17.prettyprinter;

import com.goodfellaz17.symboltable.*;
import com.goodfellaz17.visitor.SymbolVisitor;
import com.goodfellaz17.visitor.Visitor;

/**
 * Service PrettyPrinter - Formats ServiceSymbol output.
 * 
 * Manual Ch.8.24: Sublanguage visitor for delegation.
 * Formats services for Perfect Panel API documentation.
 */
public class ServicePrettyPrinter implements SymbolVisitor<String> {
    
    private SymbolVisitor<String> realThis = this;
    private final IndentPrinter printer;
    
    public ServicePrettyPrinter(IndentPrinter printer) {
        this.printer = printer;
    }
    
    @Override
    public Visitor<String> getRealThis() {
        return realThis;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void setRealThis(Visitor<String> realThis) {
        this.realThis = (SymbolVisitor<String>) realThis;
    }
    
    @Override
    public String visit(ServiceSymbol symbol) {
        printer.println(String.format("Service #%d: %s", 
                symbol.getPublicId(), symbol.getDisplayName()));
        printer.indent();
        printer.println(String.format("├── Rate: $%.2f per 1000", symbol.getRate()));
        printer.println(String.format("├── Min Order: %,d", symbol.getMinOrder()));
        printer.println(String.format("├── Max Order: %,d", symbol.getMaxOrder()));
        printer.println(String.format("├── Category: %s", symbol.getCategory()));
        printer.println(String.format("└── Status: %s", symbol.isActive() ? "✓ Active" : "✗ Inactive"));
        printer.unindent();
        
        return printer.getContent();
    }
    
    /**
     * Format for Perfect Panel API response.
     */
    public String formatForApi(ServiceSymbol symbol) {
        return String.format(
                "{\"service\":%d,\"name\":\"%s\",\"rate\":%.4f,\"min\":%d,\"max\":%d,\"category\":\"%s\"}",
                symbol.getPublicId(),
                symbol.getDisplayName(),
                symbol.getRate(),
                symbol.getMinOrder(),
                symbol.getMaxOrder(),
                symbol.getCategory()
        );
    }
    
    /**
     * Format for CLI table output.
     */
    public String formatForTable(ServiceSymbol symbol) {
        return String.format("| %4d | %-30s | $%6.2f | %,8d | %,12d |",
                symbol.getPublicId(),
                truncate(symbol.getDisplayName(), 30),
                symbol.getRate(),
                symbol.getMinOrder(),
                symbol.getMaxOrder()
        );
    }
    
    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}
