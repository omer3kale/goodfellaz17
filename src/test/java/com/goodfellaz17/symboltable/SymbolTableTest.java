package com.goodfellaz17.symboltable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Symbol Table Tests - MontiCore Manual Ch.9 validation.
 * 
 * Tests:
 * - Scope hierarchy (GlobalScope → ArtifactScope)
 * - Symbol resolution (O(1) lookup)
 * - Tenant isolation
 * - Shadowing behavior
 */
@DisplayName("Symbol Table Tests (MontiCore Ch.9)")
class SymbolTableTest {
    
    private SmmSymbolTable symbolTable;
    
    @BeforeEach
    void setUp() {
        symbolTable = new SmmSymbolTable();
        symbolTable.initialize();
    }
    
    @Nested
    @DisplayName("Service Resolution (Manual 9.5)")
    class ServiceResolutionTests {
        
        @Test
        @DisplayName("Should resolve service by ID in O(1)")
        void shouldResolveServiceById() {
            // Given: Service ID 1 exists
            int serviceId = 1;
            
            // When: Resolving service
            ServiceSymbol service = symbolTable.resolveService(serviceId);
            
            // Then: Service is found
            assertNotNull(service);
            assertEquals(serviceId, service.getPublicId());
            assertEquals("Spotify Plays Worldwide", service.getDisplayName());
        }
        
        @Test
        @DisplayName("Should throw for non-existent service")
        void shouldThrowForNonExistentService() {
            // Given: Service ID 999 does not exist
            int invalidServiceId = 999;
            
            // When/Then: Should throw
            assertThrows(GlobalScope.ServiceNotFoundException.class,
                    () -> symbolTable.resolveService(invalidServiceId));
        }
        
        @Test
        @DisplayName("Should check service existence")
        void shouldCheckServiceExistence() {
            assertTrue(symbolTable.serviceExists(1));
            assertFalse(symbolTable.serviceExists(999));
        }
        
        @Test
        @DisplayName("Should return all services")
        void shouldReturnAllServices() {
            List<ServiceSymbol> services = symbolTable.getAllServices();
            
            assertNotNull(services);
            assertFalse(services.isEmpty());
            assertTrue(services.size() >= 7); // Default services
        }
    }
    
    @Nested
    @DisplayName("Scope Hierarchy (Manual 9.3)")
    class ScopeHierarchyTests {
        
        @Test
        @DisplayName("GlobalScope should contain services")
        void globalScopeShouldContainServices() {
            GlobalScope globalScope = symbolTable.getGlobalScope();
            
            assertNotNull(globalScope);
            assertFalse(globalScope.getAllServices().isEmpty());
        }
        
        @Test
        @DisplayName("Should create ArtifactScope per API key (Manual 9.3.3)")
        void shouldCreateArtifactScopePerApiKey() {
            // Given: Two different API keys
            String apiKey1 = "api-key-tenant-1";
            String apiKey2 = "api-key-tenant-2";
            
            // When: Getting tenant scopes
            ArtifactScope scope1 = symbolTable.getTenantScope(apiKey1);
            ArtifactScope scope2 = symbolTable.getTenantScope(apiKey2);
            
            // Then: Separate scopes created
            assertNotNull(scope1);
            assertNotNull(scope2);
            assertNotSame(scope1, scope2);
            assertEquals(2, symbolTable.getTenantCount());
        }
        
        @Test
        @DisplayName("Should resolve services from ArtifactScope via parent")
        void shouldResolveFromParentScope() {
            // Given: ArtifactScope for tenant
            ArtifactScope tenantScope = symbolTable.getTenantScope("test-api-key");
            
            // When: Resolving service via parent
            ServiceSymbol service = tenantScope.resolveService(1);
            
            // Then: Service resolved from GlobalScope
            assertNotNull(service);
            assertEquals(1, service.getPublicId());
        }
    }
    
    @Nested
    @DisplayName("Tenant Isolation (Manual 9.3.3)")
    class TenantIsolationTests {
        
        @Test
        @DisplayName("Should isolate orders per tenant")
        void shouldIsolateOrdersPerTenant() {
            // Given: Two tenants
            String tenant1 = "tenant-1-api-key";
            String tenant2 = "tenant-2-api-key";
            
            // When: Adding orders to different tenants
            OrderSymbol order1 = new OrderSymbol(UUID.randomUUID(), 1, "http://track1", 1000);
            OrderSymbol order2 = new OrderSymbol(UUID.randomUUID(), 1, "http://track2", 2000);
            
            symbolTable.trackOrder(tenant1, order1);
            symbolTable.trackOrder(tenant2, order2);
            
            // Then: Orders isolated
            List<OrderSymbol> tenant1Orders = symbolTable.getTenantOrders(tenant1);
            List<OrderSymbol> tenant2Orders = symbolTable.getTenantOrders(tenant2);
            
            assertEquals(1, tenant1Orders.size());
            assertEquals(1, tenant2Orders.size());
            assertEquals(1000, tenant1Orders.get(0).getQuantity());
            assertEquals(2000, tenant2Orders.get(0).getQuantity());
        }
        
        @Test
        @DisplayName("Should resolve order within tenant scope")
        void shouldResolveOrderWithinTenantScope() {
            // Given: Order in tenant scope
            String apiKey = "test-tenant-key";
            UUID orderId = UUID.randomUUID();
            OrderSymbol order = new OrderSymbol(orderId, 1, "http://track", 5000);
            symbolTable.trackOrder(apiKey, order);
            
            // When: Resolving order
            Optional<OrderSymbol> resolved = symbolTable.resolveOrder(apiKey, orderId.toString());
            
            // Then: Order found
            assertTrue(resolved.isPresent());
            assertEquals(orderId, resolved.get().getOrderId());
        }
        
        @Test
        @DisplayName("Should not resolve order from different tenant")
        void shouldNotResolveOrderFromDifferentTenant() {
            // Given: Order in tenant 1
            String tenant1 = "tenant-1";
            String tenant2 = "tenant-2";
            UUID orderId = UUID.randomUUID();
            symbolTable.trackOrder(tenant1, new OrderSymbol(orderId, 1, "http://track", 1000));
            
            // When: Trying to resolve from tenant 2
            Optional<OrderSymbol> resolved = symbolTable.resolveOrder(tenant2, orderId.toString());
            
            // Then: Not found (isolated)
            assertTrue(resolved.isEmpty());
        }
    }
    
    @Nested
    @DisplayName("Symbol Types")
    class SymbolTypeTests {
        
        @Test
        @DisplayName("ServiceSymbol should calculate cost")
        void serviceSymbolShouldCalculateCost() {
            ServiceSymbol service = symbolTable.resolveService(1);
            
            BigDecimal cost = service.calculateCost(10000);
            
            // $0.50 per 1000 * 10000 = $5.00
            assertEquals(new BigDecimal("5.00"), cost);
        }
        
        @Test
        @DisplayName("ServiceSymbol should validate quantity")
        void serviceSymbolShouldValidateQuantity() {
            ServiceSymbol service = symbolTable.resolveService(1);
            
            assertTrue(service.isValidQuantity(1000));
            assertFalse(service.isValidQuantity(10)); // Below min
            assertFalse(service.isValidQuantity(100_000_000)); // Above max
        }
        
        @Test
        @DisplayName("ApiKeySymbol should track balance")
        void apiKeySymbolShouldTrackBalance() {
            ApiKeySymbol apiKey = new ApiKeySymbol("test-key", UUID.randomUUID(), new BigDecimal("50.00"));
            
            assertTrue(apiKey.hasSufficientBalance(new BigDecimal("25.00")));
            assertFalse(apiKey.hasSufficientBalance(new BigDecimal("100.00")));
            
            apiKey.deductBalance(new BigDecimal("25.00"));
            assertEquals(new BigDecimal("25.00"), apiKey.getBalance());
        }
        
        @Test
        @DisplayName("OrderSymbol should track progress")
        void orderSymbolShouldTrackProgress() {
            OrderSymbol order = new OrderSymbol(UUID.randomUUID(), 1, "http://track", 1000);
            
            assertEquals(0, order.getDelivered());
            assertEquals(1000, order.getRemaining());
            assertEquals(0.0, order.getProgress());
            
            order.addDelivered(500);
            assertEquals(500, order.getDelivered());
            assertEquals(500, order.getRemaining());
            assertEquals(50.0, order.getProgress());
            
            order.addDelivered(500);
            assertEquals("COMPLETED", order.getStatus());
        }
    }
    
    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {
        
        @Test
        @DisplayName("Should provide accurate statistics")
        void shouldProvideAccurateStatistics() {
            // Given: Some tenant scopes
            symbolTable.getTenantScope("tenant-1");
            symbolTable.getTenantScope("tenant-2");
            
            // When: Getting statistics
            SmmSymbolTable.Statistics stats = symbolTable.getStatistics();
            
            // Then: Accurate counts
            assertTrue(stats.serviceCount() >= 7);
            assertEquals(2, stats.tenantCount());
            assertTrue(stats.totalSymbols() >= 0);
        }
    }
}
