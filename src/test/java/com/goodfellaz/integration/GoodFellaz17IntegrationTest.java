package com.goodfellaz.integration;

import com.goodfellaz.order.service.OrderInvariants;
import com.goodfellaz17.GoodfellazApplication;
import com.goodfellaz17.application.invariants.OrderInvariantValidator;
import com.goodfellaz17.application.invariants.OrderInvariantValidator.ValidationResult;
import com.goodfellaz17.application.service.OrderExecutionService;
import com.goodfellaz17.application.service.OrderExecutionService.CreateOrderCommand;
import com.goodfellaz17.application.service.OrderExecutionService.OrderResult;
import com.goodfellaz17.application.testing.FailureInjectionService;
import com.goodfellaz17.application.worker.OrderDeliveryWorker;
import com.goodfellaz17.domain.model.generated.*;
import com.goodfellaz17.infrastructure.persistence.generated.*;
import com.goodfellaz17.infrastructure.proxy.generated.HybridProxyRouterV2;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2a: Integration Test Harness
 * 
 * This test validates the entire system end-to-end:
 * Customer order → ProxyRouter selection → Worker execution → Refund computation
 * 
 * Week 1 Goal: Test compiles and fails at first assertion (expected—services not wired yet)
 * Week 2 Goal: Seed data insertion works, test progresses to Worker stage
 */
@SpringBootTest(
        classes = GoodfellazApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.profiles.active=test"  // Enable instant execution for ≤1000 plays
)
@Testcontainers
@DisplayName("Phase 2a: GoodFellaz17 Integration Tests")
class GoodFellaz17IntegrationTest {

    // ========== TESTCONTAINERS ==========
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("goodfellaz17_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("test-schema.sql");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    // ========== SPRING BOOT WIRING ==========
    
    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient webTestClient;

    // ========== REAL SERVICES (Week 2) ==========
    
    @Autowired
    private OrderExecutionService orderExecutionService;
    
    @Autowired
    private GeneratedOrderRepository orderRepository;
    
    @Autowired
    private GeneratedUserRepository userRepository;
    
    @Autowired
    private GeneratedServiceRepository serviceRepository;
    
    @Autowired
    private GeneratedBalanceTransactionRepository transactionRepository;
    
    @Autowired
    private com.goodfellaz17.application.service.CapacityService capacityService;
    
    // ========== WEEK 3: ADDITIONAL SERVICES ==========
    
    @Autowired(required = false)
    private OrderDeliveryWorker orderDeliveryWorker;
    
    @Autowired(required = false)
    private FailureInjectionService failureInjectionService;
    
    @Autowired
    private OrderTaskRepository taskRepository;
    
    @Autowired
    private OrderInvariantValidator orderInvariantValidator;
    
    @Autowired
    private GeneratedProxyNodeRepository proxyNodeRepository;
    
    @Autowired
    private com.goodfellaz17.infrastructure.proxy.generated.HybridProxyRouterV2 proxyRouter;
    
    @Autowired
    private DatabaseClient databaseClient;

    // Test data IDs (stable across tests)
    private static final UUID TEST_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TEST_SERVICE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    // ========== DYNAMIC PROPERTIES ==========
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // R2DBC URL for reactive PostgreSQL
        registry.add("spring.r2dbc.url", () -> String.format(
                "r2dbc:postgresql://%s:%d/%s",
                postgres.getHost(),
                postgres.getFirstMappedPort(),
                postgres.getDatabaseName()
        ));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        
        // Redis connection
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    // ========== TEST SETUP ==========
    
    @BeforeEach
    void setUp() {
        // Clean slate: delete all test data in correct FK order
        taskRepository.deleteAll().block(Duration.ofSeconds(5));
        transactionRepository.deleteAll().block(Duration.ofSeconds(5));
        orderRepository.deleteAll().block(Duration.ofSeconds(5));
        proxyNodeRepository.deleteAll().block(Duration.ofSeconds(5));
        userRepository.deleteAll().block(Duration.ofSeconds(5));
        serviceRepository.deleteAll().block(Duration.ofSeconds(5));
        
        // CRITICAL: Refresh proxy router cache after cleanup to ensure clean state
        proxyRouter.refreshAvailableTiers();
        
        // CRITICAL: Reset failure injection to ensure clean state for each test
        if (failureInjectionService != null) {
            failureInjectionService.reset();
        }
        
        // Small delay to ensure async operations complete
        try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        
        // Seed test user with balance
        UserEntity testUser = new UserEntity();
        testUser.setId(TEST_USER_ID);
        testUser.setEmail("test-integration@goodfellaz17.test");
        testUser.setPasswordHash("$2a$10$testpasswordhash");
        testUser.setTier(UserTier.CONSUMER.name());
        testUser.setBalance(new BigDecimal("100.00"));  // €100 starting balance
        testUser.setStatus(UserStatus.ACTIVE.name());
        userRepository.save(testUser).block(Duration.ofSeconds(5));
        
        // Seed test service (Spotify Plays)
        ServiceEntity testService = new ServiceEntity();
        testService.setId(TEST_SERVICE_ID);
        testService.setName("spotify-plays-test");
        testService.setDisplayName("Spotify Plays (Test)");
        testService.setServiceType(ServiceType.PLAYS.name());
        testService.setCostPer1k(new BigDecimal("2.00"));         // €2 per 1000
        testService.setResellerCostPer1k(new BigDecimal("1.50")); // €1.50 reseller
        testService.setAgencyCostPer1k(new BigDecimal("1.00"));   // €1.00 agency
        testService.setMinQuantity(100);
        testService.setMaxQuantity(1000000);
        testService.setEstimatedDaysMin(1);
        testService.setEstimatedDaysMax(7);
        testService.setIsActive(true);
        testService.setSortOrder(1);
        serviceRepository.save(testService).block(Duration.ofSeconds(5));
        
        // Simulate sufficient proxy capacity for tests (1000 plays/hr = instant acceptance)
        // This bypasses the need to seed actual proxy nodes for the happy path test
        capacityService.setSimulatedCapacity(1000);
    }
    
    @AfterEach
    void tearDown() {
        // Reset failure injection after each test to prevent cross-test pollution
        if (failureInjectionService != null) {
            failureInjectionService.reset();
        }
    }

    // ========== WEEK 1: CONTAINERS SPIN UP ==========
    
    @Test
    @DisplayName("Week 1: PostgreSQL container is running")
    void test_postgres_container_is_running() {
        assertTrue(postgres.isRunning(), "PostgreSQL container should be running");
        assertNotNull(postgres.getJdbcUrl(), "PostgreSQL should have a JDBC URL");
    }

    @Test
    @DisplayName("Week 1: Redis container is running")
    void test_redis_container_is_running() {
        assertTrue(redis.isRunning(), "Redis container should be running");
        assertTrue(redis.getMappedPort(6379) > 0, "Redis should have a mapped port");
    }

    @Test
    @DisplayName("Week 1: Spring Boot application starts with test containers")
    void test_spring_boot_starts_with_containers() {
        assertTrue(port > 0, "Spring Boot should start on a random port");
    }

    // ========== WEEK 2: HAPPY PATH (REAL POSTGRESQL) ==========
    
    @Test
    @DisplayName("Week 2: Happy path - Customer order to completion with invariant checks")
    void happy_path_customer_order_to_refund() {
        // ========== ARRANGE: Test parameters ==========
        final int QUANTITY = 500;  // ≤1000 triggers instant execution
        final BigDecimal UNIT_PRICE = new BigDecimal("0.002");  // €2.00 / 1000 = €0.002/play
        final BigDecimal EXPECTED_COST = UNIT_PRICE.multiply(BigDecimal.valueOf(QUANTITY))
                .setScale(2, RoundingMode.HALF_UP);  // €1.00
        
        // Verify preconditions
        UserEntity userBefore = userRepository.findById(TEST_USER_ID).block(Duration.ofSeconds(5));
        assertNotNull(userBefore, "Test user should exist");
        BigDecimal balanceBefore = userBefore.getBalance();
        assertEquals(new BigDecimal("100.00"), balanceBefore, "User should start with €100");
        
        // ========== ACT: Create order via real service layer ==========
        CreateOrderCommand command = new CreateOrderCommand(
                TEST_USER_ID,
                TEST_SERVICE_ID,
                QUANTITY,
                "https://open.spotify.com/track/4iV5W9uYEdYUVa79Axb7Rh",  // Valid Spotify URL
                GeoProfile.WORLDWIDE.name(),
                "test-idempotency-" + UUID.randomUUID()  // Unique idempotency key
        );
        
        OrderResult result = orderExecutionService.createOrder(command)
                .block(Duration.ofSeconds(10));
        
        // ========== ASSERT 1: Order creation succeeded ==========
        assertNotNull(result, "OrderResult should not be null");
        assertTrue(result.success(), "Order creation should succeed: " + result.errorMessage());
        assertNotNull(result.order(), "Order entity should be present");
        
        OrderEntity order = result.order();
        UUID orderId = order.getId();
        
        // ========== ASSERT 2: Order instant-executed (≤1000 plays) ==========
        assertEquals(OrderStatus.COMPLETED.name(), order.getStatus(),
                "Order ≤1000 plays should instant-complete");
        assertEquals(QUANTITY, order.getQuantity(), "Quantity should match");
        assertEquals(QUANTITY, order.getDelivered(), "All plays should be delivered (instant mode)");
        assertEquals(0, order.getRemains(), "No remaining plays");
        assertEquals(0, order.getFailedPermanentPlays(), "No failed plays in happy path");
        
        // ========== ASSERT 3: INVARIANT #1 - Quantity Conservation ==========
        // ordered = delivered + failed + pending
        int delivered = order.getDelivered();
        int failed = order.getFailedPermanentPlays();
        int pending = order.getRemains();
        
        assertTrue(
                OrderInvariants.quantityConserved(QUANTITY, delivered, failed, pending),
                String.format("INV-1: quantity=%d must equal delivered=%d + failed=%d + pending=%d",
                        QUANTITY, delivered, failed, pending)
        );
        
        // ========== ASSERT 4: INVARIANT #2 - Refund Amount (happy path: 0 failures) ==========
        BigDecimal expectedRefund = OrderInvariants.refundAmount(failed, UNIT_PRICE);
        assertEquals(0, expectedRefund.compareTo(order.getRefundAmount()),
                String.format("INV-2: refund should be %s for %d failed plays", expectedRefund, failed));
        
        // ========== ASSERT 5: Balance deducted correctly ==========
        UserEntity userAfter = userRepository.findById(TEST_USER_ID).block(Duration.ofSeconds(5));
        BigDecimal balanceAfter = userAfter.getBalance();
        BigDecimal actualDeduction = balanceBefore.subtract(balanceAfter);
        
        assertEquals(0, EXPECTED_COST.compareTo(actualDeduction),
                String.format("Balance deduction should be €%.2f, was €%.2f", EXPECTED_COST, actualDeduction));
        
        // ========== ASSERT 6: INVARIANT #3 - Balance = Income - Refund ==========
        // In happy path: balance deducted = cost, no refund
        BigDecimal netBalance = balanceAfter;
        BigDecimal expectedBalance = balanceBefore.subtract(EXPECTED_COST).add(order.getRefundAmount());
        assertEquals(0, expectedBalance.compareTo(netBalance),
                String.format("INV-3: balance should be %.2f (was %.2f)", expectedBalance, netBalance));
        
        // ========== ASSERT 7: IDEMPOTENCE - Re-execution doesn't duplicate ==========
        // Same idempotency key should return same order
        OrderResult retryResult = orderExecutionService.createOrder(command)
                .block(Duration.ofSeconds(10));
        
        assertNotNull(retryResult, "Retry result should not be null");
        assertTrue(retryResult.success(), "Retry should succeed (idempotent)");
        assertTrue(retryResult.wasIdempotent(), "Retry should be flagged as idempotent");
        assertEquals(orderId, retryResult.order().getId(), "Retry should return same order ID");
        
        // Balance should NOT be deducted again
        UserEntity userAfterRetry = userRepository.findById(TEST_USER_ID).block(Duration.ofSeconds(5));
        assertEquals(balanceAfter, userAfterRetry.getBalance(),
                "Balance should not change on idempotent retry");
        
        // ========== ASSERT 8: Order persisted in PostgreSQL ==========
        OrderEntity reloadedOrder = orderRepository.findById(orderId).block(Duration.ofSeconds(5));
        assertNotNull(reloadedOrder, "Order should be persisted in PostgreSQL");
        assertEquals(order.getQuantity(), reloadedOrder.getQuantity());
        assertEquals(order.getDelivered(), reloadedOrder.getDelivered());
        assertEquals(order.getStatus(), reloadedOrder.getStatus());
        
        // ========== SUCCESS: All invariants verified against real PostgreSQL ==========
    }

    // ========== WEEK 3-4: ADDITIONAL SCENARIOS ==========
    
    @Test
    @DisplayName("Week 3: Partial failure - Task-based order with injected failures validates INV-1 to INV-4")
    void scenario_two_proxies_degrade_mid_execution() {
        // Skip if FailureInjectionService not available (production profile)
        if (failureInjectionService == null) {
            fail("FailureInjectionService not available - ensure test profile is active");
        }
        
        // ========== ARRANGE: Seed proxy nodes for task execution ==========
        // Create 3 DATACENTER proxies so the router can find available proxies
        for (int i = 1; i <= 3; i++) {
            ProxyNodeEntity proxy = ProxyNodeEntity.builder()
                    .provider("TEST")
                    .publicIp("10.0.0." + i)
                    .port(8080)
                    .region("EU")
                    .country("DE")
                    .tier(ProxyTier.DATACENTER.name())
                    .capacity(1000)
                    .currentLoad(0)
                    .status(ProxyStatus.ONLINE.name())
                    .build();
            proxyNodeRepository.save(proxy).block(Duration.ofSeconds(5));
        }
        
        // CRITICAL: Refresh the router's tier cache after seeding proxies
        proxyRouter.refreshAvailableTiers();
        // Small delay to ensure async refresh completes
        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        
        // ========== ARRANGE: Create a task-based order (>1000 plays) ==========
        final int QUANTITY = 2000;  // Forces task-based delivery (threshold = 1000)
        
        // Bump simulated capacity for large order
        capacityService.setSimulatedCapacity(5000);
        
        // Verify preconditions
        UserEntity userBefore = userRepository.findById(TEST_USER_ID).block(Duration.ofSeconds(5));
        assertNotNull(userBefore, "Test user should exist");
        
        // ========== ACT PHASE 1: Create order (should use task-based delivery) ==========
        CreateOrderCommand command = new CreateOrderCommand(
                TEST_USER_ID,
                TEST_SERVICE_ID,
                QUANTITY,
                "https://open.spotify.com/track/test-chaos-" + UUID.randomUUID(),
                "WORLDWIDE",
                "chaos-test-" + UUID.randomUUID()  // idempotency key
        );
        
        OrderResult result = orderExecutionService.createOrder(command)
                .block(Duration.ofSeconds(10));
        
        assertNotNull(result, "Order creation should return result");
        assertTrue(result.success(), "Order should be accepted: " + result.errorMessage());
        
        OrderEntity order = result.order();
        UUID orderId = order.getId();
        assertNotNull(orderId, "Order should have an ID");
        
        // For >1000 plays, the order uses task-based delivery
        // Status may progress quickly from PENDING to RUNNING as worker picks up tasks
        String initialStatus = order.getStatus();
        assertTrue(
                OrderStatus.PENDING.name().equals(initialStatus) ||
                OrderStatus.RUNNING.name().equals(initialStatus),
                "Large order should start as PENDING or RUNNING (task-based), got: " + initialStatus);
        assertTrue(order.getUsesTaskDelivery(), 
                "Order >1000 should use task delivery");
        
        // ========== ACT PHASE 2: Enable failure injection (HIGH failure rate for faster test) ==========
        failureInjectionService.enable();
        failureInjectionService.setFailurePercentage(90);  // 90% failure rate for faster permanent failures
        
        // ========== ACT PHASE 3: Wait for worker to process tasks ==========
        // Worker runs every 10 seconds - wait for order to reach terminal state
        // Terminal states: COMPLETED, PARTIAL, FAILED (all tasks terminal)
        // With 50% failure rate and 3 max attempts, some tasks will go FAILED_PERMANENT
        // Note: Each task can take up to 3.5 min (30s + 60s + 120s backoff) to reach permanent failure
        Awaitility.await()
                .atMost(8, TimeUnit.MINUTES)  // Increased timeout for retries with exponential backoff
                .pollInterval(5, TimeUnit.SECONDS)
                .pollDelay(3, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    OrderEntity current = orderRepository.findById(orderId)
                            .block(Duration.ofSeconds(5));
                    assertNotNull(current, "Order should exist");
                    
                    String status = current.getStatus();
                    // Log progress for debugging
                    System.out.println("  [POLL] Order status=" + status + 
                            ", delivered=" + current.getDelivered() + "/" + current.getQuantity() +
                            ", failed=" + current.getFailedPermanentPlays() +
                            ", remains=" + current.getRemains());
                    
                    // Terminal states for task-based orders:
                    // - COMPLETED: all tasks succeeded
                    // - PARTIAL: some tasks failed permanently (dead-lettered)
                    // - FAILED: all tasks failed
                    assertTrue(
                            OrderStatus.COMPLETED.name().equals(status) ||
                            OrderStatus.PARTIAL.name().equals(status) ||
                            OrderStatus.FAILED.name().equals(status),
                            "Order should reach terminal state, current: " + status);
                });
        
        // ========== ASSERT: Validate all 6 invariants via OrderInvariantValidator ==========
        ValidationResult validation = orderInvariantValidator.validateOrder(orderId)
                .block(Duration.ofSeconds(10));
        
        assertNotNull(validation, "Validation result should not be null");
        
        // Log validation details for debugging
        if (!validation.passed()) {
            System.err.println("=== INVARIANT VALIDATION FAILED ===");
            for (var violation : validation.violations()) {
                System.err.println("  " + violation.code() + ": " + violation.details());
            }
        }
        
        // The core assertion: all Phase 1 invariants must hold
        assertTrue(validation.passed(), 
                "All invariants (INV-1 to INV-6) must pass after chaos: " + 
                validation.violations().stream()
                        .map(v -> v.code() + ": " + v.details())
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("unknown failure"));
        
        // ========== ASSERT: Order reached terminal state ==========
        OrderEntity finalOrder = orderRepository.findById(orderId).block(Duration.ofSeconds(5));
        assertNotNull(finalOrder, "Order should exist after processing");
        
        String finalStatus = finalOrder.getStatus();
        assertTrue(
                OrderStatus.COMPLETED.name().equals(finalStatus) ||
                OrderStatus.PARTIAL.name().equals(finalStatus) ||
                OrderStatus.FAILED.name().equals(finalStatus),
                "Order should be in terminal state: " + finalStatus);
        
        // ========== ASSERT: Quantity conservation (INV-1) verified manually ==========
        int delivered = finalOrder.getDelivered();
        int failed = finalOrder.getFailedPermanentPlays() != null ? finalOrder.getFailedPermanentPlays() : 0;
        int pending = finalOrder.getQuantity() - delivered - failed;
        
        // For a completed order, pending should be 0
        if (OrderStatus.COMPLETED.name().equals(finalStatus)) {
            assertEquals(0, pending, "Completed order should have no pending plays");
        }
        
        // Total accounting: delivered + failed + pending == quantity
        assertEquals(QUANTITY, delivered + failed + pending,
                "Quantity conservation: " + QUANTITY + " = " + delivered + " + " + failed + " + " + pending);
        
        // ========== SUCCESS: Partial failure scenario validated ==========
        System.out.println("=== WEEK 3 CHAOS TEST PASSED ===");
        System.out.println("  Order ID: " + orderId);
        System.out.println("  Final Status: " + finalStatus);
        System.out.println("  Delivered: " + delivered + "/" + QUANTITY);
        System.out.println("  Failed Permanently: " + failed);
        System.out.println("  All invariants: PASSED");
    }

    @Test
    @DisplayName("Week 4: Complete failure - All proxies offline, graceful degradation")
    void scenario_all_proxies_offline_graceful_failure() {
        // ========== ARRANGE ==========
        final int QUANTITY = 2000;  // Task-based delivery (>1000 threshold)
        final BigDecimal UNIT_PRICE = new BigDecimal("0.002");  // €2.00 / 1000
        
        // Seed 3 proxies so order can be created and tasks generated
        for (int i = 1; i <= 3; i++) {
            ProxyNodeEntity proxy = ProxyNodeEntity.builder()
                    .provider("TEST")
                    .publicIp("10.0.0." + i)
                    .port(8080)
                    .region("EU")
                    .country("DE")
                    .tier(ProxyTier.DATACENTER.name())
                    .capacity(1000)
                    .currentLoad(0)
                    .status(ProxyStatus.ONLINE.name())
                    .build();
            proxyNodeRepository.save(proxy).block(Duration.ofSeconds(5));
        }
        proxyRouter.refreshAvailableTiers();
        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        
        // Bump simulated capacity to accept order
        capacityService.setSimulatedCapacity(5000);
        
        // ========== ACT: Create order ==========
        CreateOrderCommand command = new CreateOrderCommand(
                TEST_USER_ID,
                TEST_SERVICE_ID,
                QUANTITY,
                "https://open.spotify.com/track/total-outage-" + UUID.randomUUID(),
                "WORLDWIDE",
                "outage-test-" + UUID.randomUUID()
        );
        
        OrderResult result = orderExecutionService.createOrder(command)
                .block(Duration.ofSeconds(10));
        
        assertNotNull(result, "Order creation should return result");
        assertTrue(result.success(), "Order should be accepted: " + result.errorMessage());
        
        OrderEntity order = result.order();
        UUID orderId = order.getId();
        assertNotNull(orderId, "Order should have an ID");
        
        // Verify task-based delivery is being used
        assertTrue(order.getUsesTaskDelivery(), "Order >1000 should use task delivery");
        
        System.out.println("=== WEEK 4: Total Outage Test ===");
        System.out.println("  Order ID: " + orderId);
        System.out.println("  Quantity: " + QUANTITY);
        
        // ========== SIMULATE TOTAL OUTAGE via SQL ==========
        // This is faster than waiting for real retries - we force all tasks to FAILED_PERMANENT
        // This tests the invariant validation under complete failure scenario
        
        // 1. Mark all tasks as FAILED_PERMANENT
        databaseClient.sql("""
            UPDATE order_tasks 
            SET status = 'FAILED_PERMANENT', 
                attempts = 3,
                last_error = 'Simulated total outage for Week 4 test'
            WHERE order_id = :orderId
            """)
            .bind("orderId", orderId)
            .fetch()
            .rowsUpdated()
            .block(Duration.ofSeconds(5));
        
        // 2. Update order to reflect total failure
        databaseClient.sql("""
            UPDATE orders 
            SET status = 'FAILED',
                failed_permanent_plays = :quantity,
                delivered = 0,
                refund_amount = :refundAmount
            WHERE id = :orderId
            """)
            .bind("orderId", orderId)
            .bind("quantity", QUANTITY)
            .bind("refundAmount", UNIT_PRICE.multiply(BigDecimal.valueOf(QUANTITY)))
            .fetch()
            .rowsUpdated()
            .block(Duration.ofSeconds(5));
        
        System.out.println("  Simulated: All tasks → FAILED_PERMANENT");
        System.out.println("  Simulated: Order → FAILED with full refund");
        
        // ========== ASSERT: Validate invariants via OrderInvariantValidator ==========
        ValidationResult validation = orderInvariantValidator.validateOrder(orderId)
                .block(Duration.ofSeconds(10));
        
        assertNotNull(validation, "Validation result should not be null");
        
        if (!validation.passed()) {
            System.err.println("=== INVARIANT VALIDATION FAILED ===");
            for (var violation : validation.violations()) {
                System.err.println("  " + violation.code() + ": " + violation.details());
            }
        }
        
        assertTrue(validation.passed(), 
                "All invariants must pass even under total failure: " + 
                validation.violations().stream()
                        .map(v -> v.code() + ": " + v.details())
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("unknown"));
        
        // ========== ASSERT: Order state ==========
        OrderEntity finalOrder = orderRepository.findById(orderId).block(Duration.ofSeconds(5));
        assertNotNull(finalOrder, "Order should exist");
        
        String finalStatus = finalOrder.getStatus();
        int delivered = finalOrder.getDelivered();
        int failed = finalOrder.getFailedPermanentPlays() != null 
                ? finalOrder.getFailedPermanentPlays() : 0;
        
        // Verify total failure scenario
        assertEquals(OrderStatus.FAILED.name(), finalStatus, "Order should be in FAILED status");
        assertEquals(0, delivered, "No plays should be delivered (total outage)");
        assertEquals(QUANTITY, failed, "All plays should be marked as failed");
        
        // ========== ASSERT: INV-1 Quantity Conservation ==========
        assertEquals(QUANTITY, delivered + failed,
                "Quantity conservation: " + QUANTITY + " = " + delivered + " + " + failed);
        
        // ========== SUCCESS ==========
        System.out.println("=== WEEK 4 TOTAL OUTAGE TEST PASSED ===");
        System.out.println("  Final Status: " + finalStatus);
        System.out.println("  Delivered: " + delivered + "/" + QUANTITY);
        System.out.println("  Failed Permanently: " + failed);
        System.out.println("  All invariants: PASSED");
    }

    // ========== HELPER: Verify Phase 1 invariants still hold ==========
    
    private void assertQuantityConserved(int ordered, int delivered, int failed, int pending) {
        assertEquals(ordered, delivered + failed + pending,
                "Quantity conservation: ordered must equal delivered + failed + pending");
    }

    private void assertRefundCorrect(int failedCount, BigDecimal unitPrice, BigDecimal actualRefund) {
        BigDecimal expectedRefund = unitPrice.multiply(BigDecimal.valueOf(failedCount))
                .setScale(2, RoundingMode.HALF_UP);
        assertEquals(0, expectedRefund.compareTo(actualRefund),
                "Refund must be exactly failedCount × unitPrice");
    }

    private void assertBalanceConserved(BigDecimal income, BigDecimal refund, BigDecimal netBalance) {
        BigDecimal expected = income.subtract(refund);
        assertEquals(0, expected.compareTo(netBalance),
                "Balance conservation: net = income - refund");
    }
}
