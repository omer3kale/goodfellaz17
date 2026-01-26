package com.goodfellaz17.order.service;

import com.goodfellaz17.order.domain.Order;
import com.goodfellaz17.order.domain.OrderTask;
import com.goodfellaz17.order.metrics.DeliveryMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * OrderServiceFacade: Wraps OrderOrchestrator with:
 * - Structured logging
 * - Consistent error handling
 * - Metric collection
 *
 * CRITICAL: This stays fully reactive (Mono/Flux returns).
 * It's a decorator, not a blocker.
 */
@Service
public class OrderServiceFacade {

    private static final Logger logger = LoggerFactory.getLogger(OrderServiceFacade.class);

    private final OrderOrchestrator orchestrator;

    public OrderServiceFacade(OrderOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Create order with audit logging and error handling.
     * Returns Mono<Order> - still fully async, no blocking.
     */
    public Mono<Order> createOrderWithAudit(String trackId, Integer quantity, List<String> accountIds) {
        return Mono.defer(() -> {
            logger.info("🎯 Order creation request | trackId={} | quantity={} | accounts={}",
                trackId, quantity, accountIds.size());

            return orchestrator.createOrder(trackId, quantity, accountIds)
                .doOnSuccess(order ->
                    logger.info("✅ Order created successfully | orderId={} | status={} | tasks={}",
                        order.getId(), order.getStatus(), order.getTasks().size())
                )
                .doOnError(e ->
                    logger.error("❌ Order creation failed | trackId={} | error={}",
                        trackId, e.getMessage(), e)
                )
                .onErrorMap(this::mapOrderCreationException);
        });
    }

    /**
     * Record task delivery with logging.
     */
    public Mono<Void> recordTaskDeliveryWithAudit(UUID taskId, String proxyNodeId) {
        return Mono.defer(() -> {
            logger.info("📤 Recording task delivery | taskId={} | proxyNode={}", taskId, proxyNodeId);

            return orchestrator.recordTaskDelivery(taskId, proxyNodeId)
                .doOnSuccess(v ->
                    logger.info("✅ Task delivery recorded | taskId={}", taskId)
                )
                .doOnError(e ->
                    logger.error("❌ Task delivery failed | taskId={} | error={}",
                        taskId, e.getMessage())
                )
                .onErrorMap(e -> new OrderServiceException(e.getMessage(), e));
        });
    }

    /**
     * Record task failure with logging.
     */
    public Mono<Void> recordTaskFailureWithAudit(UUID taskId, String reason) {
        return Mono.defer(() -> {
            logger.warn("⚠️  Recording task failure | taskId={} | reason={}", taskId, reason);

            return orchestrator.recordTaskFailure(taskId, reason)
                .doOnSuccess(v ->
                    logger.info("✅ Task failure recorded (retry queued or marked failed) | taskId={}", taskId)
                )
                .doOnError(e ->
                    logger.error("❌ Task failure recording failed | taskId={} | error={}",
                        taskId, e.getMessage())
                )
                .onErrorMap(e -> new OrderServiceException(e.getMessage(), e));
        });
    }

    /**
     * Assign task to proxy node with logging.
     */
    public Mono<Void> assignTaskWithAudit(UUID taskId, String proxyNodeId) {
        return Mono.defer(() -> {
            logger.info("📋 Assigning task to proxy | taskId={} | proxyNode={}", taskId, proxyNodeId);

            return orchestrator.assignTask(taskId, proxyNodeId)
                .doOnSuccess(v ->
                    logger.info("✅ Task assigned | taskId={} | proxyNode={}", taskId, proxyNodeId)
                )
                .doOnError(e ->
                    logger.error("❌ Task assignment failed | taskId={} | error={}",
                        taskId, e.getMessage())
                )
                .onErrorMap(e -> new OrderServiceException(e.getMessage(), e));
        });
    }

    /**
     * Get metrics with logging.
     */
    public Mono<DeliveryMetrics> getMetricsWithAudit() {
        return orchestrator.getMetrics()
            .doOnSuccess(metrics ->
                logger.debug("📊 Pipeline metrics | totalOrders={} | successRate={}% | status={}",
                    metrics.getTotalOrders(),
                    String.format("%.1f", metrics.getOverallSuccessRate() * 100),
                    metrics.getPipelineStatus())
            );
    }

    /**
     * Get order with logging.
     */
    public Mono<Order> getOrderWithAudit(UUID orderId) {
        return orchestrator.getOrder(orderId)
            .doOnNext(order ->
                logger.debug("📦 Retrieved order | orderId={} | status={} | tasks={}",
                    order.getId(), order.getStatus(), order.getTasks().size())
            );
    }

    /**
     * Get order tasks with logging.
     */
    public Flux<OrderTask> getOrderTasksWithAudit(UUID orderId) {
        return orchestrator.getOrderTasks(orderId)
            .doFinally(signal ->
                logger.debug("📋 Retrieved order tasks | orderId={}", orderId)
            );
    }

    /**
     * Get pending tasks with logging.
     */
    public Flux<OrderTask> getPendingTasksWithAudit() {
        return orchestrator.getPendingTasks()
            .doFinally(signal ->
                logger.debug("⏳ Retrieved pending tasks")
            );
    }

    // ============ Error Mapping ============

    /**
     * Map exceptions to domain-specific error types.
     */
    private OrderServiceException mapOrderCreationException(Throwable e) {
        if (e instanceof IllegalArgumentException) {
            return new OrderServiceException("Invalid order request: " + e.getMessage(), e);
        }
        if (e instanceof RuntimeException && e.getMessage() != null && e.getMessage().contains("Failed to save")) {
            return new OrderServiceException("Database error while saving order", e);
        }
        return new OrderServiceException("Unexpected error during order creation: " + e.getMessage(), e);
    }

    // ============ Custom Exceptions ============

    /**
     * Domain-specific exception for order service failures.
     */
    public static class OrderServiceException extends RuntimeException {
        public OrderServiceException(String message) {
            super(message);
        }

        public OrderServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
