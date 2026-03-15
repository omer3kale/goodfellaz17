package com.goodfellaz17.infrastructure.persistence.repository;

import com.goodfellaz17.infrastructure.persistence.entity.ExecutionResultEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * ExecutionResultRepository — Reactive CRUD for execution_results table.
 */
@Repository
public interface ExecutionResultRepository extends R2dbcRepository<ExecutionResultEntity, UUID> {

    /**
     * Find all execution results for a specific task.
     */
    Flux<ExecutionResultEntity> findByOrderTaskId(UUID orderTaskId);

    /**
     * Find all successful execution results for a task.
     */
    @Query("SELECT * FROM execution_results WHERE order_task_id = :taskId AND success_flag = 1 ORDER BY created_at DESC")
    Flux<ExecutionResultEntity> findSuccessfulByOrderTaskId(UUID taskId);

    /**
     * Find all failed execution results for a task.
     */
    @Query("SELECT * FROM execution_results WHERE order_task_id = :taskId AND success_flag = 0 ORDER BY created_at DESC")
    Flux<ExecutionResultEntity> findFailedByOrderTaskId(UUID taskId);

    /**
     * Find all execution results for a specific order.
     */
    Flux<ExecutionResultEntity> findByOrderId(UUID orderId);

    /**
     * Count successful executions for a task.
     */
    @Query("SELECT COUNT(*) FROM execution_results WHERE order_task_id = :taskId AND success_flag = 1")
    Mono<Long> countSuccessfulByOrderTaskId(UUID taskId);

    /**
     * Count failed executions for a task.
     */
    @Query("SELECT COUNT(*) FROM execution_results WHERE order_task_id = :taskId AND success_flag = 0")
    Mono<Long> countFailedByOrderTaskId(UUID taskId);

    /**
     * Count all execution results in the system.
     */
    Mono<Long> count();
}
