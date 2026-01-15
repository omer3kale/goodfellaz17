package com.goodfellaz17.infrastructure.persistence.generated;

import com.goodfellaz17.domain.model.generated.OrderEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * Repository: OrderRepository
 * 
 * R2DBC reactive repository for Order entity operations.
 * Includes custom queries for common access patterns.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
@Repository
public interface GeneratedOrderRepository extends R2dbcRepository<OrderEntity, UUID> {
    
    // === Basic Queries (derived) ===
    
    /**
     * Find all orders for a specific user.
     */
    Flux<OrderEntity> findByUserId(UUID userId);
    
    /**
     * Find all orders for a user with specific status.
     */
    Flux<OrderEntity> findByUserIdAndStatus(UUID userId, String status);
    
    /**
     * Find all orders with a specific status.
     */
    Flux<OrderEntity> findByStatus(String status);
    
    /**
     * Find orders with status in list (e.g., active orders).
     */
    Flux<OrderEntity> findByStatusIn(java.util.Collection<String> statuses);
    
    /**
     * Find orders created within a time range.
     */
    Flux<OrderEntity> findByCreatedAtBetween(Instant start, Instant end);
    
    /**
     * Find orders for a specific target URL.
     */
    Flux<OrderEntity> findByTargetUrl(String targetUrl);
    
    // === Count Queries ===
    
    /**
     * Count orders for a user with specific status.
     */
    Mono<Long> countByUserIdAndStatus(UUID userId, String status);
    
    /**
     * Count all orders with specific status.
     */
    Mono<Long> countByStatus(String status);
    
    // === Custom Queries ===
    
    /**
     * Find active orders (PENDING or RUNNING) ordered by creation time.
     */
    @Query("SELECT * FROM orders WHERE status IN ('PENDING', 'RUNNING') ORDER BY created_at ASC")
    Flux<OrderEntity> findActiveOrders();
    
    /**
     * Find orders ready for processing (PENDING status, oldest first).
     */
    @Query("SELECT * FROM orders WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit")
    Flux<OrderEntity> findPendingOrdersLimit(int limit);
    
    /**
     * Find user's recent orders with pagination.
     */
    @Query("SELECT * FROM orders WHERE user_id = :userId ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    Flux<OrderEntity> findByUserIdWithPagination(UUID userId, int limit, int offset);
    
    /**
     * Find orders that need webhook delivery.
     */
    @Query("SELECT * FROM orders WHERE webhook_delivered = false AND status IN ('COMPLETED', 'FAILED', 'PARTIAL')")
    Flux<OrderEntity> findOrdersNeedingWebhookDelivery();
    
    /**
     * Update order progress atomically.
     */
    @Query("UPDATE orders SET delivered = :delivered, current_count = :currentCount WHERE id = :orderId AND delivered <= :delivered RETURNING *")
    Mono<OrderEntity> updateProgress(UUID orderId, int delivered, Integer currentCount);
    
    /**
     * Transition order status with optimistic locking.
     */
    @Query("UPDATE orders SET status = :newStatus, started_at = CASE WHEN :newStatus = 'RUNNING' THEN CURRENT_TIMESTAMP ELSE started_at END WHERE id = :orderId AND status = :expectedStatus RETURNING *")
    Mono<OrderEntity> transitionStatus(UUID orderId, String expectedStatus, String newStatus);
    
    /**
     * Complete order and set completion timestamp.
     */
    @Query("UPDATE orders SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP, delivered = quantity WHERE id = :orderId RETURNING *")
    Mono<OrderEntity> completeOrder(UUID orderId);
    
    /**
     * Fail order with reason.
     */
    @Query("UPDATE orders SET status = 'FAILED', completed_at = CURRENT_TIMESTAMP, failure_reason = :reason WHERE id = :orderId RETURNING *")
    Mono<OrderEntity> failOrder(UUID orderId, String reason);
    
    /**
     * Get total revenue for a time period.
     */
    @Query("SELECT COALESCE(SUM(cost - refund_amount), 0) FROM orders WHERE created_at >= :start AND created_at < :end AND status NOT IN ('CANCELLED', 'REFUNDED')")
    Mono<java.math.BigDecimal> calculateRevenue(Instant start, Instant end);
    
    /**
     * Get order statistics by status.
     */
    @Query("SELECT status, COUNT(*) as count, SUM(quantity) as total_quantity, SUM(delivered) as total_delivered FROM orders GROUP BY status")
    Flux<OrderStatistics> getOrderStatistics();
    
    /**
     * Statistics projection interface.
     */
    interface OrderStatistics {
        String getStatus();
        Long getCount();
        Long getTotalQuantity();
        Long getTotalDelivered();
    }
    
    // === Idempotency Support ===
    
    /**
     * Find order by external order ID (idempotency key).
     * Used for duplicate detection on retries.
     */
    Mono<OrderEntity> findByExternalOrderId(String externalOrderId);
    
    /**
     * Check if an order with this idempotency key exists.
     */
    Mono<Boolean> existsByExternalOrderId(String externalOrderId);
}
