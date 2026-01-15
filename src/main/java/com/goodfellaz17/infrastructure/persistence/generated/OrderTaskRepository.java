package com.goodfellaz17.infrastructure.persistence.generated;

import com.goodfellaz17.domain.model.generated.OrderTaskEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * Repository for OrderTaskEntity - granular execution units.
 * 
 * Provides queries for:
 * - Worker task pickup (pending + orphaned)
 * - Task status management
 * - Progress tracking
 * - Dead-letter queue queries
 * 
 * @author goodfellaz17
 * @since 1.0.0
 */
@Repository
public interface OrderTaskRepository extends R2dbcRepository<OrderTaskEntity, UUID> {
    
    // === Basic Queries ===
    
    /**
     * Find all tasks for an order.
     */
    Flux<OrderTaskEntity> findByOrderId(UUID orderId);
    
    /**
     * Find all tasks for an order, ordered by sequence.
     */
    Flux<OrderTaskEntity> findByOrderIdOrderBySequenceNumberAsc(UUID orderId);
    
    /**
     * Find tasks by status.
     */
    Flux<OrderTaskEntity> findByStatus(String status);
    
    /**
     * Find tasks by order and status.
     */
    Flux<OrderTaskEntity> findByOrderIdAndStatus(UUID orderId, String status);
    
    // === Worker Pickup Queries ===
    
    /**
     * Find PENDING tasks that are ready for execution.
     * Scheduled time must be past.
     */
    @Query("""
        SELECT * FROM order_tasks 
        WHERE status = 'PENDING' 
          AND scheduled_at <= :now 
        ORDER BY scheduled_at ASC 
        LIMIT :limit
        """)
    Flux<OrderTaskEntity> findPendingTasksReadyForExecution(Instant now, int limit);
    
    /**
     * Find FAILED_RETRYING tasks that are past their retry delay.
     */
    @Query("""
        SELECT * FROM order_tasks 
        WHERE status = 'FAILED_RETRYING' 
          AND (retry_after IS NULL OR retry_after <= :now)
        ORDER BY retry_after ASC NULLS FIRST
        LIMIT :limit
        """)
    Flux<OrderTaskEntity> findRetryableTasksReady(Instant now, int limit);
    
    /**
     * Find orphaned EXECUTING tasks (started but never finished).
     * Consider orphaned if execution started more than threshold seconds ago.
     */
    @Query("""
        SELECT * FROM order_tasks 
        WHERE status = 'EXECUTING' 
          AND execution_started_at < :orphanThreshold
        ORDER BY execution_started_at ASC
        LIMIT :limit
        """)
    Flux<OrderTaskEntity> findOrphanedTasks(Instant orphanThreshold, int limit);
    
    /**
     * Combined query: get all tasks ready for worker pickup.
     * Includes: PENDING (past scheduled), FAILED_RETRYING (past retry), orphaned EXECUTING.
     */
    @Query("""
        SELECT * FROM order_tasks 
        WHERE (
            (status = 'PENDING' AND scheduled_at <= :now)
            OR (status = 'FAILED_RETRYING' AND (retry_after IS NULL OR retry_after <= :now))
            OR (status = 'EXECUTING' AND execution_started_at < :orphanThreshold)
        )
        ORDER BY 
            CASE 
                WHEN status = 'EXECUTING' THEN 0  -- Orphans first (recover)
                WHEN status = 'FAILED_RETRYING' THEN 1  -- Retries second
                ELSE 2  -- New pending last
            END,
            scheduled_at ASC
        LIMIT :limit
        """)
    Flux<OrderTaskEntity> findTasksReadyForWorker(Instant now, Instant orphanThreshold, int limit);
    
    // === Claim/Lock Query ===
    
    /**
     * Atomically claim a task for execution.
     * Uses optimistic locking: only updates if status hasn't changed.
     */
    @Query("""
        UPDATE order_tasks 
        SET status = 'EXECUTING', 
            execution_started_at = :now,
            worker_id = :workerId,
            proxy_node_id = :proxyNodeId,
            attempts = attempts + 1
        WHERE id = :taskId 
          AND status IN ('PENDING', 'FAILED_RETRYING', 'EXECUTING')
        RETURNING *
        """)
    Mono<OrderTaskEntity> claimTask(UUID taskId, Instant now, String workerId, UUID proxyNodeId);
    
    /**
     * Mark task as completed.
     */
    @Query("""
        UPDATE order_tasks 
        SET status = 'COMPLETED',
            executed_at = :now,
            last_error = NULL
        WHERE id = :taskId
        RETURNING *
        """)
    Mono<OrderTaskEntity> completeTask(UUID taskId, Instant now);
    
    /**
     * Mark task as failed with error.
     */
    @Query("""
        UPDATE order_tasks 
        SET status = CASE 
                WHEN attempts >= max_attempts THEN 'FAILED_PERMANENT'
                ELSE 'FAILED_RETRYING'
            END,
            last_error = :error,
            retry_after = CASE 
                WHEN attempts >= max_attempts THEN NULL
                ELSE :retryAfter
            END,
            execution_started_at = NULL,
            worker_id = NULL
        WHERE id = :taskId
        RETURNING *
        """)
    Mono<OrderTaskEntity> failTask(UUID taskId, String error, Instant retryAfter);
    
    // === Progress Queries ===
    
    /**
     * Count tasks by status for an order.
     */
    Mono<Long> countByOrderIdAndStatus(UUID orderId, String status);
    
    /**
     * Count completed tasks for an order.
     */
    @Query("SELECT COUNT(*) FROM order_tasks WHERE order_id = :orderId AND status = 'COMPLETED'")
    Mono<Long> countCompletedTasksForOrder(UUID orderId);
    
    /**
     * Sum of quantities from completed tasks.
     */
    @Query("SELECT COALESCE(SUM(quantity), 0) FROM order_tasks WHERE order_id = :orderId AND status = 'COMPLETED'")
    Mono<Integer> sumCompletedQuantityForOrder(UUID orderId);
    
    /**
     * Count permanently failed tasks for an order.
     */
    @Query("SELECT COUNT(*) FROM order_tasks WHERE order_id = :orderId AND status = 'FAILED_PERMANENT'")
    Mono<Long> countFailedPermanentTasksForOrder(UUID orderId);
    
    /**
     * Sum of quantities from permanently failed tasks (dead-letter).
     */
    @Query("SELECT COALESCE(SUM(quantity), 0) FROM order_tasks WHERE order_id = :orderId AND status = 'FAILED_PERMANENT'")
    Mono<Integer> sumFailedPermanentQuantityForOrder(UUID orderId);
    
    /**
     * Get progress summary for an order.
     */
    @Query("""
        SELECT 
            order_id,
            COUNT(*) as total_tasks,
            SUM(quantity) as total_quantity,
            SUM(CASE WHEN status = 'COMPLETED' THEN quantity ELSE 0 END) as delivered_quantity,
            SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed_tasks,
            SUM(CASE WHEN status = 'FAILED_PERMANENT' THEN 1 ELSE 0 END) as failed_permanent_tasks,
            SUM(CASE WHEN status = 'FAILED_PERMANENT' THEN quantity ELSE 0 END) as failed_permanent_quantity,
            SUM(CASE WHEN status IN ('PENDING', 'EXECUTING', 'FAILED_RETRYING') THEN 1 ELSE 0 END) as active_tasks
        FROM order_tasks 
        WHERE order_id = :orderId
        GROUP BY order_id
        """)
    Mono<TaskProgressSummary> getProgressSummary(UUID orderId);
    
    /**
     * Progress summary projection.
     */
    interface TaskProgressSummary {
        UUID getOrderId();
        Long getTotalTasks();
        Long getTotalQuantity();
        Long getDeliveredQuantity();
        Long getCompletedTasks();
        Long getFailedPermanentTasks();
        Long getFailedPermanentQuantity();
        Long getActiveTasks();
    }
    
    // === Dead Letter Queue ===
    
    /**
     * Find all permanently failed tasks for an order (dead-letter queue).
     */
    @Query("""
        SELECT * FROM order_tasks 
        WHERE order_id = :orderId AND status = 'FAILED_PERMANENT'
        ORDER BY sequence_number ASC
        """)
    Flux<OrderTaskEntity> findFailedPermanentTasksForOrder(UUID orderId);
    
    /**
     * Find all permanently failed tasks across all orders (global dead-letter).
     */
    @Query("""
        SELECT * FROM order_tasks 
        WHERE status = 'FAILED_PERMANENT'
        ORDER BY created_at DESC
        LIMIT :limit
        """)
    Flux<OrderTaskEntity> findAllFailedPermanentTasks(int limit);
    
    // === Cleanup/Admin ===
    
    /**
     * Delete all tasks for an order (use carefully!).
     */
    Mono<Void> deleteByOrderId(UUID orderId);
    
    /**
     * Count total pending tasks globally.
     */
    @Query("SELECT COUNT(*) FROM order_tasks WHERE status = 'PENDING'")
    Mono<Long> countAllPendingTasks();
    
    /**
     * Count total executing tasks globally.
     */
    @Query("SELECT COUNT(*) FROM order_tasks WHERE status = 'EXECUTING'")
    Mono<Long> countAllExecutingTasks();
    
    /**
     * Check if idempotency token already exists (for deduplication).
     */
    Mono<Boolean> existsByIdempotencyToken(String idempotencyToken);
}
