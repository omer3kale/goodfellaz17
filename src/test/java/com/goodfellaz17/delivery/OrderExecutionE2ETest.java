package com.goodfellaz17.delivery;

import com.goodfellaz17.delivery.application.OrderService;
import com.goodfellaz17.infrastructure.persistence.entity.ExecutionResultEntity;
import com.goodfellaz17.infrastructure.persistence.entity.OrderEntity;
import com.goodfellaz17.infrastructure.persistence.entity.OrderTaskEntity;
import com.goodfellaz17.infrastructure.persistence.repository.ExecutionResultRepository;
import com.goodfellaz17.infrastructure.persistence.repository.OrderRepository;
import com.goodfellaz17.infrastructure.persistence.repository.OrderTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderExecutionE2ETest — End-to-end integration test for Slice A.
 * 
 * Verifies the full flow:
 * 1. POST /api/orders to create an order and decompose tasks.
 * 2. Wait for TaskExecutorService to process tasks asynchronously.
 * 3. Assert order reaches COMPLETED status with correct task and result counts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("local")
class OrderExecutionE2ETest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderTaskRepository orderTaskRepository;

    @Autowired
    private ExecutionResultRepository executionResultRepository;

    private static final UUID TEST_TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID TEST_SERVICE_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");
    private static final int TEST_QUANTITY = 90; // Small quantity for fast test

    /**
     * Main test: order creation → execution → completion.
     */
    @Test
    void testOrderExecutionFlow() {
        // 1. Create order via POST /api/orders
        UUID orderId = createOrder();
        assertNotNull(orderId, "Order should be created and ID returned");

        // 2. Wait for executor to process all tasks
        OrderEntity completedOrder = waitForOrderCompletion(orderId, Duration.ofSeconds(15));
        assertNotNull(completedOrder, "Order should reach COMPLETED status within timeout");

        // 3. Verify order status and aggregates
        assertEquals("COMPLETED", completedOrder.getStatus(), "Order status should be COMPLETED");
        assertTrue(completedOrder.getPlaysDelivered() > 0, "playsDelivered should be > 0");
        assertEquals(TEST_QUANTITY, completedOrder.getPlaysDelivered() + completedOrder.getPlaysFailed(),
                "playsDelivered + playsFailed should equal order quantity");

        // 4. Verify all tasks are terminal
        List<OrderTaskEntity> tasks = orderTaskRepository.findByOrderId(orderId)
                .collectList()
                .block();
        assertNotNull(tasks, "Tasks should exist");
        assertEquals(3, tasks.size(), "Order should be decomposed into 3 tasks");
        tasks.forEach(task -> {
            String status = task.getStatus();
            assertTrue("COMPLETED".equals(status) || "FAILED".equals(status),
                    "Task status should be terminal (COMPLETED or FAILED), got: " + status);
            assertNotNull(task.getCompletedAt(), "Completed task should have completedAt timestamp");
        });

        // 5. Verify execution results exist for all tasks
        for (OrderTaskEntity task : tasks) {
            List<ExecutionResultEntity> results = executionResultRepository.findByOrderTaskId(task.getId())
                    .collectList()
                    .block();
            assertNotNull(results, "ExecutionResults should exist for task " + task.getId());
            assertTrue(results.size() > 0, "At least one ExecutionResult should exist per task");
            results.forEach(result -> {
                assertNotNull(result.getStartedAt(), "Result should have startedAt");
                assertNotNull(result.getCompletedAt(), "Result should have completedAt");
                assertTrue(result.getDurationMs() >= 0, "Duration should be non-negative");
                assertTrue(result.getSuccessFlag() != null, "SuccessFlag should be set");
                if (!result.getSuccessFlag()) {
                    assertNotNull(result.getFailureReason(), "Failed result should have failureReason");
                }
            });
        }
    }

    /**
     * POST /api/orders and return the created orderId.
     */
    private UUID createOrder() {
        OrderService.OrderCreateRequest request = new OrderService.OrderCreateRequest();
        request.setTenantId(TEST_TENANT_ID);
        request.setServiceId(TEST_SERVICE_ID);
        request.setQuantity(TEST_QUANTITY);
        request.setTrackId("spotify:track:test123");
        request.setArtistId("spotify:artist:artist456");
        request.setEstimatedCost(BigDecimal.valueOf(25.50));

        OrderService.OrderResponse response = webTestClient.post()
                .uri("/api/orders")
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderService.OrderResponse.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getOrder(), "Order in response should not be null");
        return response.getOrder().getId();
    }

    /**
     * Poll the order status until COMPLETED or timeout is reached.
     * 
     * @param orderId Order UUID to poll
     * @param timeout Maximum time to wait
     * @return The completed OrderEntity, or null if timeout exceeded
     */
    private OrderEntity waitForOrderCompletion(UUID orderId, Duration timeout) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeout.toMillis();

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                // GET /api/orders/{orderId} and check if COMPLETED
                OrderService.OrderResponse response = webTestClient.get()
                        .uri("/api/orders/{id}", orderId)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(OrderService.OrderResponse.class)
                        .returnResult()
                        .getResponseBody();

                if (response != null && "COMPLETED".equals(response.getOrder().getStatus())) {
                    return response.getOrder();
                }

                // Sleep before next poll
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Polling interrupted", e);
            }
        }

        return null; // Timeout
    }
}
