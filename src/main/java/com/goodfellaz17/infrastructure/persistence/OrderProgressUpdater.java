package com.goodfellaz17.infrastructure.persistence;

import com.goodfellaz17.domain.model.generated.OrderEntity;
import com.goodfellaz17.domain.model.generated.RefundEventEntity;
import com.goodfellaz17.infrastructure.persistence.generated.GeneratedOrderRepository;
import com.goodfellaz17.infrastructure.persistence.generated.RefundEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.util.UUID;

/**
 * Service for atomic order progress updates using DatabaseClient.
 * 
 * Spring Data R2DBC @Query with @Modifying doesn't work well for UPDATE statements,
 * so we use DatabaseClient directly for atomic increments.
 * 
 * Refund Processing:
 * - Uses FOR UPDATE SKIP LOCKED to prevent concurrent worker conflicts
 * - Creates audit records in refund_events table
 * - Supports batched processing for scale
 */
@Service
public class OrderProgressUpdater {
    
    private static final Logger log = LoggerFactory.getLogger(OrderProgressUpdater.class);
    private static final int DEFAULT_REFUND_BATCH_SIZE = 10;
    
    private final DatabaseClient databaseClient;
    private final GeneratedOrderRepository orderRepository;
    private final RefundEventRepository refundEventRepository;
    private final TransactionalOperator transactionalOperator;
    private final String workerId;
    
    // Metrics
    private final Counter refundEventsCounter;
    private final Counter refundAmountCounter;
    private final Counter refundSkippedCounter;
    
    public OrderProgressUpdater(
            DatabaseClient databaseClient, 
            GeneratedOrderRepository orderRepository,
            RefundEventRepository refundEventRepository,
            TransactionalOperator transactionalOperator,
            MeterRegistry meterRegistry) {
        this.databaseClient = databaseClient;
        this.orderRepository = orderRepository;
        this.refundEventRepository = refundEventRepository;
        this.transactionalOperator = transactionalOperator;
        this.workerId = generateWorkerId();
        
        // Initialize metrics
        this.refundEventsCounter = Counter.builder("goodfellaz17.refund.events")
            .description("Total refund events processed")
            .register(meterRegistry);
        this.refundAmountCounter = Counter.builder("goodfellaz17.refund.amount")
            .description("Total refund amount credited")
            .baseUnit("dollars")
            .register(meterRegistry);
        this.refundSkippedCounter = Counter.builder("goodfellaz17.refund.skipped")
            .description("Refunds skipped due to idempotency")
            .register(meterRegistry);
    }
    
    private String generateWorkerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID().toString().substring(0, 8);
        } catch (Exception e) {
            return "worker-" + UUID.randomUUID().toString().substring(0, 8);
        }
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
     * Atomically increment failed_permanent_plays count AND decrement remains.
     * This ensures remains reflects both delivered and permanently failed plays.
     */
    public Mono<Integer> atomicIncrementFailedPermanent(UUID orderId, int failedPlays) {
        // R2DBC DatabaseClient uses :name parameter placeholders with .bind("name", value)
        // Note: orders table has no updated_at column
        // CRITICAL: Also decrement remains so order completion check works
        return databaseClient.sql(
                "UPDATE orders SET failed_permanent_plays = COALESCE(failed_permanent_plays, 0) + :plays, " +
                "remains = GREATEST(0, COALESCE(remains, quantity) - :plays) " +
                "WHERE id = :orderId")
            .bind("plays", failedPlays)
            .bind("orderId", orderId)
            .fetch()
            .rowsUpdated()
            .map(Long::intValue)
            .doOnNext(rows -> log.info("ORDER_FAILED_INCREMENT | orderId={} | failedPlays={} | rowsUpdated={}", 
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
     * 4. Creates an audit record in refund_events table
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
                    refundSkippedCounter.increment();
                    return Mono.just(new RefundResult(taskId, orderId, userId, false, BigDecimal.ZERO, 
                        "Already refunded"));
                }
                
                // Task marked as refunded - now credit balance, track on order, and audit
                return atomicCreditUserBalance(userId, refundAmount)
                    .then(atomicIncrementOrderRefund(orderId, refundAmount))
                    .then(insertRefundAuditRecord(orderId, taskId, userId, failedPlays, refundAmount, pricePerPlay))
                    .thenReturn(new RefundResult(taskId, orderId, userId, true, refundAmount, null))
                    .doOnSuccess(result -> {
                        log.info(
                            "REFUND_SUCCESS | taskId={} | orderId={} | userId={} | plays={} | amount={}", 
                            taskId, orderId, userId, failedPlays, refundAmount);
                        refundEventsCounter.increment();
                        refundAmountCounter.increment(refundAmount.doubleValue());
                    });
            })
            .as(transactionalOperator::transactional);
    }
    
    /**
     * Insert an audit record into refund_events.
     */
    private Mono<RefundEventEntity> insertRefundAuditRecord(
            UUID orderId,
            UUID taskId,
            UUID userId,
            int quantity,
            BigDecimal amount,
            BigDecimal pricePerUnit) {
        RefundEventEntity event = RefundEventEntity.create(
            orderId, taskId, userId, quantity, amount, pricePerUnit, workerId
        );
        return refundEventRepository.save(event)
            .doOnSuccess(e -> log.debug("REFUND_AUDIT_RECORD | eventId={} | taskId={}", e.getId(), taskId));
    }
    
    // =========================================================================
    // BATCH REFUND PROCESSING WITH FOR UPDATE SKIP LOCKED
    // =========================================================================
    
    /**
     * Find refundable tasks for an order with row-level locking.
     * Uses FOR UPDATE SKIP LOCKED to prevent concurrent workers from processing the same tasks.
     * 
     * @param orderId The order to find refundable tasks for
     * @param batchSize Maximum number of tasks to lock and return
     * @return Flux of tasks that are locked for this transaction
     */
    public Flux<RefundableTask> findRefundableTasksForUpdate(UUID orderId, int batchSize) {
        return databaseClient.sql("""
                SELECT t.id, t.order_id, t.quantity, t.refunded,
                       o.user_id, 
                       COALESCE(o.price_per_unit, o.cost / NULLIF(o.quantity, 0)) as price_per_unit
                FROM order_tasks t
                JOIN orders o ON o.id = t.order_id
                WHERE t.order_id = :orderId
                  AND t.status = 'FAILED_PERMANENT'
                  AND t.refunded = FALSE
                FOR UPDATE OF t SKIP LOCKED
                LIMIT :batchSize
                """)
            .bind("orderId", orderId)
            .bind("batchSize", batchSize)
            .map((row, metadata) -> new RefundableTask(
                row.get("id", UUID.class),
                row.get("order_id", UUID.class),
                row.get("user_id", UUID.class),
                row.get("quantity", Integer.class),
                row.get("price_per_unit", BigDecimal.class)
            ))
            .all()
            .doOnSubscribe(s -> log.debug("REFUND_BATCH_QUERY | orderId={} | batchSize={}", orderId, batchSize));
    }
    
    /**
     * Find ALL unrefunded FAILED_PERMANENT tasks across all orders.
     * Uses FOR UPDATE SKIP LOCKED for safe concurrent processing.
     * 
     * @param batchSize Maximum number of tasks to process
     * @return Flux of refundable tasks
     */
    public Flux<RefundableTask> findAllRefundableTasksForUpdate(int batchSize) {
        return databaseClient.sql("""
                SELECT t.id, t.order_id, t.quantity, t.refunded,
                       o.user_id, 
                       COALESCE(o.price_per_unit, o.cost / NULLIF(o.quantity, 0)) as price_per_unit
                FROM order_tasks t
                JOIN orders o ON o.id = t.order_id
                WHERE t.status = 'FAILED_PERMANENT'
                  AND t.refunded = FALSE
                FOR UPDATE OF t SKIP LOCKED
                LIMIT :batchSize
                """)
            .bind("batchSize", batchSize)
            .map((row, metadata) -> new RefundableTask(
                row.get("id", UUID.class),
                row.get("order_id", UUID.class),
                row.get("user_id", UUID.class),
                row.get("quantity", Integer.class),
                row.get("price_per_unit", BigDecimal.class)
            ))
            .all()
            .doOnSubscribe(s -> log.debug("REFUND_BATCH_QUERY_ALL | batchSize={}", batchSize));
    }
    
    /**
     * Process a batch of refundable tasks in a single transaction.
     * 
     * This is the high-level batch operation that:
     * 1. Locks and fetches unrefunded FAILED_PERMANENT tasks
     * 2. Processes each refund atomically
     * 3. Returns summary of processed refunds
     * 
     * @param orderId The order to process (or null for all orders)
     * @param batchSize Maximum tasks to process in this batch
     * @return BatchRefundResult with summary
     */
    public Mono<BatchRefundResult> processRefundBatch(UUID orderId, int batchSize) {
        Flux<RefundableTask> tasksFlux = orderId != null
            ? findRefundableTasksForUpdate(orderId, batchSize)
            : findAllRefundableTasksForUpdate(batchSize);
        
        return tasksFlux
            .flatMap(task -> atomicProcessRefund(
                task.taskId(),
                task.orderId(),
                task.userId(),
                task.quantity(),
                task.pricePerUnit()
            ))
            .collectList()
            .map(results -> {
                int processed = (int) results.stream().filter(RefundResult::refundApplied).count();
                int skipped = results.size() - processed;
                BigDecimal totalRefunded = results.stream()
                    .filter(RefundResult::refundApplied)
                    .map(RefundResult::refundAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                return new BatchRefundResult(processed, skipped, totalRefunded);
            })
            .doOnSuccess(result -> log.info(
                "REFUND_BATCH_COMPLETE | orderId={} | processed={} | skipped={} | totalAmount={}",
                orderId, result.processedCount(), result.skippedCount(), result.totalRefundAmount()))
            .as(transactionalOperator::transactional);
    }
    
    /**
     * Process refunds for all orders with default batch size.
     */
    public Mono<BatchRefundResult> processAllPendingRefunds() {
        return processRefundBatch(null, DEFAULT_REFUND_BATCH_SIZE);
    }
    
    /**
     * DTO for a task that is eligible for refund.
     */
    public record RefundableTask(
        UUID taskId,
        UUID orderId,
        UUID userId,
        int quantity,
        BigDecimal pricePerUnit
    ) {}
    
    /**
     * Result of batch refund processing.
     */
    public record BatchRefundResult(
        int processedCount,
        int skippedCount,
        BigDecimal totalRefundAmount
    ) {
        public boolean hasProcessedRefunds() {
            return processedCount > 0;
        }
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
