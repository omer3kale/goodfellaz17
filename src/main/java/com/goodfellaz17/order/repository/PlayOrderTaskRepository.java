package com.goodfellaz17.order.repository;

import com.goodfellaz17.order.domain.OrderTask;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface PlayOrderTaskRepository extends R2dbcRepository<OrderTask, UUID> {

    /**
     * Find all tasks for a specific order
     */
    Flux<OrderTask> findByOrderId(UUID orderId);

    /**
     * Find tasks by status
     */
    Flux<OrderTask> findByStatus(String status);

    /**
     * Find pending tasks waiting for assignment
     */
    Flux<OrderTask> findByStatusOrderByCreatedAtAsc(String status);

    /**
     * Find tasks assigned to a specific proxy node
     */
    Flux<OrderTask> findByAssignedProxyNode(String proxyNode);

    /**
     * Find active tasks (assigned or executing)
     */
    @Query("SELECT * FROM pipeline_order_tasks WHERE status IN ('ASSIGNED', 'EXECUTING')")
    Flux<OrderTask> findActiveTasks();

    /**
     * Find failed tasks that haven't exceeded max retries
     */
    @Query("SELECT * FROM pipeline_order_tasks WHERE status = 'FAILED' AND retry_count < max_retries")
    Flux<OrderTask> findRetryableTasks();

    /**
     * Find stale tasks (running longer than 5 minutes)
     */
    @Query("SELECT * FROM pipeline_order_tasks WHERE status = 'EXECUTING' AND started_at < :threshold")
    Flux<OrderTask> findStaleTasks(@Param("threshold") Instant threshold);

    /**
     * Count tasks by status
     */
    Mono<Long> countByStatus(String status);

    /**
     * Count tasks for a specific order by status
     */
    @Query("SELECT COUNT(*) FROM pipeline_order_tasks WHERE order_id = :orderId AND status = :status")
    Mono<Long> countByOrderIdAndStatus(@Param("orderId") UUID orderId, @Param("status") String status);

    /**
     * Find tasks completed within a time range
     */
    @Query("SELECT * FROM pipeline_order_tasks WHERE completed_at BETWEEN :startTime AND :endTime")
    Flux<OrderTask> findCompletedBetween(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    /**
     * Calculate average retries across all tasks
     */
    @Query("SELECT AVG(retry_count) FROM pipeline_order_tasks")
    Mono<Double> getAverageRetries();

    /**
     * Custom bulk insert for OrderTasks to ensure INSERT instead of UPDATE
     * (needed because R2DBC tries to UPDATE when ID is manually set)
     */
    @Query("INSERT INTO pipeline_order_tasks (id, order_id, account_id, status, created_at, retry_count, max_retries) " +
           "VALUES (:id, :orderId, :accountId, :status, :createdAt, :retryCount, :maxRetries)")
    Mono<Void> insertTask(@Param("id") UUID id,
                          @Param("orderId") UUID orderId,
                          @Param("accountId") String accountId,
                          @Param("status") String status,
                          @Param("createdAt") Instant createdAt,
                          @Param("retryCount") Integer retryCount,
                          @Param("maxRetries") Integer maxRetries);
}
