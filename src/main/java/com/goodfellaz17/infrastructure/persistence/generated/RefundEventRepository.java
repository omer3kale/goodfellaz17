package com.goodfellaz17.infrastructure.persistence.generated;

import com.goodfellaz17.domain.model.generated.RefundEventEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Repository for RefundEventEntity - append-only audit log.
 * 
 * Design principles:
 * - INSERT only (no UPDATE methods provided)
 * - Query for reconciliation and fraud detection
 * - Aggregations for reporting
 * 
 * @author goodfellaz17
 * @since 1.0.0
 */
@Repository
public interface RefundEventRepository extends R2dbcRepository<RefundEventEntity, UUID> {
    
    // === Query Methods ===
    
    /**
     * Find all refund events for an order.
     */
    Flux<RefundEventEntity> findByOrderId(UUID orderId);
    
    /**
     * Find all refund events for a user.
     */
    Flux<RefundEventEntity> findByUserId(UUID userId);
    
    /**
     * Check if a task has already been refunded (idempotency check).
     */
    Mono<Boolean> existsByTaskId(UUID taskId);
    
    /**
     * Get refund event for a specific task.
     */
    Mono<RefundEventEntity> findByTaskId(UUID taskId);
    
    // === Aggregation Queries ===
    
    /**
     * Sum of refund amounts for an order.
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) 
        FROM refund_events 
        WHERE order_id = :orderId
        """)
    Mono<BigDecimal> sumRefundAmountByOrderId(UUID orderId);
    
    /**
     * Sum of refunded quantities for an order.
     */
    @Query("""
        SELECT COALESCE(SUM(quantity), 0) 
        FROM refund_events 
        WHERE order_id = :orderId
        """)
    Mono<Long> sumRefundedQuantityByOrderId(UUID orderId);
    
    /**
     * Count refund events for an order.
     */
    Mono<Long> countByOrderId(UUID orderId);
    
    // === Fraud Detection Queries ===
    
    /**
     * Find users with multiple refunds in a time window.
     * Used for fraud velocity checks.
     */
    @Query("""
        SELECT user_id, COUNT(*) as refund_count, SUM(amount) as total_amount
        FROM refund_events
        WHERE created_at >= :since
        GROUP BY user_id
        HAVING COUNT(*) >= :minCount
        ORDER BY total_amount DESC
        """)
    Flux<UserRefundSummary> findHighVelocityRefundUsers(Instant since, int minCount);
    
    /**
     * Find recent refunds for a user (for velocity limiting).
     */
    @Query("""
        SELECT * FROM refund_events
        WHERE user_id = :userId
          AND created_at >= :since
        ORDER BY created_at DESC
        LIMIT :limit
        """)
    Flux<RefundEventEntity> findRecentRefundsForUser(UUID userId, Instant since, int limit);
    
    /**
     * Summary projection for fraud detection.
     */
    record UserRefundSummary(UUID userId, Long refundCount, BigDecimal totalAmount) {}
    
    // === Reporting Queries ===
    
    /**
     * Daily refund totals for reporting.
     */
    @Query("""
        SELECT DATE(created_at) as refund_date,
               COUNT(*) as event_count,
               SUM(amount) as total_amount,
               SUM(quantity) as total_plays
        FROM refund_events
        WHERE created_at >= :since
        GROUP BY DATE(created_at)
        ORDER BY refund_date DESC
        """)
    Flux<DailyRefundSummary> getDailyRefundTotals(Instant since);
    
    record DailyRefundSummary(
        java.time.LocalDate refundDate, 
        Long eventCount, 
        BigDecimal totalAmount, 
        Long totalPlays
    ) {}
}
