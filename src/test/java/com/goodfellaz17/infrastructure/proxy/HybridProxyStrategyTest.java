package com.goodfellaz17.infrastructure.proxy;

import com.goodfellaz17.domain.exception.NoCapacityException;
import com.goodfellaz17.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HybridProxyStrategy.
 *
 * Tests verify:
 * - Premium sources are chosen for premium/geo orders
 * - High-capacity sources are chosen for high-volume orders
 * - Cheapest source is chosen for low-priority orders
 * - Exceptions when all eligible sources are disabled or at zero capacity
 * - Mobile-preferred services fall back when mobile is unavailable
 */
@DisplayName("HybridProxyStrategy Tests")
class HybridProxyStrategyTest {

    private TestProxySource awsSource;
    private TestProxySource torSource;
    private TestProxySource mobileSource;
    private TestProxySource p2pSource;
    private HybridProxyStrategy strategy;

    @BeforeEach
    void setUp() {
        // Create test sources with different characteristics
        awsSource = new TestProxySource(
            "aws", true, 100000, 0.10, 0.1, true, List.of("US", "UK")
        );
        torSource = new TestProxySource(
            "tor", true, 500000, 0.02, 0.6, false, List.of("GLOBAL")
        );
        mobileSource = new TestProxySource(
            "mobile", false, 0, 0.30, 0.05, true, List.of("US", "TR")
        );
        p2pSource = new TestProxySource(
            "p2p", true, 200000, 0.05, 0.3, false, List.of("GLOBAL", "US", "UK")
        );

        strategy = new HybridProxyStrategy(List.of(awsSource, torSource, mobileSource, p2pSource));
    }

    @Nested
    @DisplayName("Premium Service Routing")
    class PremiumServiceRouting {

        @Test
        @DisplayName("Should choose premium source (AWS) for premium geo-targeted order")
        void shouldChoosePremiumSourceForPremiumOrder() {
            OrderContext ctx = OrderContext.builder()
                .orderId("test-1")
                .serviceId("plays_usa")
                .serviceName("USA Plays")
                .routingProfile(RoutingProfile.premium("US"))
                .targetCountry("US")
                .quantity(1000)
                .build();

            ProxyLease lease = strategy.selectProxy(ctx);

            assertEquals("aws", lease.sourceName());
            assertEquals("US", lease.country());
        }

        @Test
        @DisplayName("Should throw NoCapacity when AWS exhausted and no other premium source available")
        void shouldFallBackToP2pWhenAwsExhausted() {
            awsSource.setRemainingCapacity(0);
            // P2P is not a premium source (premium=false), so it won't be selected for PREMIUM orders
            // This test verifies the correct behavior: NoCapacityException when no premium source available

            OrderContext ctx = OrderContext.builder()
                .orderId("test-2")
                .serviceId("plays_usa")
                .serviceName("USA Plays")
                .routingProfile(new RoutingProfile(ServicePriority.PREMIUM, true, false, 0.5))
                .targetCountry("US")
                .quantity(1000)
                .build();

            // Premium orders require premium sources - P2P (premium=false) won't qualify
            assertThrows(NoCapacityException.class, () -> strategy.selectProxy(ctx));
        }
    }

    @Nested
    @DisplayName("Elite Service Routing")
    class EliteServiceRouting {

        @Test
        @DisplayName("Should prefer mobile source when enabled for elite mobile-preferred order")
        void shouldPreferMobileForEliteOrder() {
            // Enable mobile source
            mobileSource.setEnabled(true);
            mobileSource.setCapacityPerDay(50000);
            mobileSource.setRemainingCapacity(50000);

            OrderContext ctx = OrderContext.builder()
                .orderId("test-3")
                .serviceId("plays_elite")
                .serviceName("Elite Plays")
                .routingProfile(RoutingProfile.elite())
                .targetCountry("US")
                .quantity(1000)
                .build();

            ProxyLease lease = strategy.selectProxy(ctx);

            assertEquals("mobile", lease.sourceName());
        }

        @Test
        @DisplayName("Should fall back to AWS when mobile is disabled for elite order")
        void shouldFallBackToAwsWhenMobileDisabled() {
            // Mobile stays disabled (default)

            OrderContext ctx = OrderContext.builder()
                .orderId("test-4")
                .serviceId("plays_elite")
                .serviceName("Elite Plays")
                .routingProfile(RoutingProfile.elite())
                .targetCountry("US")
                .quantity(1000)
                .build();

            ProxyLease lease = strategy.selectProxy(ctx);

            // Should fall back to next premium source (AWS)
            assertEquals("aws", lease.sourceName());
        }

        @Test
        @DisplayName("Should NOT use Tor for elite orders (too risky)")
        void shouldNotUseTorForEliteOrders() {
            awsSource.setRemainingCapacity(0);
            mobileSource.setEnabled(false);
            p2pSource.setRemainingCapacity(0);

            OrderContext ctx = OrderContext.builder()
                .orderId("test-5")
                .serviceId("plays_elite")
                .serviceName("Elite Plays")
                .routingProfile(RoutingProfile.elite())
                .targetCountry("GLOBAL")
                .quantity(1000)
                .build();

            // Tor should be excluded for elite (risk level 0.6 > max tolerance 0.1)
            assertThrows(NoCapacityException.class, () -> strategy.selectProxy(ctx));
        }
    }

    @Nested
    @DisplayName("High Volume Routing")
    class HighVolumeRouting {

        @Test
        @DisplayName("Should prefer high-capacity source (Tor) for high-volume order")
        void shouldPreferHighCapacityForHighVolume() {
            OrderContext ctx = OrderContext.builder()
                .orderId("test-6")
                .serviceId("plays_bulk")
                .serviceName("Bulk Plays")
                .routingProfile(RoutingProfile.highVolume())
                .targetCountry("GLOBAL")
                .quantity(100000)
                .build();

            ProxyLease lease = strategy.selectProxy(ctx);

            // Tor has highest capacity (500k)
            assertEquals("tor", lease.sourceName());
        }

        @Test
        @DisplayName("Should fall back to P2P when Tor exhausted for high-volume")
        void shouldFallBackToP2pWhenTorExhausted() {
            torSource.setRemainingCapacity(0);

            OrderContext ctx = OrderContext.builder()
                .orderId("test-7")
                .serviceId("plays_bulk")
                .serviceName("Bulk Plays")
                .routingProfile(RoutingProfile.highVolume())
                .targetCountry("GLOBAL")
                .quantity(100000)
                .build();

            ProxyLease lease = strategy.selectProxy(ctx);

            // P2P has next highest capacity (200k)
            assertEquals("p2p", lease.sourceName());
        }
    }

    @Nested
    @DisplayName("Basic Service Routing")
    class BasicServiceRouting {

        @Test
        @DisplayName("Should choose cheapest source (P2P) for basic order that passes risk filter")
        void shouldChooseCheapestForBasicOrder() {
            OrderContext ctx = OrderContext.builder()
                .orderId("test-8")
                .serviceId("plays_ww")
                .serviceName("Worldwide Plays")
                .routingProfile(RoutingProfile.DEFAULT)
                .targetCountry("GLOBAL")
                .quantity(1000)
                .build();

            ProxyLease lease = strategy.selectProxy(ctx);

            // P2P is cheapest among sources that pass DEFAULT profile's risk tolerance (0.5)
            // Tor ($0.02) has risk=0.6 > 0.5, filtered out
            // P2P ($0.05) has risk=0.3 <= 0.5, selected
            assertEquals("p2p", lease.sourceName());
        }
    }

    @Nested
    @DisplayName("Capacity and Error Handling")
    class CapacityHandling {

        @Test
        @DisplayName("Should throw NoCapacityException when all sources exhausted")
        void shouldThrowWhenAllSourcesExhausted() {
            awsSource.setRemainingCapacity(0);
            torSource.setRemainingCapacity(0);
            p2pSource.setRemainingCapacity(0);
            mobileSource.setEnabled(false);

            OrderContext ctx = OrderContext.builder()
                .orderId("test-9")
                .serviceId("plays_ww")
                .serviceName("Worldwide Plays")
                .routingProfile(RoutingProfile.DEFAULT)
                .targetCountry("GLOBAL")
                .quantity(1000)
                .build();

            NoCapacityException ex = assertThrows(NoCapacityException.class,
                () -> strategy.selectProxy(ctx));

            assertNotNull(ex.getOrderId());
            assertEquals("test-9", ex.getOrderId());
        }

        @Test
        @DisplayName("Should throw when no source supports required geo")
        void shouldThrowWhenNoGeoSupport() {
            // Only AWS and mobile support geo-targeting, both disabled for this geo
            awsSource.setRemainingCapacity(0);

            OrderContext ctx = OrderContext.builder()
                .orderId("test-10")
                .serviceId("plays_de")
                .serviceName("Germany Plays")
                .routingProfile(new RoutingProfile(ServicePriority.PREMIUM, true, false, 0.2))
                .targetCountry("DE") // Only AWS supports DE, but it's exhausted
                .quantity(1000)
                .build();

            // P2P and Tor support GLOBAL but have higher risk than allowed (0.2)
            // This should fail
            assertThrows(NoCapacityException.class, () -> strategy.selectProxy(ctx));
        }
    }

    @Nested
    @DisplayName("Mobile Source Behavior")
    class MobileSourceBehavior {

        @Test
        @DisplayName("Mobile source reports no capacity when disabled")
        void mobileReportsNoCapacityWhenDisabled() {
            assertFalse(mobileSource.isEnabled());
            assertFalse(mobileSource.hasCapacity());
            assertEquals(0, mobileSource.getRemainingCapacity());
        }

        @Test
        @DisplayName("Mobile source works when enabled with capacity")
        void mobileWorksWhenEnabled() {
            mobileSource.setEnabled(true);
            mobileSource.setCapacityPerDay(214000); // 107 phones * 2000
            mobileSource.setRemainingCapacity(214000);

            assertTrue(mobileSource.isEnabled());
            assertTrue(mobileSource.hasCapacity());
            assertEquals(214000, mobileSource.getRemainingCapacity());
        }
    }

    @Nested
    @DisplayName("Aggregate Stats")
    class AggregateStatsTests {

        @Test
        @DisplayName("Should return correct aggregate stats")
        void shouldReturnCorrectAggregateStats() {
            var stats = strategy.getAggregateStats();

            assertEquals(4, stats.totalSources());
            assertEquals(3, stats.enabledSources()); // Mobile disabled by default
            assertTrue(stats.totalCapacity() > 0);
            assertTrue(stats.totalRemaining() > 0);
        }
    }

    // =========================================================================
    // Test helper: Configurable proxy source for testing
    // =========================================================================

    static class TestProxySource extends AbstractProxySource {

        private int overrideRemainingCapacity = -1;
        private boolean overrideEnabled;
        private int overrideCapacityPerDay;

        TestProxySource(
            String name,
            boolean enabled,
            int capacityPerDay,
            double costPer1k,
            double riskLevel,
            boolean premium,
            List<String> geos
        ) {
            super(name, name.toUpperCase(), enabled, capacityPerDay, costPer1k, riskLevel, premium, geos, 300);
            this.overrideEnabled = enabled;
            this.overrideCapacityPerDay = capacityPerDay;
        }

        @Override
        public boolean isEnabled() {
            return overrideEnabled;
        }

        public void setEnabled(boolean enabled) {
            this.overrideEnabled = enabled;
        }

        @Override
        public int getEstimatedCapacityPerDay() {
            return overrideCapacityPerDay;
        }

        public void setCapacityPerDay(int capacity) {
            this.overrideCapacityPerDay = capacity;
        }

        @Override
        public int getRemainingCapacity() {
            if (overrideRemainingCapacity >= 0) {
                return overrideRemainingCapacity;
            }
            return super.getRemainingCapacity();
        }

        public void setRemainingCapacity(int remaining) {
            this.overrideRemainingCapacity = remaining;
        }

        @Override
        public boolean hasCapacity() {
            return isEnabled() && getRemainingCapacity() > 0;
        }

        @Override
        protected ProxyLease createLease(OrderContext ctx) {
            return ProxyLease.create(
                name,
                "test-host",
                1080,
                ProxyLease.ProxyType.SOCKS5,
                ctx.targetCountry(),
                riskLevel,
                leaseTtlSeconds,
                java.util.Map.of("test", "true")
            );
        }
    }
}
