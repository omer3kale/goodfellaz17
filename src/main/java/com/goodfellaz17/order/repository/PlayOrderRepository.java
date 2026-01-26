package com.goodfellaz17.order.repository;

import com.goodfellaz17.order.domain.Order;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface PlayOrderRepository extends R2dbcRepository<Order, UUID> {

    /**
     * Find orders by track ID
     */
    Flux<Order> findByTrackId(String trackId);

    /**
     * Find orders by status
     */
    Flux<Order> findByStatus(String status);

    /**
     * Find active orders (PENDING, ACTIVE, or DELIVERING)
     */
    @Query("SELECT * FROM pipeline_orders WHERE status IN ('PENDING', 'ACTIVE', 'DELIVERING')")
    Flux<Order> findActiveOrders();

    /**
     * Find completed orders (COMPLETED or FAILED)
     */
    @Query("SELECT * FROM pipeline_orders WHERE status IN ('COMPLETED', 'FAILED')")
    Flux<Order> findCompletedOrders();

    /**
     * Find orders created after a certain time
     */
    Flux<Order> findByCreatedAtAfter(Instant createdAt);

    /**
     * Find orders completed within a time range
     */
    @Query("SELECT * FROM pipeline_orders WHERE completed_at BETWEEN :startTime AND :endTime")
    Flux<Order> findCompletedBetween(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    /**
     * Count orders by status
     */
    Mono<Long> countByStatus(String status);

    /**
     * Sum plays delivered across all orders
     */
    @Query("SELECT COALESCE(SUM(plays_delivered), 0) FROM pipeline_orders")
    Mono<Long> sumPlaysDelivered();

    /**
     * Sum plays failed across all orders
     */
    @Query("SELECT COALESCE(SUM(plays_failed), 0) FROM pipeline_orders")
    Mono<Long> sumPlaysFailed();

    /**
     * Custom insert for Order to ensure INSERT instead of UPDATE
     * (needed because R2DBC tries to UPDATE when ID is manually set)
     */
    @Query("INSERT INTO pipeline_orders (id, track_id, quantity, status, plays_delivered, plays_failed, created_at, last_updated_at) " +
           "VALUES (:id, :trackId, :quantity, :status, :playsDelivered, :playsFailed, :createdAt, :lastUpdatedAt)")
    Mono<Void> insertOrder(@Param("id") UUID id,
                           @Param("trackId") String trackId,
                           @Param("quantity") Integer quantity,
                           @Param("status") String status,
                           @Param("playsDelivered") Integer playsDelivered,
                           @Param("playsFailed") Integer playsFailed,
                           @Param("createdAt") Instant createdAt,
                           @Param("lastUpdatedAt") Instant lastUpdatedAt);
}
