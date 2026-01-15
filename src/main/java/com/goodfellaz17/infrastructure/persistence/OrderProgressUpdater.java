package com.goodfellaz17.infrastructure.persistence;

import com.goodfellaz17.domain.model.generated.OrderEntity;
import com.goodfellaz17.infrastructure.persistence.generated.GeneratedOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Service for atomic order progress updates using DatabaseClient.
 * 
 * Spring Data R2DBC @Query with @Modifying doesn't work well for UPDATE statements,
 * so we use DatabaseClient directly for atomic increments.
 */
@Service
public class OrderProgressUpdater {
    
    private static final Logger log = LoggerFactory.getLogger(OrderProgressUpdater.class);
    
    private final DatabaseClient databaseClient;
    private final GeneratedOrderRepository orderRepository;
    private final TransactionalOperator transactionalOperator;
    
    public OrderProgressUpdater(
            DatabaseClient databaseClient, 
            GeneratedOrderRepository orderRepository,
            TransactionalOperator transactionalOperator) {
        this.databaseClient = databaseClient;
        this.orderRepository = orderRepository;
        this.transactionalOperator = transactionalOperator;
    }
    
    /**
     * Atomically increment delivered count and decrement remains.
     * Uses raw SQL UPDATE to prevent lost updates from concurrent task completions.
     * Returns the updated order for completion checking.
     */
    public Mono<OrderEntity> atomicIncrementDelivered(UUID orderId, int deliveredPlays) {
        // R2DBC DatabaseClient uses :name parameter placeholders with .bind("name", value)
        // Note: orders table has no updated_at column - only created_at, started_at, completed_at
        return databaseClient.sql(
                "UPDATE orders SET delivered = delivered + :plays, " +
                "remains = GREATEST(0, quantity - (delivered + :plays)) " +
                "WHERE id = :orderId")
            .bind("plays", deliveredPlays)
            .bind("orderId", orderId)
            .fetch()
            .rowsUpdated()
            .doOnNext(rows -> log.debug("ORDER_INCREMENT | orderId={} | plays={} | rows={}", 
                orderId, deliveredPlays, rows))
            .then(orderRepository.findById(orderId));
    }
    
    /**
     * Atomically increment failed_permanent_plays count.
     */
    public Mono<Integer> atomicIncrementFailedPermanent(UUID orderId, int failedPlays) {
        // R2DBC DatabaseClient uses :name parameter placeholders with .bind("name", value)
        // Note: orders table has no updated_at column
        return databaseClient.sql(
                "UPDATE orders SET failed_permanent_plays = COALESCE(failed_permanent_plays, 0) + :plays " +
                "WHERE id = :orderId")
            .bind("plays", failedPlays)
            .bind("orderId", orderId)
            .fetch()
            .rowsUpdated()
            .map(Long::intValue)
            .doOnNext(rows -> log.debug("ORDER_FAILED_INCREMENT | orderId={} | failedPlays={} | rows={}", 
                orderId, failedPlays, rows));
    }
    
    /**
     * Atomically increment order's refund_amount for failed plays.
     * Used to track total refunded amount on the order.
     */
    public Mono<Long> atomicIncrementOrderRefund(UUID orderId, BigDecimal refundAmount) {
        return databaseClient.sql(
                "UPDATE orders SET refund_amount = COALESCE(refund_amount, 0) + :amount " +
                "WHERE id = :orderId")
            .bind("amount", refundAmount)
            .bind("orderId", orderId)
            .fetch()
            .rowsUpdated()
            .doOnNext(rows -> log.debug("ORDER_REFUND_INCREMENT | orderId={} | refundAmount={} | rows={}", 
                orderId, refundAmount, rows));
    }
    
    /**
     * Atomically credit user balance for failed plays.
     * Uses optimistic update: balance = balance + amount (no negative check needed for credits).
     */
    public Mono<Long> atomicCreditUserBalance(UUID userId, BigDecimal creditAmount) {
        return databaseClient.sql(
                "UPDATE users SET balance = balance + :amount WHERE id = :userId")
            .bind("amount", creditAmount)
            .bind("userId", userId)
            .fetch()
            .rowsUpdated()
            .doOnNext(rows -> log.info("USER_BALANCE_CREDIT | userId={} | creditAmount={} | rows={}", 
                userId, creditAmount, rows));
    }
    
    /**
     * Mark a task as refunded (idempotency check).
     * Returns the number of rows updated (0 if already refunded, 1 if newly marked).
     */
    public Mono<Long> markTaskRefunded(UUID taskId) {
        return databaseClient.sql(
                "UPDATE order_tasks SET refunded = TRUE WHERE id = :taskId AND refunded = FALSE")
            .bind("taskId", taskId)
            .fetch()
            .rowsUpdated()
            .doOnNext(rows -> log.debug("TASK_MARK_REFUNDED | taskId={} | rowsUpdated={}", taskId, rows));
    }
    
    /**
     * Atomically process a refund for a permanently failed task.
     * 
     * This is the core refund operation that:
     * 1. Marks the task as refunded (idempotency check - fails silently if already refunded)
     * 2. Credits the user's balance with the pro-rated refund amount
     * 3. Increments the order's refund_amount for tracking
     * 
     * All operations are in a single transaction for atomicity.
     * If the task is already refunded (markTaskRefunded returns 0), no balance credit occurs.
     * 
     * @param taskId The failed task ID
     * @param orderId The parent order ID
     * @param userId The user to credit
     * @param failedPlays Number of plays that failed
     * @param pricePerPlay Price per play (totalCost / quantity from order)
     * @return RefundResult with details of the operation
     */
    public Mono<RefundResult> atomicProcessRefund(
            UUID taskId, 
            UUID orderId, 
            UUID userId, 
            int failedPlays, 
            BigDecimal pricePerPlay) {
        
        BigDecimal refundAmount = pricePerPlay
            .multiply(BigDecimal.valueOf(failedPlays))
            .setScale(4, RoundingMode.HALF_UP);
        
        return markTaskRefunded(taskId)
            .flatMap(rowsUpdated -> {
                if (rowsUpdated == 0) {
                    // Task already refunded - idempotent skip
                    log.debug("REFUND_SKIP_IDEMPOTENT | taskId={} | already refunded", taskId);
                    return Mono.just(new RefundResult(taskId, orderId, userId, false, BigDecimal.ZERO, 
                        "Already refunded"));
                }
                
                // Task marked as refunded - now credit balance and track on order
                return atomicCreditUserBalance(userId, refundAmount)
                    .then(atomicIncrementOrderRefund(orderId, refundAmount))
                    .thenReturn(new RefundResult(taskId, orderId, userId, true, refundAmount, null))
                    .doOnSuccess(result -> log.info(
                        "REFUND_SUCCESS | taskId={} | orderId={} | userId={} | plays={} | amount={}", 
                        taskId, orderId, userId, failedPlays, refundAmount));
            })
            .as(transactionalOperator::transactional);
    }
    
    /**
     * Result of a refund operation.
     */
    public record RefundResult(
        UUID taskId,
        UUID orderId,
        UUID userId,
        boolean refundApplied,
        BigDecimal refundAmount,
        String skipReason
    ) {}
}
