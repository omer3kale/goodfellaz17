package com.goodfellaz17.presentation.api.v2;

import com.goodfellaz17.application.dto.generated.CreateOrderRequest;
import com.goodfellaz17.application.dto.generated.OrderResponse;
import com.goodfellaz17.domain.model.generated.*;
import com.goodfellaz17.infrastructure.persistence.generated.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OrderControllerV2 using Testcontainers.
 *
 * Tests the complete order creation flow:
 * 1. API key authentication
 * 2. Balance validation
 * 3. Order and transaction creation
 * 4. Balance deduction
 *
 * Requires Docker. Skip with SKIP_DOCKER_TESTS=true
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.springframework.test.context.ActiveProfiles("test")
@DisabledIfEnvironmentVariable(named = "SKIP_DOCKER_TESTS", matches = "true")
@SuppressWarnings("null") // Reactive .block() null handling is intentional in tests
class OrderControllerV2IntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("goodfellaz17_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("test-schema.sql");
        postgres.start();
    }

    @DynamicPropertySource
    static void r2dbcProperties(DynamicPropertyRegistry registry) {
        // Build R2DBC URL from JDBC container (container already started in static block)
        registry.add("spring.r2dbc.url", () -> String.format(
            "r2dbc:postgresql://%s:%d/%s",
            postgres.getHost(),
            postgres.getMappedPort(5432),
            postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("proxy.hybrid.enabled", () -> "false");
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private GeneratedUserRepository userRepository;

    @Autowired
    private GeneratedServiceRepository serviceRepository;

    @Autowired
    private GeneratedOrderRepository orderRepository;

    @Autowired
    private GeneratedBalanceTransactionRepository transactionRepository;

    // Test data
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("100.00");
    private static final int ORDER_QUANTITY = 15000;

    // Shared test data - reset each test via @BeforeEach
    private UUID testUserId;
    private UUID testServiceId;
    private String testApiKey;

    @BeforeEach
    void setupTestData() {
        // Generate unique API key per test to ensure isolation
        testApiKey = "test-api-key-" + UUID.randomUUID();

        // Clean prior test data
        transactionRepository.deleteAll().block(Duration.ofSeconds(5));
        orderRepository.deleteAll().block(Duration.ofSeconds(5));
        serviceRepository.deleteAll().block(Duration.ofSeconds(5));
        userRepository.deleteAll().block(Duration.ofSeconds(5));

        // Seed fresh test data for this test
        seedTestData();
    }

    private void seedTestData() {
        // Create test user with €100 balance
        UserEntity testUser = UserEntity.builder()
            .email("test-" + UUID.randomUUID() + "@goodfellaz17.com")
            .passwordHash("$2a$10$dummyhashfortest")
            .tier(UserTier.CONSUMER.name())
            .balance(INITIAL_BALANCE)
            .apiKey(testApiKey)
            .status(UserStatus.ACTIVE.name())
            .emailVerified(true)
            .build();

        testUserId = userRepository.save(testUser)
            .map(UserEntity::getId)
            .block(Duration.ofSeconds(5));

        // Create test service (Spotify Plays)
        ServiceEntity testService = ServiceEntity.builder()
            .name("spotify_plays_" + UUID.randomUUID())
            .displayName("Spotify Premium Plays")
            .serviceType(ServiceType.PLAYS.name())
            .description("High-quality Spotify plays")
            .costPer1k(new BigDecimal("2.50"))      // €2.50/1k for CONSUMER
            .resellerCostPer1k(new BigDecimal("2.00"))
            .agencyCostPer1k(new BigDecimal("1.50"))
            .minQuantity(100)
            .maxQuantity(1000000)
            .estimatedDaysMin(1)
            .estimatedDaysMax(7)
            .geoProfiles("[\"WORLDWIDE\", \"US_FOCUSED\", \"EU_FOCUSED\"]")
            .isActive(true)
            .sortOrder(1)
            .build();

        testServiceId = serviceRepository.save(testService)
            .map(ServiceEntity::getId)
            .block(Duration.ofSeconds(5));

        assertThat(testUserId).isNotNull();
        assertThat(testServiceId).isNotNull();
    }

    /**
     * Helper: Create an order and return the order ID.
     * Used by dependent tests to create their own order in isolation.
     */
    private UUID createOrderForTest() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .serviceId(testServiceId)
            .quantity(ORDER_QUANTITY)
            .targetUrl("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")
            .geoProfile("WORLDWIDE")
            .speedMultiplier(1.0)
            .build();

        OrderResponse response = webTestClient
            .post()
            .uri("/api/v2/orders")
            .header("X-Api-Key", testApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(OrderResponse.class)
            .returnResult()
            .getResponseBody();

        return response != null ? response.id() : null;
    }

    // ==================== Test Cases ====================

    @Test
    @Order(1)
    @DisplayName("POST /api/v2/orders - Should create order successfully with valid request")
    void shouldCreateOrderSuccessfully() {
        // Given: 15k plays at €2.50/1k = €37.50 total
        CreateOrderRequest request = CreateOrderRequest.builder()
            .serviceId(testServiceId)
            .quantity(ORDER_QUANTITY)
            .targetUrl("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")
            .geoProfile("WORLDWIDE")
            .speedMultiplier(1.0)
            .build();

        BigDecimal expectedCost = new BigDecimal("37.50"); // 15 * 2.50

        // When
        webTestClient
            .post()
            .uri("/api/v2/orders")
            .header("X-Api-Key", testApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()

            // Then
            .expectStatus().isCreated()
            .expectBody(OrderResponse.class)
            .value(order -> {
                assertThat(order.id()).isNotNull();
                assertThat(order.serviceId()).isEqualTo(testServiceId);
                assertThat(order.serviceName()).isEqualTo("Spotify Premium Plays");
                assertThat(order.quantity()).isEqualTo(ORDER_QUANTITY);
                assertThat(order.delivered()).isEqualTo(0);
                assertThat(order.status()).isEqualTo("PENDING");
                assertThat(order.cost()).isEqualByComparingTo(expectedCost);
                assertThat(order.progressPercent()).isEqualTo(0.0);
                assertThat(order.isTerminal()).isFalse();
            });
    }

    @Test
    @Order(2)
    @DisplayName("Verify order row created in database")
    void shouldVerifyOrderInDatabase() {
        // Create order in THIS test (independent)
        UUID createdOrderId = createOrderForTest();
        assertThat(createdOrderId).isNotNull();

        StepVerifier.create(orderRepository.findById(createdOrderId))
            .assertNext(order -> {
                assertThat(order.getUserId()).isEqualTo(testUserId);
                assertThat(order.getServiceId()).isEqualTo(testServiceId);
                assertThat(order.getQuantity()).isEqualTo(ORDER_QUANTITY);
                assertThat(order.getDelivered()).isEqualTo(0);
                assertThat(order.getRemains()).isEqualTo(ORDER_QUANTITY);
                assertThat(order.getStatus()).isEqualTo("PENDING");
                assertThat(order.getCost()).isEqualByComparingTo(new BigDecimal("37.50"));
            })
            .verifyComplete();
    }

    @Test
    @Order(3)
    @DisplayName("Verify balance transaction created in database")
    void shouldVerifyBalanceTransactionInDatabase() {
        // Create order in THIS test (independent)
        UUID createdOrderId = createOrderForTest();
        assertThat(createdOrderId).isNotNull();

        StepVerifier.create(transactionRepository.findByOrderId(createdOrderId).collectList())
            .assertNext(transactions -> {
                assertThat(transactions).hasSize(1);
                BalanceTransactionEntity tx = transactions.get(0);

                assertThat(tx.getUserId()).isEqualTo(testUserId);
                assertThat(tx.getOrderId()).isEqualTo(createdOrderId);
                assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("-37.50")); // Debit
                assertThat(tx.getBalanceBefore()).isEqualByComparingTo(INITIAL_BALANCE);
                assertThat(tx.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("62.50"));
                assertThat(tx.getType()).isEqualTo("DEBIT");
            })
            .verifyComplete();
    }

    @Test
    @Order(4)
    @DisplayName("Verify user balance deducted correctly")
    void shouldVerifyUserBalanceDeducted() {
        // Create order in THIS test (independent)
        createOrderForTest();

        BigDecimal expectedBalance = INITIAL_BALANCE.subtract(new BigDecimal("37.50")); // €62.50

        StepVerifier.create(userRepository.findById(testUserId))
            .assertNext(user -> {
                assertThat(user.getBalance()).isEqualByComparingTo(expectedBalance);
            })
            .verifyComplete();
    }

    @Test
    @Order(5)
    @DisplayName("GET /api/v2/orders/{id} - Should retrieve order by ID")
    void shouldGetOrderById() {
        // Create order in THIS test (independent)
        UUID createdOrderId = createOrderForTest();
        assertThat(createdOrderId).isNotNull();

        webTestClient
            .get()
            .uri("/api/v2/orders/{id}", createdOrderId)
            .header("X-Api-Key", testApiKey)
            .exchange()
            .expectStatus().isOk()
            .expectBody(OrderResponse.class)
            .value(order -> {
                assertThat(order.id()).isEqualTo(createdOrderId);
                assertThat(order.quantity()).isEqualTo(ORDER_QUANTITY);
            });
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/v2/orders/balance - Should return correct balance after order")
    void shouldReturnCorrectBalance() {
        // Create order first to change balance
        createOrderForTest();

        webTestClient
            .get()
            .uri("/api/v2/orders/balance")
            .header("X-Api-Key", testApiKey)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.balance").isEqualTo(62.50)
            .jsonPath("$.tier").isEqualTo("CONSUMER");
    }

    // ==================== Error Cases ====================

    @Test
    @Order(10)
    @DisplayName("POST /api/v2/orders - Should return 401 when no API key")
    void shouldReturn401WhenNoApiKey() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .serviceId(testServiceId)
            .quantity(1000)
            .targetUrl("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")
            .build();

        webTestClient
            .post()
            .uri("/api/v2/orders")
            // No X-Api-Key header
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @Order(11)
    @DisplayName("POST /api/v2/orders - Should return 401 when invalid API key")
    void shouldReturn401WhenInvalidApiKey() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .serviceId(testServiceId)
            .quantity(1000)
            .targetUrl("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")
            .build();

        webTestClient
            .post()
            .uri("/api/v2/orders")
            .header("X-Api-Key", "invalid-api-key-does-not-exist")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @Order(12)
    @DisplayName("POST /api/v2/orders - Should return 402 when insufficient balance")
    void shouldReturn402WhenInsufficientBalance() {
        // First, deplete balance by creating an order (€100 - €37.50 = €62.50)
        createOrderForTest();

        // Given: User has €62.50 remaining, trying to order 50k plays = €125
        CreateOrderRequest request = CreateOrderRequest.builder()
            .serviceId(testServiceId)
            .quantity(50000) // 50k * €2.50/1k = €125 > €62.50 available
            .targetUrl("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")
            .build();

        webTestClient
            .post()
            .uri("/api/v2/orders")
            .header("X-Api-Key", testApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isEqualTo(402); // Payment Required
    }

    @Test
    @Order(13)
    @DisplayName("POST /api/v2/orders - Should return 404 when service not found")
    void shouldReturn404WhenServiceNotFound() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .serviceId(UUID.randomUUID()) // Non-existent service
            .quantity(1000)
            .targetUrl("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")
            .build();

        webTestClient
            .post()
            .uri("/api/v2/orders")
            .header("X-Api-Key", testApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @Order(14)
    @DisplayName("POST /api/v2/orders - Should return 422 when service is inactive")
    void shouldReturn422WhenServiceInactive() {
        // Create an inactive service
        ServiceEntity inactiveService = ServiceEntity.builder()
            .name("inactive_service")
            .displayName("Inactive Service")
            .serviceType(ServiceType.PLAYS.name())
            .costPer1k(new BigDecimal("1.00"))
            .isActive(false) // Inactive
            .build();

        UUID inactiveServiceId = serviceRepository.save(inactiveService)
            .map(ServiceEntity::getId)
            .block(Duration.ofSeconds(5));

        CreateOrderRequest request = CreateOrderRequest.builder()
            .serviceId(inactiveServiceId)
            .quantity(1000)
            .targetUrl("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")
            .build();

        webTestClient
            .post()
            .uri("/api/v2/orders")
            .header("X-Api-Key", testApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isEqualTo(422); // Unprocessable Entity
    }

    @Test
    @Order(15)
    @DisplayName("POST /api/v2/orders - Should return 400 when quantity below minimum")
    void shouldReturn400WhenQuantityBelowMinimum() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .serviceId(testServiceId)
            .quantity(10) // Below minimum of 100
            .targetUrl("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")
            .build();

        webTestClient
            .post()
            .uri("/api/v2/orders")
            .header("X-Api-Key", testApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @Order(20)
    @DisplayName("GET /api/v2/orders - Should list user orders with pagination")
    void shouldListOrdersWithPagination() {
        // Create order first so there's something to list
        createOrderForTest();

        webTestClient
            .get()
            .uri("/api/v2/orders?page=0&size=10")
            .header("X-Api-Key", testApiKey)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").value(length -> assertThat((Integer) length).isGreaterThanOrEqualTo(1));
    }

    @AfterAll
    void cleanup() {
        // Clean up test data (defensive - @BeforeEach already cleans)
        transactionRepository.deleteAll().block(Duration.ofSeconds(5));
        orderRepository.deleteAll().block(Duration.ofSeconds(5));
        serviceRepository.deleteAll().block(Duration.ofSeconds(5));
        userRepository.deleteAll().block(Duration.ofSeconds(5));
    }
}
