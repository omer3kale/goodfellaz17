package com.goodfellaz17.prettyprinter;

import com.goodfellaz17.symboltable.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PrettyPrinter Tests - MontiCore Manual Ch.8 DelegatorVisitor validation.
 */
@DisplayName("PrettyPrinter Tests (MontiCore Ch.8)")
class PrettyPrinterTest {
    
    private SmmPrettyPrinter prettyPrinter;
    private SmmSymbolTable symbolTable;
    
    @BeforeEach
    void setUp() {
        prettyPrinter = new SmmPrettyPrinter();
        symbolTable = new SmmSymbolTable();
        symbolTable.initialize();
    }
    
    @Nested
    @DisplayName("Service Printing")
    class ServicePrintingTests {
        
        @Test
        @DisplayName("Should print single service")
        void shouldPrintSingleService() {
            ServiceSymbol service = symbolTable.resolveService(1);
            
            String output = prettyPrinter.printService(service);
            
            assertNotNull(output);
            assertTrue(output.contains("Service #1"));
            assertTrue(output.contains("Spotify Plays"));
            assertTrue(output.contains("Rate"));
        }
        
        @Test
        @DisplayName("Should print service catalog")
        void shouldPrintServiceCatalog() {
            List<ServiceSymbol> services = symbolTable.getAllServices();
            
            String output = prettyPrinter.printServiceCatalog(services);
            
            assertNotNull(output);
            assertTrue(output.contains("GOODFELLAZ17"));
            assertTrue(output.contains("Service Catalog"));
            assertTrue(output.contains("Total:"));
        }
        
        @Test
        @DisplayName("Should print services as JSON")
        void shouldPrintServicesJson() {
            List<ServiceSymbol> services = symbolTable.getAllServices();
            
            String json = prettyPrinter.printServicesJson(services);
            
            assertNotNull(json);
            assertTrue(json.startsWith("["));
            assertTrue(json.endsWith("]"));
            assertTrue(json.contains("\"service\":"));
            assertTrue(json.contains("\"name\":"));
        }
    }
    
    @Nested
    @DisplayName("Order Printing")
    class OrderPrintingTests {
        
        @Test
        @DisplayName("Should print single order")
        void shouldPrintSingleOrder() {
            OrderSymbol order = new OrderSymbol(UUID.randomUUID(), 1, 
                    "https://open.spotify.com/track/test", 5000);
            order.addDelivered(2500);
            
            String output = prettyPrinter.printOrder(order);
            
            assertNotNull(output);
            assertTrue(output.contains("Order #"));
            assertTrue(output.contains("Progress"));
            assertTrue(output.contains("50.0%"));
        }
        
        @Test
        @DisplayName("Should print order status for API")
        void shouldPrintOrderStatusForApi() {
            OrderSymbol order = new OrderSymbol(UUID.randomUUID(), 1, 
                    "https://open.spotify.com/track/test", 5000);
            
            String status = prettyPrinter.printOrderStatus(order);
            
            assertNotNull(status);
            assertTrue(status.contains("\"order\""));
            assertTrue(status.contains("\"status\""));
            assertTrue(status.contains("\"remains\""));
        }
        
        @Test
        @DisplayName("Should print multiple orders")
        void shouldPrintMultipleOrders() {
            List<OrderSymbol> orders = List.of(
                    new OrderSymbol(UUID.randomUUID(), 1, "http://track1", 1000),
                    new OrderSymbol(UUID.randomUUID(), 2, "http://track2", 2000)
            );
            
            String output = prettyPrinter.printOrders(orders);
            
            assertNotNull(output);
            assertTrue(output.contains("Orders (2)"));
        }
    }
    
    @Nested
    @DisplayName("Scope Printing")
    class ScopePrintingTests {
        
        @Test
        @DisplayName("Should print global scope")
        void shouldPrintGlobalScope() {
            GlobalScope globalScope = symbolTable.getGlobalScope();
            
            String output = prettyPrinter.visit(globalScope);
            
            assertNotNull(output);
            assertTrue(output.contains("GlobalScope"));
            assertTrue(output.contains("Services"));
        }
        
        @Test
        @DisplayName("Should print statistics")
        void shouldPrintStatistics() {
            String output = prettyPrinter.printStatistics(symbolTable);
            
            assertNotNull(output);
            assertTrue(output.contains("Symbol Table Statistics"));
            assertTrue(output.contains("Services"));
            assertTrue(output.contains("Tenants"));
        }
    }
    
    @Nested
    @DisplayName("IndentPrinter")
    class IndentPrinterTests {
        
        @Test
        @DisplayName("Should handle indentation")
        void shouldHandleIndentation() {
            IndentPrinter printer = new IndentPrinter();
            
            printer.println("Level 0");
            printer.indent();
            printer.println("Level 1");
            printer.indent();
            printer.println("Level 2");
            printer.unindent();
            printer.println("Level 1 again");
            
            String output = printer.getContent();
            assertTrue(output.contains("Level 0"));
            assertTrue(output.contains("  Level 1"));
            assertTrue(output.contains("    Level 2"));
        }
        
        @Test
        @DisplayName("Should support printf")
        void shouldSupportPrintf() {
            IndentPrinter printer = new IndentPrinter();
            
            printer.printf("Value: %d, Name: %s\n", 42, "test");
            
            String output = printer.getContent();
            assertTrue(output.contains("Value: 42"));
            assertTrue(output.contains("Name: test"));
        }
        
        @Test
        @DisplayName("Should clear buffer")
        void shouldClearBuffer() {
            IndentPrinter printer = new IndentPrinter();
            
            printer.println("Some text");
            assertFalse(printer.getContent().isEmpty());
            
            printer.clear();
            assertTrue(printer.getContent().isEmpty());
        }
    }
}
