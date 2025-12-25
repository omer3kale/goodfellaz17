package com.goodfellaz17.infrastructure.persistence;

import com.goodfellaz17.domain.model.Order;
import com.goodfellaz17.domain.model.OrderStatus;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * R2DBC Repository - Orders from Neon PostgreSQL.
 * 
 * REAL DATABASE - NO MOCKS.
 */
@Repository
public interface OrderRepository extends ReactiveCrudRepository<Order, UUID> {
    
    /**
     * Find pending orders.
     */
    Flux<Order> findByStatus(OrderStatus status);
    
    /**
     * Find orders created today.
     */
    @Query("SELECT * FROM orders WHERE DATE(created_at) = :today")
    Flux<Order> findTodayOrders(LocalDate today);
    
    /**
     * Count all completed orders.
     */
    Mono<Long> countByStatus(OrderStatus status);
    
    /**
     * Sum total delivered plays.
     */
    @Query("SELECT COALESCE(SUM(delivered), 0) FROM orders WHERE status = 'COMPLETED'")
    Mono<Integer> totalDelivered();
    
    /**
     * Calculate total revenue (completed orders).
     */
    @Query("SELECT COALESCE(SUM(quantity * rate_per_thousand / 1000), 0) FROM orders WHERE status = 'COMPLETED'")
    Mono<BigDecimal> totalRevenue();
    
    /**
     * Find processing orders with remaining delivery.
     */
    @Query("SELECT * FROM orders WHERE status = 'PROCESSING' AND delivered < quantity")
    Flux<Order> findProcessingWithRemaining();
    
    /**
     * Update delivered count.
     */
    @Modifying
    @Query("UPDATE orders SET delivered = :delivered WHERE id = :id")
    Mono<Void> updateDelivered(UUID id, int delivered);
    
    /**
     * Update order status.
     */
    @Modifying
    @Query("UPDATE orders SET status = :status WHERE id = :id")
    Mono<Void> updateStatus(UUID id, String status);
}
