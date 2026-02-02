package com.goodfellaz17.order.controller;

import com.goodfellaz17.order.domain.Order;
import com.goodfellaz17.order.domain.OrderTask;
import com.goodfellaz17.order.metrics.OrderMetrics;
import com.goodfellaz17.order.repository.PlayOrderTaskRepository;
import com.goodfellaz17.order.service.OrderServiceFacade;
import com.goodfellaz17.order.service.TaskExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.UUID;

/**
 * OrderController: REST API for order pipeline management.
 * FULLY REACTIVE - All methods return Mono<ResponseEntity<>> or Flux<>
 *
 * Endpoints:
 *   POST   /api/orders/create       → Mono<ResponseEntity<OrderResponse>>
 *   GET    /api/orders/{id}         → Mono<ResponseEntity<OrderDetailResponse>>
 *   GET    /api/orders/metrics      → Mono<ResponseEntity<DeliveryMetrics>>
 *   GET    /api/orders/pending-tasks → Flux<OrderTask>
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderServiceFacade facade;
    private final TaskExecutionService taskExecutionService;
    private final PlayOrderTaskRepository taskRepository;

    public OrderController(
            OrderServiceFacade facade,
            TaskExecutionService taskExecutionService,
            PlayOrderTaskRepository taskRepository) {
        this.facade = facade;
        this.taskExecutionService = taskExecutionService;
        this.taskRepository = taskRepository;
    }

    /**
     * POST /api/orders/create
     * Create a new order and decompose into tasks.
     * Returns Mono<ResponseEntity<OrderResponse>> - fully async, no blocking.
     *
     * Request body:
     * {
     *   "trackId": "spotify:track:...",
     *   "quantity": 50,
     *   "accountIds": ["account1", "account2", ...]
     * }
     */
    @PostMapping("/create")
    public Mono<ResponseEntity<OrderResponse>> createOrder(@RequestBody CreateOrderRequest request) {
        // Validate request synchronously (no I/O)
        String validationError = validateCreateOrderRequest(request);
        if (validationError != null) {
            return Mono.just(ResponseEntity.badRequest().body(
                new OrderResponse(null, "error", validationError, null)
            ));
        }

        // Create order asynchronously with facade
        return facade.createOrderWithAudit(
                request.getTrackId(),
                request.getQuantity(),
                request.getAccountIds()
            )
            .map(order -> ResponseEntity.status(HttpStatus.CREATED).body(
                new OrderResponse(order.getId(), "success", "Order created", new OrderDTO(order))
            ))
            .onErrorResume(e -> Mono.just(
                ResponseEntity.badRequest().body(
                    new OrderResponse(null, "error", e.getMessage(), null)
                )
            ));
    }

    /**
     * GET /api/orders/{id}
     * Get order details and all associated tasks.
     * Returns Mono<ResponseEntity<OrderDetailResponse>> - fully async.
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<OrderDetailResponse>> getOrder(@PathVariable UUID id) {
        return facade.getOrderWithAudit(id)
            .flatMap(order ->
                facade.getOrderTasksWithAudit(id)
                    .collectList()
                    .map(tasks -> new OrderDetailResponse(order, tasks))
                    .map(ResponseEntity::ok)
            )
            .onErrorResume(e -> Mono.just(
                ResponseEntity.notFound().build()
            ));
    }

    /**
     * GET /api/orders/metrics
     * Get pipeline health metrics.
     * Returns Mono<ResponseEntity<OrderMetrics>> - fully async.
     */
    @GetMapping("/metrics")
    public Mono<ResponseEntity<OrderMetrics>> getMetrics() {
        return facade.getMetricsWithAudit()
            .map(ResponseEntity::ok)
            .onErrorResume(e -> Mono.just(
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
            ));
    }

    /**
     * GET /api/orders/pending-tasks
     * Get all pending tasks awaiting assignment.
     * Returns Flux<OrderTask> - streams results, no collection into List.
     */
    @GetMapping("/pending-tasks")
    public Flux<OrderTask> getPendingTasks() {
        return facade.getPendingTasksWithAudit()
            .onErrorResume(e -> {
                // Log and return empty on error
                return Flux.empty();
            });
    }

    /**
     * POST /api/orders/{orderId}/tasks/{taskId}/execute
     * Execute a task immediately (for testing/manual trigger).
     * Fetches task, calls TaskExecutionService, returns updated task with final status.
     *
     * @param orderId Parent order ID (for safety validation)
     * @param taskId Task to execute
     * @return Mono<ResponseEntity<OrderTaskResponse>> with updated task state
     */
    @PostMapping("/{orderId}/tasks/{taskId}/execute")
    public Mono<ResponseEntity<OrderTaskResponse>> executeTask(
            @PathVariable UUID orderId,
            @PathVariable UUID taskId) {

        return taskRepository.findById(taskId)
            .switchIfEmpty(Mono.error(new NotFoundException("Task not found: " + taskId)))
            .flatMap(task -> {
                // Safety check: ensure task belongs to order
                if (!task.getOrderId().equals(orderId)) {
                    return Mono.error(new IllegalArgumentException(
                        "Task " + taskId + " does not belong to order " + orderId));
                }
                // Execute the task
                return taskExecutionService.executeTask(task);
            })
            .map(updated -> ResponseEntity.ok(new OrderTaskResponse(updated)))
            .onErrorResume(NotFoundException.class, e ->
                Mono.just(ResponseEntity.notFound().build()))
            .onErrorResume(IllegalArgumentException.class, e ->
                Mono.just(ResponseEntity.badRequest().build()))
            .onErrorResume(e ->
                Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()));
    }

    // ============ Helpers ============

    /**
     * Validate create order request. Returns null if valid, error message if invalid.
     */
    private String validateCreateOrderRequest(CreateOrderRequest request) {
        if (request.getTrackId() == null || request.getTrackId().isEmpty()) {
            return "Track ID is required";
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            return "Quantity must be > 0";
        }
        if (request.getAccountIds() == null || request.getAccountIds().isEmpty()) {
            return "At least one account required";
        }
        if (request.getAccountIds().size() != request.getQuantity()) {
            return "Account count must match quantity";
        }
        return null;
    }

    // ============ DTOs ============

    /**
     * Request body for creating an order.
     */
    public static class CreateOrderRequest {
        private String trackId;
        private Integer quantity;
        private List<String> accountIds;

        public String getTrackId() { return trackId; }
        public void setTrackId(String trackId) { this.trackId = trackId; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public List<String> getAccountIds() { return accountIds; }
        public void setAccountIds(List<String> accountIds) { this.accountIds = accountIds; }
    }

    /**
     * Response body for order operations.
     */
    public static class OrderResponse {
        private UUID orderId;
        private String status;
        private String message;
        private OrderDTO order;

        public OrderResponse(UUID orderId, String status, String message, OrderDTO order) {
            this.orderId = orderId;
            this.status = status;
            this.message = message;
            this.order = order;
        }

        public UUID getOrderId() { return orderId; }
        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public OrderDTO getOrder() { return order; }
    }

    /**
     * DTO for Order - excludes the @Transient tasks field
     */
    public static class OrderDTO {
        private UUID id;
        private String trackId;
        private Integer quantity;
        private String status;
        private Integer playsDelivered;
        private Integer playsFailed;
        private String failureReason;
        private Instant createdAt;
        private Instant completedAt;

        public OrderDTO() {}
        public OrderDTO(Order order) {
            this.id = order.getId();
            this.trackId = order.getTrackId();
            this.quantity = order.getQuantity();
            this.status = order.getStatus().toString();
            this.playsDelivered = order.getPlaysDelivered();
            this.playsFailed = order.getPlaysFailed();
            this.failureReason = order.getFailureReason();
            this.createdAt = order.getCreatedAt();
            this.completedAt = order.getCompletedAt();
        }

        public UUID getId() { return id; }
        public String getTrackId() { return trackId; }
        public Integer getQuantity() { return quantity; }
        public String getStatus() { return status; }
        public Integer getPlaysDelivered() { return playsDelivered; }
        public Integer getPlaysFailed() { return playsFailed; }
        public String getFailureReason() { return failureReason; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getCompletedAt() { return completedAt; }
    }

    /**
     * Response body for detailed order information.
     */
    public static class OrderDetailResponse {
        private Order order;
        private List<OrderTask> tasks;
        private Integer taskCount;
        private Integer completedTasks;
        private Integer failedTasks;

        public OrderDetailResponse(Order order, List<OrderTask> tasks) {
            this.order = order;
            this.tasks = tasks;
            this.taskCount = tasks.size();
            this.completedTasks = (int) tasks.stream()
                .filter(t -> t.getStatus().equals("COMPLETED"))
                .count();
            this.failedTasks = (int) tasks.stream()
                .filter(t -> t.getStatus().equals("FAILED"))
                .count();
        }

        public Order getOrder() { return order; }
        public List<OrderTask> getTasks() { return tasks; }
        public Integer getTaskCount() { return taskCount; }
        public Integer getCompletedTasks() { return completedTasks; }
        public Integer getFailedTasks() { return failedTasks; }
    }

    /**
     * Response body for task execution result.
     */
    public static class OrderTaskResponse {
        private UUID taskId;
        private UUID orderId;
        private String accountId;
        private String status;
        private String failureReason;
        private Integer retryCount;
        private String assignedProxyNode;
        private Instant startedAt;
        private Instant completedAt;

        public OrderTaskResponse() {}
        public OrderTaskResponse(OrderTask task) {
            this.taskId = task.getId();
            this.orderId = task.getOrderId();
            this.accountId = task.getAccountId();
            this.status = task.getStatus();
            this.failureReason = task.getFailureReason();
            this.retryCount = task.getRetryCount();
            this.assignedProxyNode = task.getAssignedProxyNode();
            this.startedAt = task.getStartedAt();
            this.completedAt = task.getCompletedAt();
        }

        public UUID getTaskId() { return taskId; }
        public UUID getOrderId() { return orderId; }
        public String getAccountId() { return accountId; }
        public String getStatus() { return status; }
        public String getFailureReason() { return failureReason; }
        public Integer getRetryCount() { return retryCount; }
        public String getAssignedProxyNode() { return assignedProxyNode; }
        public Instant getStartedAt() { return startedAt; }
        public Instant getCompletedAt() { return completedAt; }
    }
}

// Custom exception for not found
class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
