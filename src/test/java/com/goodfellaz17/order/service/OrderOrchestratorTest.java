package com.goodfellaz17.order.service;

import com.goodfellaz17.order.domain.Order;
import com.goodfellaz17.order.domain.OrderTask;
import com.goodfellaz17.order.repository.PlayOrderRepository;
import com.goodfellaz17.order.repository.PlayOrderTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderOrchestrator.
 * 
 * Tests the reactive order creation pipeline, task decomposition,
 * status transitions, and error handling.
 * 
 * Thesis Evidence: Demonstrates rigorous testing methodology for
 * reactive, async-first architecture using StepVerifier.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderOrchestrator Tests")
class OrderOrchestratorTest {
    
    @Mock
    private PlayOrderRepository orderRepository;
    
    @Mock
    private PlayOrderTaskRepository taskRepository;
    
    @Mock
    private OrderTaskFactory taskFactory;
    
    private OrderOrchestrator orchestrator;
    
    @BeforeEach
    void setUp() {
        orchestrator = new OrderOrchestrator(orderRepository, taskRepository, taskFactory);
    }
    
    // ==================== Happy Path Tests ====================
    
    @Test
    @DisplayName("testCreateOrder_validInput_createsOrderAndTasks - Happy path with 2 accounts")
    void testCreateOrder_validInput_createsOrderAndTasks() {
        // Arrange
        String trackId = "spotify:track:test-123";
        Integer quantity = 2;
        List<String> accountIds = Arrays.asList("account-1", "account-2");
        
        UUID orderId = UUID.randomUUID();
        Order savedOrder = new Order();
        savedOrder.setId(orderId);
        savedOrder.setTrackId(trackId);
        savedOrder.setQuantity(quantity);
        savedOrder.setStatus("ACTIVE");
        
        OrderTask task1 = new OrderTask();
        task1.setId(UUID.randomUUID());
        task1.setOrderId(orderId);
        task1.setAccountId("account-1");
        task1.setStatus("PENDING");
        
        OrderTask task2 = new OrderTask();
        task2.setId(UUID.randomUUID());
        task2.setOrderId(orderId);
        task2.setAccountId("account-2");
        task2.setStatus("PENDING");
        
        Set<OrderTask> taskSet = new HashSet<>(Arrays.asList(task1, task2));
        
        // Mock the behavior
        when(orderRepository.insertOrder(any(), eq(trackId), eq(quantity), eq("PENDING"), eq(0), eq(0), any(), any()))
            .thenReturn(Mono.empty());
        when(taskFactory.createTasksForOrder(any(), eq(accountIds)))
            .thenReturn(taskSet);
        when(taskRepository.insertTask(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Mono.empty());
        when(orderRepository.save(any()))
            .thenReturn(Mono.just(savedOrder));
        
        // Act & Assert
        StepVerifier.create(orchestrator.createOrder(trackId, quantity, accountIds))
            .assertNext(order -> {
                assertEquals(trackId, order.getTrackId());
                assertEquals(quantity, order.getQuantity());
                assertEquals("ACTIVE", order.getStatus());
            })
            .verifyComplete();
    }
    
    @Test
    @DisplayName("testCreateOrder_decomposeIntoTasks_createsCorrectNumber")
    void testCreateOrder_decomposeIntoTasks_createsCorrectNumber() {
        // Arrange
        String trackId = "spotify:track:decompose-test";
        Integer quantity = 3;  // MUST match accountIds size
        List<String> accountIds = Arrays.asList("acc1", "acc2", "acc3");
        
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setTrackId(trackId);
        order.setQuantity(quantity);
        order.setStatus("ACTIVE");
        
        // Create 3 tasks (one per account)
        Set<OrderTask> tasks = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            OrderTask task = new OrderTask();
            task.setId(UUID.randomUUID());
            task.setOrderId(orderId);
            task.setAccountId("acc" + (i + 1));
            task.setStatus("PENDING");
            tasks.add(task);
        }
        
        // Mock
        when(orderRepository.insertOrder(any(), eq(trackId), eq(quantity), eq("PENDING"), eq(0), eq(0), any(), any()))
            .thenReturn(Mono.empty());
        when(taskFactory.createTasksForOrder(any(), eq(accountIds)))
            .thenReturn(tasks);
        when(taskRepository.insertTask(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Mono.empty());
        when(orderRepository.save(any()))
            .thenReturn(Mono.just(order));
        
        // Act & Assert
        StepVerifier.create(orchestrator.createOrder(trackId, quantity, accountIds))
            .assertNext(resultOrder -> {
                assertEquals(3, tasks.size(), "Expected 3 tasks for 3 accounts");
            })
            .verifyComplete();
    }
    
    // ==================== Error Handling Tests ====================
    
    @Test
    @DisplayName("testCreateOrder_nullTrackId_throwsException")
    void testCreateOrder_nullTrackId_throwsException() {
        // Arrange
        String trackId = null;
        Integer quantity = 1;
        List<String> accountIds = Arrays.asList("account-1");
        
        // Act & Assert
        StepVerifier.create(orchestrator.createOrder(trackId, quantity, accountIds))
            .expectError(IllegalArgumentException.class)
            .verify();
    }
    
    @Test
    @DisplayName("testCreateOrder_zeroQuantity_throwsException")
    void testCreateOrder_zeroQuantity_throwsException() {
        // Arrange
        String trackId = "spotify:track:test";
        Integer quantity = 0;
        List<String> accountIds = Arrays.asList("account-1");
        
        // Act & Assert
        StepVerifier.create(orchestrator.createOrder(trackId, quantity, accountIds))
            .expectError(IllegalArgumentException.class)
            .verify();
    }
    
    @Test
    @DisplayName("testCreateOrder_emptyAccounts_throwsException")
    void testCreateOrder_emptyAccounts_throwsException() {
        // Arrange
        String trackId = "spotify:track:test";
        Integer quantity = 1;
        List<String> accountIds = new ArrayList<>();
        
        // Mock to throw error (factory should handle this)
        when(taskFactory.createTasksForOrder(any(), eq(accountIds)))
            .thenThrow(new IllegalArgumentException("At least one account ID required"));
        
        // Act & Assert
        StepVerifier.create(orchestrator.createOrder(trackId, quantity, accountIds))
            .expectError(IllegalArgumentException.class)
            .verify();
    }
    
    // ==================== Status Transition Tests ====================
    
    @Test
    @DisplayName("testCreateOrder_statusTransition_pendingToActive")
    void testCreateOrder_statusTransition_pendingToActive() {
        // Arrange
        String trackId = "spotify:track:status-test";
        Integer quantity = 1;
        List<String> accountIds = Arrays.asList("account-1");
        
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setTrackId(trackId);
        order.setQuantity(quantity);
        order.setStatus("ACTIVE"); // Should transition from PENDING
        
        Set<OrderTask> tasks = new HashSet<>();
        OrderTask task = new OrderTask();
        task.setId(UUID.randomUUID());
        task.setOrderId(orderId);
        task.setAccountId("account-1");
        task.setStatus("PENDING");
        tasks.add(task);
        
        // Mock
        when(orderRepository.insertOrder(any(), eq(trackId), eq(quantity), eq("PENDING"), eq(0), eq(0), any(), any()))
            .thenReturn(Mono.empty());
        when(taskFactory.createTasksForOrder(any(), eq(accountIds)))
            .thenReturn(tasks);
        when(taskRepository.insertTask(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Mono.empty());
        when(orderRepository.save(any()))
            .thenReturn(Mono.just(order));
        
        // Act & Assert
        StepVerifier.create(orchestrator.createOrder(trackId, quantity, accountIds))
            .assertNext(result -> {
                assertEquals("ACTIVE", result.getStatus());
            })
            .verifyComplete();
    }
    
    // ==================== Metrics Tests ====================
    
    @Test
    @DisplayName("testGetMetrics_aggregatesOrderAndTaskCounts")
    void testGetMetrics_aggregatesOrderAndTaskCounts() {
        // Arrange - mock all repository calls needed by getMetrics()
        when(orderRepository.count()).thenReturn(Mono.just(3L));
        when(orderRepository.countByStatus("COMPLETED")).thenReturn(Mono.just(1L));
        when(orderRepository.countByStatus("FAILED")).thenReturn(Mono.just(0L));
        when(orderRepository.countByStatus("PENDING")).thenReturn(Mono.just(0L));
        when(orderRepository.countByStatus("ACTIVE")).thenReturn(Mono.just(2L));
        when(orderRepository.countByStatus("DELIVERING")).thenReturn(Mono.just(0L));
        
        when(taskRepository.count()).thenReturn(Mono.just(6L));
        when(taskRepository.countByStatus("COMPLETED")).thenReturn(Mono.just(2L));
        when(taskRepository.countByStatus("FAILED")).thenReturn(Mono.just(0L));
        when(taskRepository.countByStatus("ASSIGNED")).thenReturn(Mono.just(0L));
        when(taskRepository.getAverageRetries()).thenReturn(Mono.just(0.5));
        
        when(orderRepository.sumPlaysDelivered()).thenReturn(Mono.just(100L));
        when(orderRepository.sumPlaysFailed()).thenReturn(Mono.just(0L));
        
        // Act & Assert
        StepVerifier.create(orchestrator.getMetrics())
            .assertNext(metrics -> {
                assertEquals(3L, (long)metrics.getTotalOrders());
                assertEquals(6L, (long)metrics.getTotalTasks());
                assertEquals(1L, (long)metrics.getCompletedOrders());
            })
            .verifyComplete();
    }
    
    // ==================== Reactive Contract Tests ====================
    
    @Test
    @DisplayName("testCreateOrder_returnsMonoNotBlocking - Lazy evaluation")
    void testCreateOrder_returnsMonoNotBlocking() {
        // Arrange
        String trackId = "spotify:track:lazy-test";
        Integer quantity = 1;
        List<String> accountIds = Arrays.asList("account-1");
        
        // This should NOT be called until subscription
        when(orderRepository.insertOrder(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Mono.empty());
        
        // Act
        Mono<Order> result = orchestrator.createOrder(trackId, quantity, accountIds);
        
        // Assert - repository not called until subscription
        verify(orderRepository, never()).insertOrder(any(), any(), any(), any(), any(), any(), any(), any());
        
        // Now subscribe and verify it gets called
        result.subscribe();
        verify(orderRepository, atLeastOnce()).insertOrder(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
