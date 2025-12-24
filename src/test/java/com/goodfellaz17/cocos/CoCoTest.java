package com.goodfellaz17.cocos;

import com.goodfellaz17.cocos.order.OrderCoCoChecker;
import com.goodfellaz17.cocos.order.OrderContext;
import com.goodfellaz17.symboltable.ApiKeySymbol;
import com.goodfellaz17.symboltable.ServiceSymbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CoCo Tests - MontiCore Manual Ch.10 validation.
 * 
 * Tests:
 * - OrderQuantityValidCoCo
 * - SpotifyDripRateCoCo
 * - ApiKeyBalanceCoCo
 * - TrackUrlValidCoCo
 * - CoCoCollector
 */
@DisplayName("CoCo Tests (MontiCore Ch.10)")
class CoCoTest {
    
    private OrderCoCoChecker cocoChecker;
    private ServiceSymbol spotifyPlays;
    private ApiKeySymbol apiKey;
    
    @BeforeEach
    void setUp() {
        cocoChecker = new OrderCoCoChecker();
        
        // Create test fixtures
        spotifyPlays = new ServiceSymbol(1, "Spotify Plays WW", 
                new BigDecimal("0.50"), 100, 10_000_000, "spotify");
        
        apiKey = new ApiKeySymbol("test-api-key-12345", UUID.randomUUID(), new BigDecimal("100.00"));
    }
    
    @Nested
    @DisplayName("OrderQuantityValidCoCo (0xGFL01)")
    class OrderQuantityTests {
        
        @Test
        @DisplayName("Should pass for valid quantity")
        void shouldPassForValidQuantity() {
            OrderContext context = OrderContext.builder()
                    .apiKey(apiKey)
                    .service(spotifyPlays)
                    .trackUrl("https://open.spotify.com/track/abc123xyz456789012345")
                    .quantity(1000)
                    .deliveryHours(24)
                    .build();
            
            assertDoesNotThrow(() -> cocoChecker.checkAll(context));
        }
        
        @Test
        @DisplayName("Should fail for quantity below minimum")
        void shouldFailForQuantityBelowMinimum() {
            OrderContext context = OrderContext.builder()
                    .apiKey(apiKey)
                    .service(spotifyPlays)
                    .trackUrl("https://open.spotify.com/track/abc123xyz456789012345")
                    .quantity(10)  // Below 100 minimum
                    .deliveryHours(24)
                    .build();
            
            CoCoViolationException e = assertThrows(CoCoViolationException.class,
                    () -> cocoChecker.checkAll(context));
            
            assertEquals("0xGFL01", e.getErrorCode());
            assertTrue(e.getMessage().contains("below service minimum"));
        }
        
        @Test
        @DisplayName("Should fail for quantity above maximum")
        void shouldFailForQuantityAboveMaximum() {
            OrderContext context = OrderContext.builder()
                    .apiKey(apiKey)
                    .service(spotifyPlays)
                    .trackUrl("https://open.spotify.com/track/abc123xyz456789012345")
                    .quantity(100_000_000)  // Above 10M maximum
                    .deliveryHours(24)
                    .build();
            
            CoCoViolationException e = assertThrows(CoCoViolationException.class,
                    () -> cocoChecker.checkAll(context));
            
            assertEquals("0xGFL01", e.getErrorCode());
            assertTrue(e.getMessage().contains("exceeds service maximum"));
        }
    }
    
    @Nested
    @DisplayName("ApiKeyBalanceCoCo (0xGFL03)")
    class BalanceTests {
        
        @Test
        @DisplayName("Should pass for sufficient balance")
        void shouldPassForSufficientBalance() {
            // $100 balance, $0.50 cost (1000 * $0.50/1000)
            // Use smaller quantity to pass drip rate check
            OrderContext context = OrderContext.builder()
                    .apiKey(apiKey)
                    .service(spotifyPlays)
                    .trackUrl("https://open.spotify.com/track/abc123xyz456789012345")
                    .quantity(1000)
                    .deliveryHours(24)
                    .existingPlays(100000)  // High baseline for safe drip
                    .build();
            
            assertDoesNotThrow(() -> cocoChecker.checkAll(context));
        }
        
        @Test
        @DisplayName("Should fail for insufficient balance")
        void shouldFailForInsufficientBalance() {
            // $100 balance, $500 cost (1M * $0.50/1000)
            OrderContext context = OrderContext.builder()
                    .apiKey(apiKey)
                    .service(spotifyPlays)
                    .trackUrl("https://open.spotify.com/track/abc123xyz456789012345")
                    .quantity(1_000_000)
                    .deliveryHours(24)
                    .build();
            
            CoCoViolationException e = assertThrows(CoCoViolationException.class,
                    () -> cocoChecker.checkAll(context));
            
            assertEquals("0xGFL03", e.getErrorCode());
            assertTrue(e.getMessage().contains("Insufficient balance"));
        }
    }
    
    @Nested
    @DisplayName("TrackUrlValidCoCo (0xGFL04)")
    class TrackUrlTests {
        
        @Test
        @DisplayName("Should pass for valid Spotify track URL")
        void shouldPassForValidTrackUrl() {
            OrderContext context = OrderContext.builder()
                    .apiKey(apiKey)
                    .service(spotifyPlays)
                    .trackUrl("https://open.spotify.com/track/abc123xyz456789012345")
                    .quantity(1000)
                    .deliveryHours(24)
                    .build();
            
            assertDoesNotThrow(() -> cocoChecker.checkAll(context));
        }
        
        @Test
        @DisplayName("Should pass for valid Spotify album URL")
        void shouldPassForValidAlbumUrl() {
            OrderContext context = OrderContext.builder()
                    .apiKey(apiKey)
                    .service(spotifyPlays)
                    .trackUrl("https://open.spotify.com/album/abc123xyz456789012345")
                    .quantity(1000)
                    .deliveryHours(24)
                    .build();
            
            assertDoesNotThrow(() -> cocoChecker.checkAll(context));
        }
        
        @Test
        @DisplayName("Should pass for valid Spotify playlist URL")
        void shouldPassForValidPlaylistUrl() {
            OrderContext context = OrderContext.builder()
                    .apiKey(apiKey)
                    .service(spotifyPlays)
                    .trackUrl("https://open.spotify.com/playlist/abc123xyz456789012345")
                    .quantity(1000)
                    .deliveryHours(24)
                    .build();
            
            assertDoesNotThrow(() -> cocoChecker.checkAll(context));
        }
        
        @Test
        @DisplayName("Should fail for invalid URL")
        void shouldFailForInvalidUrl() {
            OrderContext context = OrderContext.builder()
                    .apiKey(apiKey)
                    .service(spotifyPlays)
                    .trackUrl("https://youtube.com/watch?v=invalid")
                    .quantity(1000)
                    .deliveryHours(24)
                    .build();
            
            CoCoViolationException e = assertThrows(CoCoViolationException.class,
                    () -> cocoChecker.checkAll(context));
            
            assertEquals("0xGFL04", e.getErrorCode());
            assertTrue(e.getMessage().contains("Invalid Spotify URL"));
        }
        
        @Test
        @DisplayName("Should fail for empty URL")
        void shouldFailForEmptyUrl() {
            OrderContext context = OrderContext.builder()
                    .apiKey(apiKey)
                    .service(spotifyPlays)
                    .trackUrl("")
                    .quantity(1000)
                    .deliveryHours(24)
                    .build();
            
            CoCoViolationException e = assertThrows(CoCoViolationException.class,
                    () -> cocoChecker.checkAll(context));
            
            assertEquals("0xGFL04", e.getErrorCode());
            assertTrue(e.getMessage().contains("required"));
        }
    }
    
    @Nested
    @DisplayName("SpotifyDripRateCoCo (0xGFL02)")
    class DripRateTests {
        
        @Test
        @DisplayName("Should pass for safe drip rate")
        void shouldPassForSafeDripRate() {
            // 1000 plays over 24 hours = ~42/hour, safe for baseline 1000
            OrderContext context = OrderContext.builder()
                    .apiKey(apiKey)
                    .service(spotifyPlays)
                    .trackUrl("https://open.spotify.com/track/abc123xyz456789012345")
                    .quantity(1000)
                    .deliveryHours(24)
                    .existingPlays(1000)
                    .build();
            
            assertDoesNotThrow(() -> cocoChecker.checkAll(context));
        }
        
        @Test
        @DisplayName("Should fail for dangerous spike")
        void shouldFailForDangerousSpike() {
            // 10000 plays over 1 hour = 10000/hour, dangerous spike
            OrderContext context = OrderContext.builder()
                    .apiKey(apiKey)
                    .service(spotifyPlays)
                    .trackUrl("https://open.spotify.com/track/abc123xyz456789012345")
                    .quantity(10000)
                    .deliveryHours(1)
                    .existingPlays(1000)
                    .build();
            
            CoCoViolationException e = assertThrows(CoCoViolationException.class,
                    () -> cocoChecker.checkAll(context));
            
            assertEquals("0xGFL02", e.getErrorCode());
            assertTrue(e.getMessage().contains("spike"));
        }
    }
    
    @Nested
    @DisplayName("CoCoCollector")
    class CollectorTests {
        
        @Test
        @DisplayName("Should collect multiple errors")
        void shouldCollectMultipleErrors() {
            OrderContext context = OrderContext.builder()
                    .apiKey(apiKey)
                    .service(spotifyPlays)
                    .trackUrl("invalid-url")
                    .quantity(10)  // Below min
                    .deliveryHours(24)
                    .build();
            
            cocoChecker.checkAllCollecting(context);
            
            assertTrue(CoCoCollector.hasErrors());
            assertTrue(CoCoCollector.getErrorCount() >= 1);
            
            String summary = CoCoCollector.getSummary();
            assertNotNull(summary);
            assertTrue(summary.contains("violations"));
            
            CoCoCollector.clear();
        }
        
        @Test
        @DisplayName("Should clear errors")
        void shouldClearErrors() {
            CoCoCollector.addError("0xTEST", "Test error");
            assertTrue(CoCoCollector.hasErrors());
            
            CoCoCollector.clear();
            assertFalse(CoCoCollector.hasErrors());
        }
    }
    
    @Nested
    @DisplayName("OrderContext Builder")
    class ContextBuilderTests {
        
        @Test
        @DisplayName("Should create standard context")
        void shouldCreateStandardContext() {
            OrderContext context = OrderContext.standard(apiKey, spotifyPlays, 
                    "https://open.spotify.com/track/test123", 1000);
            
            assertEquals(24, context.deliveryHours());
            assertEquals(0, context.existingPlays());
        }
        
        @Test
        @DisplayName("Should create VIP context")
        void shouldCreateVipContext() {
            OrderContext context = OrderContext.vip(apiKey, spotifyPlays, 
                    "https://open.spotify.com/track/test123", 1000);
            
            assertEquals(6, context.deliveryHours());
        }
        
        @Test
        @DisplayName("Should create drip context")
        void shouldCreateDripContext() {
            OrderContext context = OrderContext.drip(apiKey, spotifyPlays, 
                    "https://open.spotify.com/track/test123", 10000, 72);
            
            assertEquals(72, context.deliveryHours());
        }
    }
}
