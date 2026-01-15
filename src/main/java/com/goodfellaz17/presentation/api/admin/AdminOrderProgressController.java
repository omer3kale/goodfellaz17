package com.goodfellaz17.presentation.api.admin;

import com.goodfellaz17.application.service.CapacityService;
import com.goodfellaz17.application.service.TaskGenerationService;
import com.goodfellaz17.application.worker.OrderDeliveryWorker;
import com.goodfellaz17.domain.model.generated.OrderEntity;
import com.goodfellaz17.domain.model.generated.OrderTaskEntity;
import com.goodfellaz17.domain.model.generated.TaskStatus;
import com.goodfellaz17.infrastructure.persistence.generated.GeneratedOrderRepository;
import com.goodfellaz17.infrastructure.persistence.generated.OrderTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin API for monitoring and managing 15k order delivery.
 * 
 * Provides endpoints for:
 * - Order progress tracking
 * - Failed task inspection (dead-letter queue)
 * - Worker metrics
 * - Capacity status
 * 
 * @author goodfellaz17
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/admin")
public class AdminOrderProgressController {
    
    private static final Logger log = LoggerFactory.getLogger(AdminOrderProgressController.class);
    
    private final GeneratedOrderRepository orderRepository;
    private final OrderTaskRepository taskRepository;
    private final OrderDeliveryWorker deliveryWorker;
    private final CapacityService capacityService;
    private final TaskGenerationService taskGenerationService;
    
    public AdminOrderProgressController(
            GeneratedOrderRepository orderRepository,
            OrderTaskRepository taskRepository,
            OrderDeliveryWorker deliveryWorker,
            CapacityService capacityService,
            TaskGenerationService taskGenerationService) {
        this.orderRepository = orderRepository;
        this.taskRepository = taskRepository;
        this.deliveryWorker = deliveryWorker;
        this.capacityService = capacityService;
        this.taskGenerationService = taskGenerationService;
    }
    
    // =========================================================================
    // ORDER PROGRESS
    // =========================================================================
    
    /**
     * GET /api/admin/orders/{id}/progress
     * 
     * Get detailed progress for a specific order including:
     * - Delivered plays
     * - Task breakdown
     * - Failed task count
     * - ETA status
     */
    @GetMapping("/orders/{orderId}/progress")
    public Mono<ResponseEntity<OrderProgressResponse>> getOrderProgress(
            @PathVariable UUID orderId) {
        
        return orderRepository.findById(orderId)
            .flatMap(order -> 
                taskRepository.getProgressSummary(orderId)
                    .defaultIfEmpty(createEmptyProgress(orderId))
                    .map(summary -> buildProgressResponse(order, summary)))
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    /**
     * GET /api/admin/orders/{id}/tasks
     * 
     * Get all tasks for an order with their status.
     */
    @GetMapping("/orders/{orderId}/tasks")
    public Mono<ResponseEntity<OrderTasksResponse>> getOrderTasks(
            @PathVariable UUID orderId) {
        
        return orderRepository.findById(orderId)
            .flatMap(order -> 
                taskRepository.findByOrderIdOrderBySequenceNumberAsc(orderId)
                    .map(this::mapTask)
                    .collectList()
                    .map(tasks -> new OrderTasksResponse(
                        orderId,
                        order.getQuantity(),
                        order.getDelivered(),
                        order.getStatus(),
                        tasks.size(),
                        tasks)))
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    /**
     * GET /api/admin/orders/{id}/failed-tasks
     * 
     * Get permanently failed tasks for an order (dead-letter queue).
     */
    @GetMapping("/orders/{orderId}/failed-tasks")
    public Mono<ResponseEntity<FailedTasksResponse>> getFailedTasks(
            @PathVariable UUID orderId) {
        
        return orderRepository.findById(orderId)
            .flatMap(order -> 
                taskRepository.findFailedPermanentTasksForOrder(orderId)
                    .map(this::mapFailedTask)
                    .collectList()
                    .map(tasks -> {
                        int failedPlays = tasks.stream()
                            .mapToInt(FailedTaskDetail::quantity)
                            .sum();
                        
                        return new FailedTasksResponse(
                            orderId,
                            order.getQuantity(),
                            order.getDelivered(),
                            failedPlays,
                            tasks.size(),
                            tasks);
                    }))
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    // =========================================================================
    // WORKER STATUS
    // =========================================================================
    
    /**
     * GET /api/admin/worker/status
     * 
     * Get current worker metrics and status.
     */
    @GetMapping("/worker/status")
    public Mono<ResponseEntity<WorkerStatusResponse>> getWorkerStatus() {
        OrderDeliveryWorker.WorkerMetrics metrics = deliveryWorker.getMetrics();
        
        return taskRepository.countAllPendingTasks()
            .zipWith(taskRepository.countAllExecutingTasks())
            .map(tuple -> new WorkerStatusResponse(
                metrics.workerId(),
                metrics.isRunning(),
                metrics.totalProcessed(),
                metrics.totalCompleted(),
                metrics.totalFailed(),
                metrics.transientFailures(),
                metrics.permanentFailures(),
                metrics.totalRetries(),
                metrics.recoveredOrphans(),
                metrics.tasksRecoveredAfterStart(),
                metrics.activeCount(),
                tuple.getT1(),  // pendingTasks
                tuple.getT2(), // executingTasks
                metrics.workerStartTime(),
                Instant.now()))
            .map(ResponseEntity::ok);
    }
    
    /**
     * GET /api/admin/worker/metrics
     * 
     * Get lightweight worker metrics (no DB queries).
     * This endpoint is cheap to call frequently for monitoring.
     * 
     * Example:
     * curl http://localhost:8080/api/admin/worker/metrics | jq .
     */
    @GetMapping("/worker/metrics")
    public ResponseEntity<WorkerMetricsResponse> getWorkerMetrics() {
        OrderDeliveryWorker.WorkerMetrics m = deliveryWorker.getMetrics();
        return ResponseEntity.ok(new WorkerMetricsResponse(
            m.workerId(),
            m.totalProcessed(),
            m.totalCompleted(),
            m.totalFailed(),
            m.transientFailures(),
            m.permanentFailures(),
            m.totalRetries(),
            m.recoveredOrphans(),
            m.tasksRecoveredAfterStart(),
            m.activeCount(),
            m.isRunning(),
            m.workerStartTime(),
            Duration.between(m.workerStartTime(), Instant.now()).toSeconds()
        ));
    }
    
    /**
     * GET /api/admin/orders/{orderId}/debug
     * 
     * Get detailed debug view of an order including:
     * - Order fields (id, status, quantity, delivered, remains, timestamps)
     * - Task status counts
     * - Last 5 tasks with status and last_error
     * 
     * Example:
     * curl http://localhost:8080/api/admin/orders/{orderId}/debug | jq .
     */
    @GetMapping("/orders/{orderId}/debug")
    public Mono<ResponseEntity<OrderDebugResponse>> getOrderDebug(
            @PathVariable UUID orderId) {
        
        return orderRepository.findById(orderId)
            .flatMap(order -> 
                // Get task counts by status
                taskRepository.findByOrderIdOrderBySequenceNumberAsc(orderId)
                    .collectList()
                    .map(tasks -> {
                        // Count by status
                        Map<String, Long> statusCounts = tasks.stream()
                            .collect(java.util.stream.Collectors.groupingBy(
                                OrderTaskEntity::getStatus,
                                java.util.stream.Collectors.counting()));
                        
                        // Last 5 tasks (most recent by sequence)
                        List<TaskDebugInfo> lastTasks = tasks.stream()
                            .sorted((a, b) -> Integer.compare(b.getSequenceNumber(), a.getSequenceNumber()))
                            .limit(5)
                            .map(t -> new TaskDebugInfo(
                                t.getId(),
                                t.getSequenceNumber(),
                                t.getQuantity(),
                                t.getStatus(),
                                t.getAttempts(),
                                t.getLastError(),
                                t.getExecutionStartedAt()))
                            .toList();
                        
                        return new OrderDebugResponse(
                            order.getId(),
                            order.getStatus(),
                            order.getQuantity(),
                            order.getDelivered(),
                            order.getRemains(),
                            order.getFailedPermanentPlays(),
                            order.getCreatedAt(),
                            order.getStartedAt(),
                            order.getCompletedAt(),
                            order.getEstimatedCompletionAt(),
                            tasks.size(),
                            statusCounts,
                            lastTasks);
                    }))
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    // =========================================================================
    // CAPACITY STATUS
    // =========================================================================
    
    /**
     * GET /api/admin/capacity-status
     * 
     * Get current system capacity and can-accept status.
     * Note: /api/admin/capacity is handled by CapacityAdminController for detailed capacity management.
     */
    @GetMapping("/capacity-status")
    public Mono<ResponseEntity<CapacityStatusResponse>> getCapacityStatus() {
        return capacityService.calculateCurrentCapacity()
            .flatMap(snapshot -> 
                capacityService.calculatePendingLoad()
                    .map(pendingLoad -> new CapacityStatusResponse(
                        snapshot.totalPlaysPerHour(),
                        snapshot.max48hCapacity(),
                        snapshot.max72hCapacity(),
                        pendingLoad,
                        snapshot.max72hCapacity() - pendingLoad,
                        snapshot.healthyProxyCount(),
                        snapshot.totalProxyCount(),
                        snapshot.canAccept15kOrder(),
                        snapshot.calculatedAt())))
            .map(ResponseEntity::ok);
    }
    
    /**
     * GET /api/admin/capacity/preview/{quantity}
     * 
     * Preview task distribution for a specific quantity.
     */
    @GetMapping("/capacity/preview/{quantity}")
    public Mono<ResponseEntity<TaskDistributionPreview>> previewTaskDistribution(
            @PathVariable int quantity) {
        
        return taskGenerationService.calculateTaskDistribution(quantity)
            .map(dist -> new TaskDistributionPreview(
                quantity,
                dist.canAccept(),
                dist.taskCount(),
                dist.taskSize(),
                dist.windowHours(),
                dist.estimatedHours(),
                dist.rejectionReason()))
            .map(ResponseEntity::ok);
    }
    
    // =========================================================================
    // GLOBAL DEAD LETTER QUEUE
    // =========================================================================
    
    /**
     * GET /api/admin/dead-letter-queue
     * 
     * Get all permanently failed tasks across all orders.
     */
    @GetMapping("/dead-letter-queue")
    public Mono<ResponseEntity<DeadLetterQueueResponse>> getDeadLetterQueue(
            @RequestParam(defaultValue = "100") int limit) {
        
        return taskRepository.findAllFailedPermanentTasks(limit)
            .map(this::mapFailedTask)
            .collectList()
            .map(tasks -> {
                int totalFailedPlays = tasks.stream()
                    .mapToInt(FailedTaskDetail::quantity)
                    .sum();
                
                return new DeadLetterQueueResponse(
                    tasks.size(),
                    totalFailedPlays,
                    tasks);
            })
            .map(ResponseEntity::ok);
    }
    
    // =========================================================================
    // HELPERS
    // =========================================================================
    
    private OrderProgressResponse buildProgressResponse(
            OrderEntity order, 
            OrderTaskRepository.TaskProgressSummary summary) {
        
        double progressPercent = order.getQuantity() > 0 
            ? (double) order.getDelivered() / order.getQuantity() * 100.0 
            : 0.0;
        
        boolean onSchedule = true;
        if (order.getEstimatedCompletionAt() != null && 
            !order.getStatus().equals("COMPLETED")) {
            onSchedule = Instant.now().isBefore(order.getEstimatedCompletionAt());
        }
        
        return new OrderProgressResponse(
            order.getId(),
            order.getQuantity(),
            order.getDelivered(),
            order.getRemains(),
            order.getFailedPermanentPlays() != null ? order.getFailedPermanentPlays() : 0,
            progressPercent,
            order.getStatus(),
            order.getUsesTaskDelivery() != null && order.getUsesTaskDelivery(),
            summary.getTotalTasks() != null ? summary.getTotalTasks().intValue() : 0,
            summary.getCompletedTasks() != null ? summary.getCompletedTasks().intValue() : 0,
            summary.getFailedPermanentTasks() != null ? summary.getFailedPermanentTasks().intValue() : 0,
            summary.getActiveTasks() != null ? summary.getActiveTasks().intValue() : 0,
            order.getCreatedAt(),
            order.getStartedAt(),
            order.getEstimatedCompletionAt(),
            order.getCompletedAt(),
            onSchedule);
    }
    
    private OrderTaskRepository.TaskProgressSummary createEmptyProgress(UUID orderId) {
        return new OrderTaskRepository.TaskProgressSummary() {
            @Override public UUID getOrderId() { return orderId; }
            @Override public Long getTotalTasks() { return 0L; }
            @Override public Long getTotalQuantity() { return 0L; }
            @Override public Long getDeliveredQuantity() { return 0L; }
            @Override public Long getCompletedTasks() { return 0L; }
            @Override public Long getFailedPermanentTasks() { return 0L; }
            @Override public Long getFailedPermanentQuantity() { return 0L; }
            @Override public Long getActiveTasks() { return 0L; }
        };
    }
    
    private TaskDetail mapTask(OrderTaskEntity task) {
        return new TaskDetail(
            task.getId(),
            task.getSequenceNumber(),
            task.getQuantity(),
            task.getStatus(),
            task.getAttempts(),
            task.getMaxAttempts(),
            task.getLastError(),
            task.getScheduledAt(),
            task.getExecutedAt(),
            task.getRetryAfter());
    }
    
    private FailedTaskDetail mapFailedTask(OrderTaskEntity task) {
        return new FailedTaskDetail(
            task.getId(),
            task.getOrderId(),
            task.getSequenceNumber(),
            task.getQuantity(),
            task.getAttempts(),
            task.getLastError(),
            task.getProxyNodeId(),
            task.getCreatedAt(),
            task.getExecutionStartedAt());
    }
    
    // =========================================================================
    // RESPONSE DTOs
    // =========================================================================
    
    public record OrderProgressResponse(
        UUID orderId,
        int totalQuantity,
        int delivered,
        int remains,
        int failedPermanent,
        double progressPercent,
        String status,
        boolean usesTaskDelivery,
        int totalTasks,
        int completedTasks,
        int failedTasks,
        int activeTasks,
        Instant createdAt,
        Instant startedAt,
        Instant estimatedCompletionAt,
        Instant completedAt,
        boolean onSchedule
    ) {}
    
    public record OrderTasksResponse(
        UUID orderId,
        int totalQuantity,
        int delivered,
        String status,
        int taskCount,
        List<TaskDetail> tasks
    ) {}
    
    public record TaskDetail(
        UUID id,
        int sequenceNumber,
        int quantity,
        String status,
        int attempts,
        int maxAttempts,
        String lastError,
        Instant scheduledAt,
        Instant executedAt,
        Instant retryAfter
    ) {}
    
    public record FailedTasksResponse(
        UUID orderId,
        int totalQuantity,
        int delivered,
        int failedPlays,
        int failedTaskCount,
        List<FailedTaskDetail> failedTasks
    ) {}
    
    public record FailedTaskDetail(
        UUID taskId,
        UUID orderId,
        int sequenceNumber,
        int quantity,
        int attempts,
        String lastError,
        UUID lastProxyNodeId,
        Instant createdAt,
        Instant lastExecutionAt
    ) {}
    
    public record WorkerStatusResponse(
        String workerId,
        boolean isRunning,
        long totalProcessed,
        long totalCompleted,
        long totalFailed,
        long transientFailures,
        long permanentFailures,
        long totalRetries,
        long recoveredOrphans,
        long tasksRecoveredAfterStart,
        int activeCount,
        long pendingTasks,
        long executingTasks,
        Instant workerStartTime,
        Instant timestamp
    ) {}
    
    public record CapacityStatusResponse(
        int playsPerHour,
        int max48hCapacity,
        int max72hCapacity,
        int pendingLoad,
        int availableCapacity,
        int healthyProxyCount,
        int totalProxyCount,
        boolean canAccept15k,
        Instant calculatedAt
    ) {}
    
    public record TaskDistributionPreview(
        int quantity,
        boolean canAccept,
        int taskCount,
        int taskSize,
        int windowHours,
        double estimatedHours,
        String rejectionReason
    ) {}
    
    public record DeadLetterQueueResponse(
        int totalFailedTasks,
        int totalFailedPlays,
        List<FailedTaskDetail> tasks
    ) {}
    
    public record WorkerMetricsResponse(
        String workerId,
        long totalProcessed,
        long totalCompleted,
        long totalFailed,
        long transientFailures,
        long permanentFailures,
        long totalRetries,
        long recoveredOrphans,
        long tasksRecoveredAfterStart,
        int activeCount,
        boolean isRunning,
        Instant workerStartTime,
        long uptimeSeconds
    ) {}
    
    public record OrderDebugResponse(
        UUID orderId,
        String status,
        int quantity,
        int delivered,
        int remains,
        Integer failedPermanentPlays,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant estimatedCompletionAt,
        int totalTasks,
        Map<String, Long> taskStatusCounts,
        List<TaskDebugInfo> lastTasks
    ) {}
    
    public record TaskDebugInfo(
        UUID taskId,
        int sequenceNumber,
        int quantity,
        String status,
        int attempts,
        String lastError,
        Instant executionStartedAt
    ) {}
}
