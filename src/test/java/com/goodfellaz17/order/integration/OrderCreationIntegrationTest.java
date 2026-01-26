package com.goodfellaz17.order.integration;

import com.goodfellaz17.order.repository.PlayOrderRepository;
import com.goodfellaz17.order.repository.PlayOrderTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Order Creation endpoint.
 * Uses real PostgreSQL database via Testcontainers.
 * Tests the complete flow: HTTP request → R2DBC persistence → metrics aggregation.
 */
@DisplayName("Order Creation Integration Tests")
public class OrderCreationIntegrationTest extends IntegrationTestBase {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PlayOrderRepository orderRepository;

    @Autowired
    private PlayOrderTaskRepository taskRepository;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll().block();
        orderRepository.deleteAll().block();
    }

    @Test
    @DisplayName("POST /api/orders/create persists to real PostgreSQL database")
    void testCreateOrder_persistsToDatabase() {
        String request = """
            {
              "trackId": "spotify:track:it-1",
              "quantity": 2,
              "accountIds": ["it-a1", "it-a2"]
            }
            """;

        webTestClient.post()
            .uri("/api/orders/create")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.order.id").isNotEmpty()
            .jsonPath("$.order.status").isEqualTo("ACTIVE")
            .jsonPath("$.order.trackId").isEqualTo("spotify:track:it-1");

        // Verify persistence: order saved to DB
        var orders = orderRepository.findAll().collectList().block();
        assertEquals(1, orders.size(), "Order persisted to DB");
        assertEquals("spotify:track:it-1", orders.get(0).getTrackId());

        // Verify persistence: 2 tasks created
        var tasks = taskRepository.findAll().collectList().block();
        assertEquals(2, tasks.size(), "2 tasks created for 2 accounts");
        assertTrue(tasks.stream().allMatch(t -> "PENDING".equals(t.getStatus())),
            "All tasks PENDING initially");
    }

    @Test
    @DisplayName("POST /api/orders/create decomposes into correct number of tasks")
    void testCreateOrder_decomposeIntoTasks() {
        String request = """
            {
              "trackId": "spotify:track:it-decompose",
              "quantity": 3,
              "accountIds": ["it-b1", "it-b2", "it-b3"]
            }
            """;

        webTestClient.post()
            .uri("/api/orders/create")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated();

        // Verify task decomposition: 3 accounts → 3 tasks
        var tasks = taskRepository.findAll().collectList().block();
        assertEquals(3, tasks.size(), "3 tasks from 3 accounts");

        var accountIds = tasks.stream().map(t -> t.getAccountId()).sorted().toList();
        assertEquals(3, accountIds.size());
        assertTrue(accountIds.contains("it-b1"));
        assertTrue(accountIds.contains("it-b2"));
        assertTrue(accountIds.contains("it-b3"));

        assertTrue(tasks.stream().allMatch(t -> "PENDING".equals(t.getStatus())),
            "All tasks PENDING");
    }

    @Test
    @DisplayName("GET /api/orders/metrics aggregates real database data")
    void testGetMetrics_aggregatesOrders() {
        // Create 2 orders with 2 tasks each (4 tasks total)
        for (int i = 1; i <= 2; i++) {
            String req = String.format("""
                {
                  "trackId": "spotify:track:it-metrics-%d",
                  "quantity": 2,
                  "accountIds": ["it-m%d-1", "it-m%d-2"]
                }
                """, i, i, i);

            webTestClient.post()
                .uri("/api/orders/create")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isCreated();
        }

        // Verify metrics aggregation
        webTestClient.get()
            .uri("/api/orders/metrics")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.totalOrders").isEqualTo(2)
            .jsonPath("$.totalTasks").isEqualTo(4);
    }
}
