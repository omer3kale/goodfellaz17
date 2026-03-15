package com.goodfellaz17.infrastructure.persistence.repository;

import com.goodfellaz17.infrastructure.persistence.entity.OrderTaskEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * OrderTaskRepository — Reactive CRUD for order_tasks table.
 */
@Repository
public interface OrderTaskRepository extends R2dbcRepository<OrderTaskEntity, UUID> {

    /**
     * Find all tasks for a specific order.
     */
    Flux<OrderTaskEntity> findByOrderId(UUID orderId);

    /**
     * Find all PENDING tasks (ready to execute).
     */
    @Query("SELECT * FROM order_tasks WHERE \"status\" = 'PENDING' ORDER BY created_at ASC")
    Flux<OrderTaskEntity> findPendingTasks();

    /**
     * Find all tasks with a specific status.
     */
    Flux<OrderTaskEntity> findByStatus(String status);

    /**
     * Count tasks for a specific order.
     */
    Mono<Long> countByOrderId(UUID orderId);

    /**
     * Count PENDING tasks.
     */
    @Query("SELECT COUNT(*) FROM order_tasks WHERE status = 'PENDING'")
    Mono<Long> countPendingTasks();

    /**
     * Count all tasks in the system.
     */
    Mono<Long> count();

    /**
     * Count tasks for a specific order in a specific status.
     */
    Mono<Long> countByOrderIdAndStatus(UUID orderId, String status);
}
