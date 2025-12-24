package com.goodfellaz17.prettyprinter;

import com.goodfellaz17.symboltable.*;
import com.goodfellaz17.visitor.DelegatorVisitor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SMM PrettyPrinter - Composed DelegatorVisitor for all symbols.
 * 
 * Manual Ch.8.24: DelegatorVisitor composes sublanguage visitors.
 * SmmPrettyPrinter combines Service, Order, and ApiKey printing.
 * 
 * Usage:
 * ```java
 * String output = smmPrettyPrinter.printService(service);
 * String catalog = smmPrettyPrinter.printServiceCatalog(services);
 * ```
 */
@Component
public class SmmPrettyPrinter extends DelegatorVisitor<String> {
    
    private final IndentPrinter printer;
    private final ServicePrettyPrinter servicePrinter;
    private final OrderPrettyPrinter orderPrinter;
    
    public SmmPrettyPrinter() {
        this.printer = new IndentPrinter();
        this.servicePrinter = new ServicePrettyPrinter(printer);
        this.orderPrinter = new OrderPrettyPrinter(printer);
        
        // Configure delegation (Manual Ch.8.25)
        setServiceVisitor(servicePrinter);
        setOrderVisitor(orderPrinter);
    }
    
    // ==================== Service Printing ====================
    
    /**
     * Print a single service.
     */
    public String printService(ServiceSymbol service) {
        printer.clear();
        return visit(service);
    }
    
    /**
     * Print service catalog (all services).
     */
    public String printServiceCatalog(List<ServiceSymbol> services) {
        printer.clear();
        printer.println("╔════════════════════════════════════════════════════════════════╗");
        printer.println("║                    GOODFELLAZ17 Service Catalog                ║");
        printer.println("╠════════════════════════════════════════════════════════════════╣");
        printer.println("║  ID  │            Service Name            │  Rate   │  Min/Max  ║");
        printer.println("╠══════╪════════════════════════════════════╪═════════╪══════════╣");
        
        for (ServiceSymbol service : services) {
            printer.printf("║ %4d │ %-34s │ $%5.2f  │ %4d-%dM  ║\n",
                    service.getPublicId(),
                    truncate(service.getDisplayName(), 34),
                    service.getRate(),
                    service.getMinOrder(),
                    service.getMaxOrder() / 1_000_000
            );
        }
        
        printer.println("╚════════════════════════════════════════════════════════════════╝");
        printer.printf("Total: %d services available\n", services.size());
        
        return printer.getContent();
    }
    
    /**
     * Print services for Perfect Panel API (JSON array).
     */
    public String printServicesJson(List<ServiceSymbol> services) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < services.size(); i++) {
            if (i > 0) json.append(",");
            json.append(servicePrinter.formatForApi(services.get(i)));
        }
        json.append("]");
        return json.toString();
    }
    
    // ==================== Order Printing ====================
    
    /**
     * Print a single order.
     */
    public String printOrder(OrderSymbol order) {
        printer.clear();
        return visit(order);
    }
    
    /**
     * Print order status for Perfect Panel API.
     */
    public String printOrderStatus(OrderSymbol order) {
        return orderPrinter.formatForApi(order);
    }
    
    /**
     * Print multiple orders.
     */
    public String printOrders(List<OrderSymbol> orders) {
        printer.clear();
        printer.println("Orders (" + orders.size() + "):");
        printer.println("─".repeat(50));
        
        for (OrderSymbol order : orders) {
            visit(order);
            printer.println();
        }
        
        return printer.getContent();
    }
    
    // ==================== Scope Printing ====================
    
    /**
     * Print a scope hierarchy.
     */
    @Override
    public String visit(Scope scope) {
        printer.clear();
        printScopeRecursive(scope, 0);
        return printer.getContent();
    }
    
    private void printScopeRecursive(Scope scope, int depth) {
        String prefix = "│  ".repeat(depth);
        
        if (scope instanceof GlobalScope globalScope) {
            printer.println(prefix + "GlobalScope");
            printer.println(prefix + "├── Services: " + globalScope.getAllServices().size());
            printer.println(prefix + "└── Tenants: " + globalScope.getTenantCount());
        } else if (scope instanceof ArtifactScope artifactScope) {
            printer.println(prefix + "└── ArtifactScope [" + 
                    artifactScope.getApiKey().substring(0, 8) + "...]");
            printer.println(prefix + "    └── Symbols: " + artifactScope.size());
        } else {
            printer.println(prefix + "Scope: " + scope.getName());
        }
        
        for (Scope subScope : scope.getSubScopes()) {
            printScopeRecursive(subScope, depth + 1);
        }
    }
    
    /**
     * Print symbol table statistics.
     */
    public String printStatistics(SmmSymbolTable symbolTable) {
        printer.clear();
        SmmSymbolTable.Statistics stats = symbolTable.getStatistics();
        
        printer.println("╔═══════════════════════════════════════╗");
        printer.println("║     Symbol Table Statistics           ║");
        printer.println("╠═══════════════════════════════════════╣");
        printer.printf("║  Services:      %,8d              ║\n", stats.serviceCount());
        printer.printf("║  Tenants:       %,8d              ║\n", stats.tenantCount());
        printer.printf("║  Total Symbols: %,8d              ║\n", stats.totalSymbols());
        printer.println("╚═══════════════════════════════════════╝");
        
        return printer.getContent();
    }
    
    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}
