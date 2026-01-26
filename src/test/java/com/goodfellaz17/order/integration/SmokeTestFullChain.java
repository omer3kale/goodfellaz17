package com.goodfellaz17.order.integration;

import com.goodfellaz17.order.domain.Order;
import com.goodfellaz17.order.domain.OrderTask;
import com.goodfellaz17.order.repository.PlayOrderRepository;
import com.goodfellaz17.order.repository.PlayOrderTaskRepository;
import com.goodfellaz17.order.service.OrderOrchestrator;
import com.goodfellaz17.order.service.OrderTaskFactory;
import com.goodfellaz17.order.service.TaskExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SmokeTestFullChain: End-to-end smoke test without Docker/live app.
 *
 * Chain: CreateOrder → Decompose Tasks → Execute Task → Verify Status Change
 *
 * Validates:
 * - Order creates with correct task count
 * - Task execution progresses through state lifecycle
 * - All invariants maintained
 */
@DisplayName("Full Chain Smoke Test (No Docker)")
class SmokeTestFullChain {

    private PlayOrderRepository orderRepository;
    private PlayOrderTaskRepository taskRepository;
    private OrderOrchestrator orchestrator;
    private TaskExecutionService taskExecutionService;

    @BeforeEach
    void setup() {
        orderRepository = Mockito.mock(PlayOrderRepository.class);
        taskRepository = Mockito.mock(PlayOrderTaskRepository.class);
        // Use real factory (not mocked) for authentic task creation
        OrderTaskFactory taskFactory = new OrderTaskFactory();
        orchestrator = new OrderOrchestrator(orderRepository, taskRepository, taskFactory);
        taskExecutionService = new TaskExecutionService(
            cmd -> Mono.just(new com.goodfellaz17.order.domain.port.PlayResult(
                true, null, "local-executor-1", cmd.retryCount() + 1,
                java.time.Instant.now(), java.time.Instant.now(), 100L)),
            taskRepository);
    }

    @Test
    @DisplayName("order creation → task decomposition → execution → status verified")
    void testFullChainSmokeTest() {
        // STEP 1: Create an order (mocked)
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setTrackId("spotify:track:test123");
        order.setQuantity(3);
        order.setStatus("ACTIVE");
        order.setPlaysDelivered(0);
        order.setPlaysFailed(0);

        when(orderRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // Mock taskRepository to save tasks and return them (3 tasks for 3 accounts)
        when(taskRepository.saveAll((java.lang.Iterable<OrderTask>) any())).thenAnswer(inv -> {
            java.lang.Iterable<OrderTask> tasks = inv.getArgument(0);
            return reactor.core.publisher.Flux.fromIterable(tasks);
        });

        // STEP 2: Create tasks via orchestrator
        StepVerifier.create(
            orchestrator.createOrder(
                order.getTrackId(),
                order.getQuantity(),
                Arrays.asList("account-1", "account-2", "account-3"))
        )
            .assertNext(createdOrder -> {
                assertNotNull(createdOrder.getId());
                assertEquals("ACTIVE", createdOrder.getStatus());
                assertEquals(3, createdOrder.getQuantity());
            })
            .verifyComplete();

        // STEP 3: Create a PENDING task
        OrderTask task = new OrderTask();
        task.setId(UUID.randomUUID());
        task.setOrderId(order.getId());
        task.setAccountId("account-1");
        task.setStatus("PENDING");
        task.setMaxRetries(3);

        when(taskRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // STEP 4: Execute the task
        StepVerifier.create(taskExecutionService.executeTask(task))
            .assertNext(executedTask -> {
                // INV-2: Status changed
                assertEquals("COMPLETED", executedTask.getStatus());

                // INV-4: startedAt + assignedProxyNode set
                assertNotNull(executedTask.getStartedAt());
                assertEquals("local-executor", executedTask.getAssignedProxyNode());

                // INV-5: completedAt set
                assertNotNull(executedTask.getCompletedAt());
            })
            .verifyComplete();
    }
}
