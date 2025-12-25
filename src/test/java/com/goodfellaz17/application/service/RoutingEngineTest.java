package com.goodfellaz17.application.service;

import com.goodfellaz17.domain.exception.NoCapacityException;
import com.goodfellaz17.domain.model.*;
import com.goodfellaz17.domain.port.ProxySource;
import com.goodfellaz17.domain.port.ProxyStrategy;
import com.goodfellaz17.infrastructure.proxy.HybridProxyStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RoutingEngine.
 * 
 * Tests verify:
 * - Correct service priority and routing path for each service type
 * - Profile lookup and defaults
 * - Route hints for optional UI display
 */
@DisplayName("RoutingEngine Tests")
class RoutingEngineTest {
    
    private RoutingEngine engine;
    private MockProxyStrategy mockStrategy;
    
    @BeforeEach
    void setUp() {
        mockStrategy = new MockProxyStrategy();
        engine = new RoutingEngine(mockStrategy);
    }
    
    @Nested
    @DisplayName("Service Profile Mapping")
    class ServiceProfileMapping {
        
        @Test
        @DisplayName("USA plays should have PREMIUM priority and geo-sensitive")
        void usaPlaysShouldBePremium() {
            RoutingProfile profile = engine.getProfile("plays_usa");
            
            assertEquals(ServicePriority.PREMIUM, profile.priority());
            assertTrue(profile.geoSensitive());
            assertFalse(profile.needsMobileLikeBehavior());
        }
        
        @Test
        @DisplayName("Elite plays should have ELITE priority and prefer mobile")
        void elitePlaysShouldBeElite() {
            RoutingProfile profile = engine.getProfile("plays_elite");
            
            assertEquals(ServicePriority.ELITE, profile.priority());
            assertTrue(profile.geoSensitive());
            assertTrue(profile.needsMobileLikeBehavior());
        }
        
        @Test
        @DisplayName("Bulk plays should have HIGH_VOLUME priority")
        void bulkPlaysShouldBeHighVolume() {
            RoutingProfile profile = engine.getProfile("plays_bulk");
            
            assertEquals(ServicePriority.HIGH_VOLUME, profile.priority());
            assertFalse(profile.geoSensitive());
        }
        
        @Test
        @DisplayName("Worldwide plays should have BASIC priority")
        void worldwidePlaysShouldBeBasic() {
            RoutingProfile profile = engine.getProfile("plays_ww");
            
            assertEquals(ServicePriority.BASIC, profile.priority());
            assertFalse(profile.geoSensitive());
        }
        
        @Test
        @DisplayName("Mobile plays should have MOBILE_EMULATION priority")
        void mobilePlaysShouldBeMobileEmulation() {
            RoutingProfile profile = engine.getProfile("mobile_plays");
            
            assertEquals(ServicePriority.MOBILE_EMULATION, profile.priority());
            assertTrue(profile.needsMobileLikeBehavior());
        }
        
        @Test
        @DisplayName("Unknown service should default to BASIC")
        void unknownServiceShouldDefaultToBasic() {
            RoutingProfile profile = engine.getProfile("unknown_service_xyz");
            
            assertEquals(ServicePriority.BASIC, profile.priority());
            assertFalse(profile.geoSensitive());
            assertFalse(profile.needsMobileLikeBehavior());
        }
        
        @Test
        @DisplayName("Profile lookup should be case-insensitive")
        void profileLookupShouldBeCaseInsensitive() {
            RoutingProfile lower = engine.getProfile("plays_usa");
            RoutingProfile upper = engine.getProfile("PLAYS_USA");
            RoutingProfile mixed = engine.getProfile("Plays_USA");
            
            assertEquals(lower.priority(), upper.priority());
            assertEquals(lower.priority(), mixed.priority());
        }
    }
    
    @Nested
    @DisplayName("Routing Execution")
    class RoutingExecution {
        
        @Test
        @DisplayName("Should route order and return lease")
        void shouldRouteOrderAndReturnLease() {
            ProxyLease lease = engine.route(
                "order-123",
                "plays_usa",
                "USA Plays",
                "US",
                1000
            );
            
            assertNotNull(lease);
            assertEquals("mock", lease.sourceName());
            
            // Verify context was passed correctly
            OrderContext ctx = mockStrategy.lastContext;
            assertEquals("order-123", ctx.orderId());
            assertEquals("plays_usa", ctx.serviceId());
            assertEquals("US", ctx.targetCountry());
            assertEquals(1000, ctx.quantity());
            assertEquals(ServicePriority.PREMIUM, ctx.getPriority());
        }
        
        @Test
        @DisplayName("Should propagate NoCapacityException")
        void shouldPropagateNoCapacityException() {
            mockStrategy.shouldThrow = true;
            
            assertThrows(NoCapacityException.class, () ->
                engine.route("order-456", "plays_ww", "WW Plays", "GLOBAL", 1000)
            );
        }
    }
    
    @Nested
    @DisplayName("Route Hints")
    class RouteHints {
        
        @Test
        @DisplayName("Elite service should have 'Elite Route' hint")
        void eliteServiceShouldHaveEliteHint() {
            String hint = engine.getRouteHint("plays_elite");
            assertEquals("Elite Route", hint);
        }
        
        @Test
        @DisplayName("Premium service should have 'Premium Route' hint")
        void premiumServiceShouldHavePremiumHint() {
            String hint = engine.getRouteHint("plays_usa");
            assertEquals("Premium Route", hint);
        }
        
        @Test
        @DisplayName("High volume service should have 'High Volume' hint")
        void highVolumeServiceShouldHaveHighVolumeHint() {
            String hint = engine.getRouteHint("plays_bulk");
            assertEquals("High Volume", hint);
        }
        
        @Test
        @DisplayName("Mobile service should have 'Mobile Route' hint")
        void mobileServiceShouldHaveMobileHint() {
            String hint = engine.getRouteHint("mobile_plays");
            assertEquals("Mobile Route", hint);
        }
        
        @Test
        @DisplayName("Basic service should have 'Standard Route' hint")
        void basicServiceShouldHaveStandardHint() {
            String hint = engine.getRouteHint("plays_ww");
            assertEquals("Standard Route", hint);
        }
    }
    
    @Nested
    @DisplayName("Custom Profile Registration")
    class CustomProfileRegistration {
        
        @Test
        @DisplayName("Should allow registering custom profiles")
        void shouldAllowCustomProfileRegistration() {
            RoutingProfile custom = new RoutingProfile(
                ServicePriority.ELITE, true, true, 0.05
            );
            
            engine.registerProfile("custom_service", custom);
            
            RoutingProfile retrieved = engine.getProfile("custom_service");
            assertEquals(ServicePriority.ELITE, retrieved.priority());
            assertTrue(retrieved.needsMobileLikeBehavior());
        }
    }
    
    // =========================================================================
    // Mock ProxyStrategy for testing
    // =========================================================================
    
    static class MockProxyStrategy implements ProxyStrategy {
        
        OrderContext lastContext;
        boolean shouldThrow = false;
        
        @Override
        public ProxyLease selectProxy(OrderContext ctx) throws NoCapacityException {
            lastContext = ctx;
            
            if (shouldThrow) {
                throw new NoCapacityException(ctx.orderId(), ctx.serviceId(), "mock");
            }
            
            return ProxyLease.create(
                "mock",
                "mock-host",
                1080,
                ProxyLease.ProxyType.SOCKS5,
                ctx.targetCountry(),
                0.1,
                300,
                Map.of()
            );
        }
        
        @Override
        public List<ProxySource> getSources() {
            return List.of();
        }
        
        @Override
        public List<ProxySource> getAvailableSources() {
            return List.of();
        }
        
        @Override
        public AggregateStats getAggregateStats() {
            return new AggregateStats(1, 1, 100000, 100000, 0);
        }
    }
}
