package com.goodfellaz17.prettyprinter;

import com.goodfellaz17.symboltable.*;
import com.goodfellaz17.visitor.SymbolVisitor;
import com.goodfellaz17.visitor.Visitor;

/**
 * Order PrettyPrinter - Formats OrderSymbol output.
 * 
 * Manual Ch.8.24: Sublanguage visitor for delegation.
 * Formats orders for Perfect Panel status responses.
 */
public class OrderPrettyPrinter implements SymbolVisitor<String> {
    
    private SymbolVisitor<String> realThis = this;
    private final IndentPrinter printer;
    
    public OrderPrettyPrinter(IndentPrinter printer) {
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
    public String visit(OrderSymbol symbol) {
        printer.println(String.format("Order #%s", symbol.getOrderId().toString().substring(0, 8)));
        printer.indent();
        printer.println(String.format("├── Service: %d", symbol.getServiceId()));
        printer.println(String.format("├── Track: %s", truncateUrl(symbol.getTrackUrl())));
        printer.println(String.format("├── Progress: %,d / %,d (%.1f%%)", 
                symbol.getDelivered(), symbol.getQuantity(), symbol.getProgress()));
        printer.println(String.format("├── Remaining: %,d", symbol.getRemaining()));
        printer.println(String.format("└── Status: %s", formatStatus(symbol.getStatus())));
        printer.unindent();
        
        return printer.getContent();
    }
    
    /**
     * Format for Perfect Panel API response.
     */
    public String formatForApi(OrderSymbol symbol) {
        return String.format(
                "{\"order\":\"%s\",\"status\":\"%s\",\"charge\":\"%.4f\"," +
                "\"start_count\":%d,\"remains\":%d}",
                symbol.getOrderId(),
                symbol.getStatus().toLowerCase(),
                0.0,  // Charge calculated elsewhere
                symbol.getDelivered(),
                symbol.getRemaining()
        );
    }
    
    /**
     * Format status with emoji.
     */
    private String formatStatus(String status) {
        return switch (status.toUpperCase()) {
            case "PENDING" -> "⏳ Pending";
            case "PROCESSING" -> "⚡ Processing";
            case "IN_PROGRESS" -> "🔄 In Progress";
            case "COMPLETED" -> "✅ Completed";
            case "CANCELLED" -> "❌ Cancelled";
            case "PARTIAL" -> "⚠️ Partial";
            default -> status;
        };
    }
    
    /**
     * Truncate URL for display.
     */
    private String truncateUrl(String url) {
        if (url == null) return "N/A";
        if (url.length() <= 50) return url;
        return url.substring(0, 47) + "...";
    }
}
