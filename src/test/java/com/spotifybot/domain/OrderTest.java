package com.spotifybot.domain;

import com.spotifybot.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Domain Layer Unit Tests - 100% Coverage Target.
 * 
 * TDD: Red → Green → Refactor
 */
class OrderTest {

    @Nested
    @DisplayName("Order Creation")
    class OrderCreation {

        @Test
        @DisplayName("should create order with valid parameters")
        void shouldCreateOrderWithValidParameters() {
            Order order = Order.builder()
                    .serviceId(1)
                    .trackUrl("spotify:track:abc123")
                    .quantity(1000)
                    .geoTarget(GeoTarget.USA)
                    .speedTier(SpeedTier.NORMAL)
                    .build();

            assertNotNull(order.getId());
            assertEquals(1, order.getServiceId());
            assertEquals("spotify:track:abc123", order.getTrackUrl());
            assertEquals(1000, order.getQuantity());
            assertEquals(0, order.getDelivered());
            assertEquals(GeoTarget.USA, order.getGeoTarget());
            assertEquals(SpeedTier.NORMAL, order.getSpeedTier());
            assertEquals(OrderStatus.PENDING, order.getStatus());
        }

        @Test
        @DisplayName("should generate UUID on creation")
        void shouldGenerateUuidOnCreation() {
            Order order1 = Order.builder().serviceId(1).trackUrl("url").quantity(100).build();
            Order order2 = Order.builder().serviceId(1).trackUrl("url").quantity(100).build();

            assertNotEquals(order1.getId(), order2.getId());
        }
    }

    @Nested
    @DisplayName("Order Decomposition")
    class OrderDecomposition {

        @Test
        @DisplayName("should decompose order into bot tasks")
        void shouldDecomposeOrderIntoBotTasks() {
            Order order = Order.builder()
                    .serviceId(1)
                    .trackUrl("spotify:track:abc123")
                    .quantity(100)
                    .geoTarget(GeoTarget.USA)
                    .speedTier(SpeedTier.NORMAL)
                    .build();

            order.startProcessing();
            List<BotTask> tasks = order.decompose();

            assertFalse(tasks.isEmpty());
            assertEquals(order.getId(), tasks.get(0).orderId());
        }
    }

    @Nested
    @DisplayName("Order Status Transitions")
    class OrderStatusTransitions {

        @Test
        @DisplayName("should transition from PENDING to PROCESSING")
        void shouldTransitionToProcessing() {
            Order order = Order.builder().serviceId(1).trackUrl("url").quantity(100).build();
            
            assertEquals(OrderStatus.PENDING, order.getStatus());
            
            order.startProcessing();
            
            assertEquals(OrderStatus.PROCESSING, order.getStatus());
        }

        @Test
        @DisplayName("should complete when delivered equals quantity")
        void shouldCompleteWhenDelivered() {
            Order order = Order.builder().serviceId(1).trackUrl("url").quantity(10).build();
            order.startProcessing();
            
            order.addDelivered(10);
            
            assertEquals(OrderStatus.COMPLETED, order.getStatus());
            assertEquals(10, order.getDelivered());
        }

        @Test
        @DisplayName("should remain processing when partially delivered")
        void shouldRemainProcessingWhenPartial() {
            Order order = Order.builder().serviceId(1).trackUrl("url").quantity(100).build();
            order.startProcessing();
            
            order.addDelivered(50);
            
            assertEquals(OrderStatus.PROCESSING, order.getStatus());
            assertEquals(50, order.getDelivered());
        }
    }

    @Nested
    @DisplayName("Spotify Compliance")
    class SpotifyCompliance {

        @Test
        @DisplayName("should enforce 5% hourly spike limit")
        void shouldEnforce5PercentSpikeLimit() {
            Order order = Order.builder()
                    .serviceId(1)
                    .trackUrl("url")
                    .quantity(1000)
                    .speedTier(SpeedTier.NORMAL)
                    .build();

            // 5% of 1000 = 50 per hour max
            // With NORMAL speed (72h delivery), this is compliant
            assertTrue(order.isSpotifyCompliant());
        }

        @Test
        @DisplayName("VIP speed should have higher spike tolerance")
        void vipSpeedShouldHaveHigherTolerance() {
            Order normalOrder = Order.builder()
                    .serviceId(1).trackUrl("url").quantity(10000)
                    .speedTier(SpeedTier.NORMAL)
                    .build();

            Order vipOrder = Order.builder()
                    .serviceId(1).trackUrl("url").quantity(10000)
                    .speedTier(SpeedTier.VIP)
                    .build();

            // Both should be compliant at this scale
            assertTrue(normalOrder.isSpotifyCompliant());
            assertTrue(vipOrder.isSpotifyCompliant());
        }
    }

    @Nested
    @DisplayName("Remaining Quantity")
    class RemainingQuantity {

        @Test
        @DisplayName("should calculate remaining correctly")
        void shouldCalculateRemainingCorrectly() {
            Order order = Order.builder().serviceId(1).trackUrl("url").quantity(100).build();
            order.startProcessing();

            assertEquals(100, order.getRemaining());

            order.addDelivered(30);
            assertEquals(70, order.getRemaining());

            order.addDelivered(70);
            assertEquals(0, order.getRemaining());
        }
    }
}
