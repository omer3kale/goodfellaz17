package com.goodfellaz17.presentation.api.admin;

import com.goodfellaz17.application.service.CapacityService;
import com.goodfellaz17.domain.model.generated.OrderEntity;
import com.goodfellaz17.domain.model.generated.OrderStatus;
import com.goodfellaz17.infrastructure.persistence.generated.GeneratedOrderRepository;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin API for Order Monitoring and Debugging.
 * 
 * Provides internal views into order execution progress.
 * NOT for public/panel use - only for ops and development debugging.
 * 
 * Endpoints:
 *   GET /api/admin/orders/{id}/progress - Detailed progress view
 *   GET /api/admin/orders/{id}/tasks    - Task breakdown (stub for now)
 *   GET /api/admin/orders/queue         - View order queue summary
 *   GET /api/admin/orders/recent        - Recent orders list
 */
@RestController
@RequestMapping("/api/admin/orders")
public class OrderAdminController {
    
    private static final Logger log = LoggerFactory.getLogger(OrderAdminController.class);
    
    private final GeneratedOrderRepository orderRepository;
    private final CapacityService capacityService;
    
    public OrderAdminController(
            GeneratedOrderRepository orderRepository,
            CapacityService capacityService) {
        this.orderRepository = orderRepository;
        this.capacityService = capacityService;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // GET /api/admin/orders/{id}/progress
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Get detailed progress view for an order.
     * 
     * Returns comprehensive progress information including:
     * - Quantity / delivered / remains
     * - Progress percentage
     * - ETA and time estimates
     * - Status history hints
     * 
     * Example:
     * curl http://localhost:8080/api/admin/orders/{id}/progress | jq .
     */
    @GetMapping("/{orderId}/progress")
    public Mono<ResponseEntity<OrderProgressResponse>> getOrderProgress(
            @PathVariable UUID orderId) {
        
        log.debug("GET /api/admin/orders/{}/progress", orderId);
        
        return orderRepository.findById(orderId)
            .map(order -> {
                // Calculate progress metrics
                double progressPercent = order.getQuantity() > 0
                    ? (double) order.getDelivered() / order.getQuantity() * 100.0
                    : 0.0;
                
                // Calculate time metrics
                Duration elapsed = order.getStartedAt() != null
                    ? Duration.between(order.getStartedAt(), Instant.now())
                    : Duration.ZERO;
                
                Duration remaining = order.getEstimatedCompletionAt() != null
                    ? Duration.between(Instant.now(), order.getEstimatedCompletionAt())
                    : null;
                
                // Calculate delivery rate
                double deliveryRate = elapsed.toMinutes() > 0
                    ? (double) order.getDelivered() / elapsed.toMinutes() * 60.0
                    : 0.0; // plays/hour
                
                OrderProgressResponse response = new OrderProgressResponse(
                    order.getId(),
                    order.getServiceName(),
                    order.getTargetUrl(),
                    order.getQuantity(),
                    order.getDelivered(),
                    order.getRemains(),
                    order.getStatus(),
                    Math.round(progressPercent * 100.0) / 100.0,
                    deliveryRate,
                    order.getEstimatedCompletionAt(),
                    remaining != null ? remaining.toHours() : null,
                    order.getCreatedAt(),
                    order.getStartedAt(),
                    order.getCompletedAt(),
                    elapsed.toMinutes(),
                    order.getFailureReason(),
                    buildStatusHint(order)
                );
                
                return ResponseEntity.ok(response);
            })
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // GET /api/admin/orders/{id}/tasks
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Get task breakdown for an order.
     * 
     * STUB: Will be populated when order_tasks table is created.
     * For now returns empty list with a hint.
     * 
     * Future behavior:
     * - Lists all order_tasks for this order
     * - Shows proxy used, batch size, status, timing
     * - Helps debug which tasks succeeded/failed
     */
    @GetMapping("/{orderId}/tasks")
    public Mono<ResponseEntity<OrderTasksResponse>> getOrderTasks(
            @PathVariable UUID orderId) {
        
        log.debug("GET /api/admin/orders/{}/tasks", orderId);
        
        return orderRepository.findById(orderId)
            .map(order -> {
                // STUB: order_tasks not implemented yet
                OrderTasksResponse response = new OrderTasksResponse(
                    orderId,
                    List.of(),  // Empty for now
                    0,
                    0,
                    0,
                    "Task execution not yet implemented. " +
                    "This endpoint will show task-level progress once the execution loop is built."
                );
                return ResponseEntity.ok(response);
            })
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // GET /api/admin/orders/queue
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Get order queue summary.
     * 
     * Shows aggregate view of pending work:
     * - Pending orders count and total plays
     * - Running orders count and total plays
     * - Estimated time to clear queue
     */
    @GetMapping("/queue")
    public Mono<ResponseEntity<OrderQueueResponse>> getOrderQueue() {
        log.debug("GET /api/admin/orders/queue");
        
        return Mono.zip(
            // Count pending orders
            orderRepository.findByStatusIn(List.of(OrderStatus.PENDING.name()))
                .collectList(),
            // Count running orders  
            orderRepository.findByStatusIn(List.of(OrderStatus.RUNNING.name()))
                .collectList(),
            // Get pending load
            capacityService.calculatePendingLoad(),
            // Get capacity
            capacityService.calculateCurrentCapacity()
        ).map(tuple -> {
            var pendingOrders = tuple.getT1();
            var runningOrders = tuple.getT2();
            int pendingPlays = tuple.getT3();
            var capacity = tuple.getT4();
            
            int pendingCount = pendingOrders.size();
            int runningCount = runningOrders.size();
            int runningPlays = runningOrders.stream()
                .mapToInt(OrderEntity::getRemains)
                .sum();
            
            // Estimate time to clear
            double hoursToClean = capacity.totalPlaysPerHour() > 0
                ? (double) pendingPlays / capacity.totalPlaysPerHour()
                : 0.0;
            
            return ResponseEntity.ok(new OrderQueueResponse(
                pendingCount,
                pendingPlays - runningPlays,
                runningCount,
                runningPlays,
                pendingPlays,
                capacity.totalPlaysPerHour(),
                Math.round(hoursToClean * 10.0) / 10.0
            ));
        });
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // GET /api/admin/orders/recent
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Get recent orders for monitoring.
     * 
     * Lists last N orders with key metrics.
     * Useful for quick health check of order flow.
     */
    @GetMapping("/recent")
    public Mono<ResponseEntity<List<OrderSummary>>> getRecentOrders(
            @RequestParam(defaultValue = "10") int limit) {
        
        log.debug("GET /api/admin/orders/recent?limit={}", limit);
        
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        
        return orderRepository.findAll()
            .take(safeLimit)
            .map(order -> new OrderSummary(
                order.getId(),
                order.getServiceName(),
                order.getQuantity(),
                order.getDelivered(),
                order.getStatus(),
                order.getCreatedAt()
            ))
            .collectList()
            .map(ResponseEntity::ok);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private String buildStatusHint(OrderEntity order) {
        OrderStatus status = OrderStatus.valueOf(order.getStatus());
        return switch (status) {
            case PENDING -> "Waiting in queue. Will start when worker picks it up.";
            case VALIDATING -> "Validating target URL before processing.";
            case RUNNING -> String.format("Active. Delivered %d of %d (%.1f%%). Rate: check delivery_rate field.",
                order.getDelivered(), order.getQuantity(),
                (double) order.getDelivered() / order.getQuantity() * 100);
            case PARTIAL -> String.format("Partially complete. Delivered %d, Remains %d. Check failure_reason.",
                order.getDelivered(), order.getRemains());
            case COMPLETED -> "Successfully completed all deliveries.";
            case FAILED -> "Failed. Check failure_reason for details.";
            case CANCELLED -> "Cancelled by user or system.";
            case REFUNDED -> "Refunded. Balance credited back to user.";
        };
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // RESPONSE DTOs
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Detailed progress view for debugging order execution.
     */
    public record OrderProgressResponse(
        UUID id,
        String serviceName,
        String targetUrl,
        int quantity,
        int delivered,
        int remains,
        String status,
        double progressPercent,
        double deliveryRatePerHour,
        @Nullable Instant estimatedCompletionAt,
        @Nullable Long remainingHours,
        Instant createdAt,
        @Nullable Instant startedAt,
        @Nullable Instant completedAt,
        long elapsedMinutes,
        @Nullable String failureReason,
        String statusHint
    ) implements Serializable {}
    
    /**
     * Task-level breakdown for an order.
     * Will be populated when order_tasks is implemented.
     */
    public record OrderTasksResponse(
        UUID orderId,
        List<TaskSummary> tasks,
        int completedTasks,
        int pendingTasks,
        int failedTasks,
        String hint
    ) implements Serializable {}
    
    /**
     * Individual task summary (stub for now).
     */
    public record TaskSummary(
        UUID taskId,
        String proxyIp,
        int batchSize,
        int delivered,
        String status,
        Instant startedAt,
        Instant completedAt
    ) implements Serializable {}
    
    /**
     * Order queue summary.
     */
    public record OrderQueueResponse(
        int pendingOrderCount,
        int pendingPlays,
        int runningOrderCount,
        int runningPlays,
        int totalQueuedPlays,
        int capacityPlaysPerHour,
        double estimatedHoursToClear
    ) implements Serializable {}
    
    /**
     * Brief order summary for listings.
     */
    public record OrderSummary(
        UUID id,
        String serviceName,
        int quantity,
        int delivered,
        String status,
        Instant createdAt
    ) implements Serializable {}
}
