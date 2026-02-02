package com.goodfellaz17.order.service;

import com.goodfellaz17.order.domain.Order;
import com.goodfellaz17.order.domain.OrderTask;
import com.goodfellaz17.order.metrics.OrderMetrics;
import com.goodfellaz17.order.repository.PlayOrderRepository;
import com.goodfellaz17.order.repository.PlayOrderTaskRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * OrderOrchestrator: Core pipeline coordinator (FULLY REACTIVE).
 * Manages order lifecycle: creation → task decomposition → execution → completion.
 *
 * ALL methods return Mono/Flux: no blocking, fully async, integrates cleanly with WebFlux.
 */
@Service
public class OrderOrchestrator {

    private final PlayOrderRepository orderRepository;
    private final PlayOrderTaskRepository taskRepository;
    private final OrderTaskFactory taskFactory;

    public OrderOrchestrator(PlayOrderRepository orderRepository,
                            PlayOrderTaskRepository taskRepository,
                            OrderTaskFactory taskFactory) {
        this.orderRepository = orderRepository;
        this.taskRepository = taskRepository;
        this.taskFactory = taskFactory;
    }

    /**
     * Create a new order: entry point for the pipeline.
     *
     * Fully reactive:
     * 1. Save order (Mono)
     * 2. Create tasks from decomposition (List, in-memory)
     * 3. Save all tasks (Flux → collected to List)
     * 4. Mark order as ACTIVE
     * 5. Return order (without populating tasks field - load tasks separately via repository)
     */
    public Mono<Order> createOrder(String trackId, Integer quantity, List<String> accountIds) {
        // Validate input synchronously (fast path, no I/O)
        return Mono.fromRunnable(() -> validateOrderInput(trackId, quantity, accountIds))
            .then(
                // Build and save order using custom INSERT (not save() which tries UPDATE)
                Mono.fromCallable(() -> {
                    Order order = new Order();  // Constructor initializes status=PENDING, timestamps
                    order.setId(UUID.randomUUID());  // Generate UUID before first save
                    order.setTrackId(trackId);
                    order.setQuantity(quantity);
                    return order;
                })
                .flatMap(order ->
                    orderRepository.insertOrder(
                        order.getId(),
                        order.getTrackId(),
                        order.getQuantity(),
                        order.getStatus(),
                        order.getPlaysDelivered(),
                        order.getPlaysFailed(),
                        order.getCreatedAt(),
                        order.getLastUpdatedAt()
                    ).then(Mono.just(order))
                )
                .flatMap(savedOrder ->
                    // Decompose into tasks and insert them
                    Mono.fromCallable(() -> taskFactory.createTasksForOrder(savedOrder, accountIds))
                        .flatMapMany(Flux::fromIterable)
                        .flatMap(task -> taskRepository.insertTask(
                            task.getId(),
                            task.getOrderId(),
                            task.getAccountId(),
                            task.getStatus(),
                            task.getCreatedAt(),
                            task.getRetryCount(),
                            task.getMaxRetries()
                        ))
                        .then(Mono.just(savedOrder))
                )
                // Mark order as ACTIVE (final save)
                .flatMap(order -> {
                    order.setStatus("ACTIVE");
                    // Use a direct UPDATE since the order already exists from insertOrder
                    return orderRepository.save(order);
                })
            );
    }

    /**
     * Record successful task delivery.
     * Automatically completes order if all tasks are done.
     */
    public Mono<Void> recordTaskDelivery(UUID taskId, String proxyNodeId) {
        return taskRepository.findById(taskId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Task not found: " + taskId)))
            .flatMap(task -> {
                task.setStatus("COMPLETED");
                task.setCompletedAt(Instant.now());
                task.setAssignedProxyNode(proxyNodeId);
                return taskRepository.save(task)
                    .then(orderRepository.findById(task.getOrderId()))
                    .flatMap(order -> {
                        order.recordSuccess();
                        return checkAndUpdateOrderStatus(order);
                    })
                    .then();
            });
    }

    /**
     * Record task failure.
     * If retries available, task remains in system for retry.
     * Otherwise, mark as FAILED.
     */
    public Mono<Void> recordTaskFailure(UUID taskId, String reason) {
        return taskRepository.findById(taskId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Task not found: " + taskId)))
            .flatMap(task -> {
                task.incrementRetry();

                if (task.canRetry()) {
                    task.setStatus("PENDING");
                    task.setAssignedProxyNode(null);
                    task.setFailureReason(reason + " (retry " + task.getRetryCount() + ")");
                } else {
                    task.setStatus("FAILED");
                    task.setCompletedAt(Instant.now());
                    task.setFailureReason(reason + " (retries exhausted)");
                }

                return taskRepository.save(task)
                    .then(orderRepository.findById(task.getOrderId()))
                    .flatMap(order -> {
                        order.recordFailure(reason);
                        return checkAndUpdateOrderStatus(order);
                    })
                    .then();
            });
    }

    /**
     * Assign a task to a proxy node.
     * Marks task as ASSIGNED and ready for execution.
     */
    public Mono<Void> assignTask(UUID taskId, String proxyNodeId) {
        return taskRepository.findById(taskId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Task not found: " + taskId)))
            .flatMap(task -> {
                task.setAssignedProxyNode(proxyNodeId);
                task.setStatus("ASSIGNED");
                task.setStartedAt(Instant.now());
                return taskRepository.save(task).then();
            });
    }

    /**
     * Get complete metrics for the pipeline.
     * Reactive: aggregates counts from all status queries using combiner function.
     */
    public Mono<OrderMetrics> getMetrics() {
        return Mono.zip(
            java.util.Arrays.asList(
                orderRepository.count(),
                orderRepository.countByStatus("COMPLETED"),
                orderRepository.countByStatus("FAILED"),
                orderRepository.countByStatus("PENDING"),
                orderRepository.countByStatus("ACTIVE"),
                orderRepository.countByStatus("DELIVERING"),
                taskRepository.count(),
                taskRepository.countByStatus("COMPLETED"),
                taskRepository.countByStatus("FAILED"),
                taskRepository.countByStatus("ASSIGNED"),
                taskRepository.getAverageRetries(),
                orderRepository.sumPlaysDelivered(),
                orderRepository.sumPlaysFailed()
            ),
            array -> {
                OrderMetrics metrics = new OrderMetrics();

                metrics.setTotalOrders((Long) array[0]);
                metrics.setCompletedOrders((Long) array[1]);
                metrics.setFailedOrders((Long) array[2]);
                metrics.setPendingOrders((Long) array[3]);
                metrics.setActiveOrders((Long) array[4] + (Long) array[5]);

                metrics.setTotalTasks(Math.toIntExact((Long) array[6]));
                metrics.setCompletedTasks(Math.toIntExact((Long) array[7]));
                metrics.setFailedTasks(Math.toIntExact((Long) array[8]));
                metrics.setAssignedTasks(Math.toIntExact((Long) array[9]));

                int total = metrics.getTotalTasks();
                if (total > 0) {
                    double successRate = (double) metrics.getCompletedTasks() / total;
                    metrics.setOverallSuccessRate(successRate);
                } else {
                    metrics.setOverallSuccessRate(0.0);
                }

                metrics.setAvgRetries(array[10] != null ? (Double) array[10] : 0.0);
                metrics.setTotalPlaysDelivered((Long) array[11]);
                metrics.setTotalPlaysFailed((Long) array[12]);

                metrics.calculateAndUpdateStatus();

                return metrics;
            }
        );
    }

    /**
     * Get order by ID with all tasks.
     */
    public Mono<Order> getOrder(UUID orderId) {
        return orderRepository.findById(orderId);
    }

    /**
     * Get all tasks for an order.
     */
    public Flux<OrderTask> getOrderTasks(UUID orderId) {
        return taskRepository.findByOrderId(orderId);
    }

    /**
     * Get pending tasks ready for assignment.
     */
    public Flux<OrderTask> getPendingTasks() {
        return taskRepository.findByStatusOrderByCreatedAtAsc("PENDING");
    }

    /**
     * Check if order is complete and update status accordingly.
     * Called after task completion/failure.
     *
     * Reactive: collects all tasks, checks status, updates if needed.
     */
    private Mono<Order> checkAndUpdateOrderStatus(Order order) {
        return getOrderTasks(order.getId())
            .collectList()
            .flatMap(tasks -> {
                if (tasks.isEmpty()) {
                    return Mono.just(order);
                }

                // Check if all tasks are done
                boolean allDone = tasks.stream()
                    .allMatch(t -> t.getStatus().equals("COMPLETED") ||
                                  t.getStatus().equals("FAILED"));

                if (!allDone) {
                    // Still tasks pending/assigned/executing
                    if (!order.getStatus().equals("DELIVERING")) {
                        order.setStatus("DELIVERING");
                    }
                    return orderRepository.save(order);
                }

                // All tasks done: check success
                boolean anyFailed = tasks.stream()
                    .anyMatch(t -> t.getStatus().equals("FAILED"));

                if (anyFailed) {
                    order.setStatus("FAILED");
                    order.setFailureReason("Some tasks failed after retries");
                } else {
                    order.setStatus("COMPLETED");
                }

                order.setCompletedAt(Instant.now());
                return orderRepository.save(order);
            });
    }

    /**
     * Synchronous validation (no I/O, fast).
     */
    private void validateOrderInput(String trackId, Integer quantity, List<String> accountIds) {
        if (trackId == null || trackId.isEmpty()) {
            throw new IllegalArgumentException("Track ID cannot be empty");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be > 0");
        }
        if (accountIds == null || accountIds.isEmpty()) {
            throw new IllegalArgumentException("At least one account required");
        }
        if (accountIds.size() != quantity) {
            throw new IllegalArgumentException("Account count must match quantity");
        }
    }
}
