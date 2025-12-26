package com.goodfellaz17.infrastructure.persistence.repository;

import com.goodfellaz17.infrastructure.persistence.entity.OrderEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Order Repository - Order tracking.
 * R2DBC reactive repository for orders table.
 */
@Repository
public interface OrderRepository extends ReactiveCrudRepository<OrderEntity, UUID> {

    /**
     * Find orders by API key.
     */
    Flux<OrderEntity> findByApiKey(String apiKey);

    /**
     * Find orders by API key ordered by updated_at DESC.
     */
    @Query("SELECT * FROM orders WHERE api_key = :apiKey ORDER BY updated_at DESC")
    Flux<OrderEntity> findByApiKeyOrderByUpdatedAtDesc(String apiKey);

    /**
     * Find recent orders by API key (limit N).
     */
    @Query("SELECT * FROM orders WHERE api_key = :apiKey ORDER BY updated_at DESC LIMIT :limit")
    Flux<OrderEntity> findRecentByApiKey(String apiKey, int limit);

    /**
     * Count active orders (Pending/Processing) by API key.
     */
    @Query("SELECT COUNT(*) FROM orders WHERE api_key = :apiKey AND status IN ('Pending', 'Processing')")
    Mono<Long> countActiveByApiKey(String apiKey);

    /**
     * Find orders by status.
     */
    Flux<OrderEntity> findByStatus(String status);

    /**
     * Find refundable failed orders.
     */
    @Query("SELECT * FROM orders WHERE status = 'Failed'")
    Flux<OrderEntity> findRefundableFailures();

    /**
     * Update order status.
     */
    @Modifying
    @Query("UPDATE orders SET status = :status, updated_at = NOW() WHERE order_id = :orderId")
    Mono<Integer> updateStatus(UUID orderId, String status);

    /**
     * Update order progress.
     */
    @Modifying
    @Query("""
        UPDATE orders SET 
            charged_count = :deliveredQuantity,
            remaining_count = quantity - :deliveredQuantity,
            updated_at = NOW() 
        WHERE order_id = :orderId
        """)
    Mono<Integer> updateProgress(UUID orderId, int progress, int deliveredQuantity);

    /**
     * Complete order.
     */
    @Modifying
    @Query("""
        UPDATE orders SET 
            status = 'Completed', 
            charged_count = quantity,
            remaining_count = 0,
            updated_at = NOW()
        WHERE order_id = :orderId
        """)
    Mono<Integer> completeOrder(UUID orderId);

    /**
     * Mark order as refunded.
     */
    @Modifying
    @Query("UPDATE orders SET status = 'Refunded', updated_at = NOW() WHERE order_id = :orderId")
    Mono<Integer> markRefunded(UUID orderId);

    // ==================== ADMIN STATS ====================

    /**
     * Total revenue (all time).
     */
    @Query("SELECT COALESCE(SUM(charged), 0) FROM orders WHERE status IN ('Completed', 'Processing')")
    Mono<BigDecimal> totalRevenue();

    /**
     * Revenue last 30 days.
     */
    @Query("SELECT COALESCE(SUM(charged), 0) FROM orders WHERE status IN ('Completed', 'Processing') AND created_at > NOW() - INTERVAL '30 days'")
    Mono<BigDecimal> revenueLast30Days();

    /**
     * Revenue today.
     */
    @Query("SELECT COALESCE(SUM(charged), 0) FROM orders WHERE status IN ('Completed', 'Processing') AND created_at > NOW() - INTERVAL '1 day'")
    Mono<BigDecimal> revenueToday();

    /**
     * Order count today.
     */
    @Query("SELECT COUNT(*) FROM orders WHERE created_at > NOW() - INTERVAL '1 day'")
    Mono<Long> ordersToday();

    /**
     * Active orders (Pending/Processing).
     */
    @Query("SELECT COUNT(*) FROM orders WHERE status IN ('Pending', 'Processing')")
    Mono<Long> activeOrderCount();

    /**
     * Completed orders count.
     */
    @Query("SELECT COUNT(*) FROM orders WHERE status = 'Completed'")
    Mono<Long> completedOrderCount();

    /**
     * Orders completed within 24h (estimate based on eta_minutes).
     */
    @Query("""
        SELECT COUNT(*) FROM orders 
        WHERE status = 'Completed' 
        AND eta_minutes IS NOT NULL 
        AND eta_minutes < 1440
        """)
    Mono<Long> ordersCompletedWithin24h();

    /**
     * Top service IDs by order count.
     */
    @Query("""
        SELECT service_id, COUNT(*) as cnt 
        FROM orders 
        GROUP BY service_id 
        ORDER BY cnt DESC 
        LIMIT :limit
        """)
    Flux<Object[]> topServicesByOrderCount(int limit);
}
