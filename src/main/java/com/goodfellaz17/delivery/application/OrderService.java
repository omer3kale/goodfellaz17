package com.goodfellaz17.delivery.application;

import com.goodfellaz17.infrastructure.persistence.entity.OrderEntity;
import com.goodfellaz17.infrastructure.persistence.entity.OrderTaskEntity;
import com.goodfellaz17.infrastructure.persistence.repository.OrderRepository;
import com.goodfellaz17.infrastructure.persistence.repository.OrderTaskRepository;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * OrderService — Application layer for Order orchestration.
 * Handles order creation, task decomposition, and status querying.
 *
 * Invariant: For each Order, quantity == SUM(order_tasks.quantity WHERE order_id = order.id)
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderTaskRepository orderTaskRepository;

    public OrderService(OrderRepository orderRepository, OrderTaskRepository orderTaskRepository) {
        this.orderRepository = orderRepository;
        this.orderTaskRepository = orderTaskRepository;
    }

    /**
     * Create a new order and decompose it into tasks.
     *
     * @param request Create request with tenantId, serviceId, quantity, trackId, artistId
     * @return Mono containing the created order
     */
    public Mono<OrderEntity> createOrder(OrderCreateRequest request) {
        // Validate request
        if (request.getTenantId() == null) {
            return Mono.error(new IllegalArgumentException("tenantId is required"));
        }
        if (request.getServiceId() == null) {
            return Mono.error(new IllegalArgumentException("serviceId is required"));
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            return Mono.error(new IllegalArgumentException("quantity must be > 0"));
        }

        // Create order entity
        OrderEntity order = new OrderEntity(request.getTenantId(), request.getServiceId(), request.getQuantity());
        order.setTrackId(request.getTrackId());
        order.setArtistId(request.getArtistId());
        order.setEstimatedCost(request.getEstimatedCost() != null ? request.getEstimatedCost() : BigDecimal.ZERO);

        // Persist order
        return orderRepository.save(order)
                .flatMap(savedOrder -> {
                    // Decompose into tasks (for now: 3 tasks, evenly distributed or simple split)
                    return createOrderTasks(savedOrder)
                            .then(Mono.just(savedOrder));
                });
    }

    /**
     * Create OrderTasks for a given order.
     * Decomposition strategy: Spread quantity evenly across N tasks (currently 3).
     *
     * @param order The order to decompose
     * @return Mono<Void> when all tasks are persisted
     */
    private Mono<Void> createOrderTasks(OrderEntity order) {
        int taskCount = 3;
        int quantityPerTask = order.getQuantity() / taskCount;
        int remainder = order.getQuantity() % taskCount;

        // Create tasks
        return reactor.core.publisher.Flux.fromIterable(
                java.util.stream.IntStream.range(0, taskCount)
                        .mapToObj(i -> {
                            int qty = quantityPerTask + (i == taskCount - 1 ? remainder : 0);
                            OrderTaskEntity task = new OrderTaskEntity(
                                    UUID.randomUUID(),
                                    order.getId(),
                                    order.getTenantId(),
                                    qty
                            );
                            return task;
                        })
                        .toList())
                .flatMap(orderTaskRepository::save)
                .then();
    }

    /**
     * Fetch an order with aggregated task statistics.
     *
     * @param orderId Order UUID
     * @return Mono containing order response with stats
     */
    public Mono<OrderResponse> getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .flatMap(order ->
                        // Aggregate task stats
                        orderTaskRepository.findByOrderId(orderId)
                                .collectList()
                                .map(tasks -> {
                                    int totalTasks = tasks.size();
                                    long pendingCount = tasks.stream().filter(t -> "PENDING".equals(t.getStatus())).count();
                                    long assignedCount = tasks.stream().filter(t -> "ASSIGNED".equals(t.getStatus())).count();
                                    long executingCount = tasks.stream().filter(t -> "EXECUTING".equals(t.getStatus())).count();
                                    long completedCount = tasks.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
                                    long failedCount = tasks.stream().filter(t -> "FAILED".equals(t.getStatus())).count();

                                    return new OrderResponse(
                                            order,
                                            totalTasks,
                                            (int) pendingCount,
                                            (int) assignedCount,
                                            (int) executingCount,
                                            (int) completedCount,
                                            (int) failedCount
                                    );
                                })
                )
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Order not found: " + orderId)));
    }

    /**
     * OrderCreateRequest DTO.
     */
    public static class OrderCreateRequest {
        @NotNull(message = "tenantId is required")
        private UUID tenantId;

        @NotNull(message = "serviceId is required")
        private UUID serviceId;

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be > 0")
        private Integer quantity;

        private String trackId;
        private String artistId;
        private BigDecimal estimatedCost;

        // Default constructor for JSON deserialization
        public OrderCreateRequest() {
        }

        public UUID getTenantId() { return tenantId; }
        public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

        public UUID getServiceId() { return serviceId; }
        public void setServiceId(UUID serviceId) { this.serviceId = serviceId; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public String getTrackId() { return trackId; }
        public void setTrackId(String trackId) { this.trackId = trackId; }

        public String getArtistId() { return artistId; }
        public void setArtistId(String artistId) { this.artistId = artistId; }

        public BigDecimal getEstimatedCost() { return estimatedCost; }
        public void setEstimatedCost(BigDecimal estimatedCost) { this.estimatedCost = estimatedCost; }
    }

    /**
     * OrderResponse DTO with aggregated task stats.
     */
    public static class OrderResponse {
        private OrderEntity order;
        private int taskCount;
        private int pendingTasks;
        private int assignedTasks;
        private int executingTasks;
        private int completedTasks;
        private int failedTasks;

        // Default constructor for JSON serialization
        public OrderResponse() {
        }

        public OrderResponse(OrderEntity order, int taskCount, int pendingTasks, int assignedTasks,
                             int executingTasks, int completedTasks, int failedTasks) {
            this.order = order;
            this.taskCount = taskCount;
            this.pendingTasks = pendingTasks;
            this.assignedTasks = assignedTasks;
            this.executingTasks = executingTasks;
            this.completedTasks = completedTasks;
            this.failedTasks = failedTasks;
        }

        public OrderEntity getOrder() { return order; }
        public void setOrder(OrderEntity order) { this.order = order; }

        public int getTaskCount() { return taskCount; }
        public void setTaskCount(int taskCount) { this.taskCount = taskCount; }

        public int getPendingTasks() { return pendingTasks; }
        public void setPendingTasks(int pendingTasks) { this.pendingTasks = pendingTasks; }

        public int getAssignedTasks() { return assignedTasks; }
        public void setAssignedTasks(int assignedTasks) { this.assignedTasks = assignedTasks; }

        public int getExecutingTasks() { return executingTasks; }
        public void setExecutingTasks(int executingTasks) { this.executingTasks = executingTasks; }

        public int getCompletedTasks() { return completedTasks; }
        public void setCompletedTasks(int completedTasks) { this.completedTasks = completedTasks; }

        public int getFailedTasks() { return failedTasks; }
        public void setFailedTasks(int failedTasks) { this.failedTasks = failedTasks; }

        public int getProgress() {
            if (taskCount == 0) return 0;
            return (completedTasks * 100) / taskCount;
        }
    }
}
