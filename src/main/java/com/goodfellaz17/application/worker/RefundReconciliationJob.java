package com.goodfellaz17.application.worker;

import com.goodfellaz17.domain.model.generated.RefundAnomalyEntity;
import com.goodfellaz17.infrastructure.persistence.generated.RefundAnomalyRepository;
import com.goodfellaz17.infrastructure.persistence.generated.RefundEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * RefundReconciliationJob - Periodic job to detect refund drift and anomalies.
 * 
 * Compares order-level aggregates (refund_amount, failed_permanent_plays) against
 * task-level data (sum of refunded tasks) to detect discrepancies.
 * 
 * Runs every 15 minutes by default.
 * 
 * Detects:
 * - REFUND_AMOUNT_MISMATCH: order.refund_amount != sum(refunded_tasks * price_per_unit)
 * - FAILED_PLAYS_MISMATCH: order.failed_permanent_plays != sum(refunded_task.quantity)
 * - Fraud velocity: users with unusually high refund rates
 * 
 * @author goodfellaz17
 * @since 1.0.0
 */
@Service
public class RefundReconciliationJob {
    
    private static final Logger log = LoggerFactory.getLogger(RefundReconciliationJob.class);
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01"); // $0.01 tolerance for rounding
    
    private final DatabaseClient databaseClient;
    private final RefundAnomalyRepository anomalyRepository;
    private final RefundEventRepository refundEventRepository;
    
    @Value("${goodfellaz17.reconciliation.enabled:true}")
    private boolean reconciliationEnabled;
    
    @Value("${goodfellaz17.reconciliation.batch-size:100}")
    private int batchSize;
    
    @Value("${goodfellaz17.fraud.velocity.threshold:5}")
    private int fraudVelocityThreshold;
    
    @Value("${goodfellaz17.fraud.velocity.window-hours:1}")
    private int fraudVelocityWindowHours;
    
    // Metrics
    private final Counter anomaliesDetectedCounter;
    private final Counter ordersReconciledCounter;
    
    public RefundReconciliationJob(
            DatabaseClient databaseClient,
            RefundAnomalyRepository anomalyRepository,
            RefundEventRepository refundEventRepository,
            MeterRegistry meterRegistry) {
        this.databaseClient = databaseClient;
        this.anomalyRepository = anomalyRepository;
        this.refundEventRepository = refundEventRepository;
        
        this.anomaliesDetectedCounter = Counter.builder("goodfellaz17.reconciliation.anomalies")
            .description("Total anomalies detected by reconciliation")
            .register(meterRegistry);
        this.ordersReconciledCounter = Counter.builder("goodfellaz17.reconciliation.orders")
            .description("Total orders reconciled")
            .register(meterRegistry);
    }
    
    /**
     * Main reconciliation job - runs every 15 minutes.
     */
    @Scheduled(cron = "${goodfellaz17.reconciliation.cron:0 */15 * * * *}")
    public void runReconciliation() {
        if (!reconciliationEnabled) {
            log.debug("RECONCILIATION_SKIP | disabled by config");
            return;
        }
        
        log.info("RECONCILIATION_START | batchSize={}", batchSize);
        Instant start = Instant.now();
        
        reconcileTerminalOrders()
            .doOnSuccess(result -> {
                Duration elapsed = Duration.between(start, Instant.now());
                log.info("RECONCILIATION_COMPLETE | ordersChecked={} | anomaliesFound={} | duration={}ms",
                    result.ordersChecked(), result.anomaliesFound(), elapsed.toMillis());
            })
            .doOnError(e -> log.error("RECONCILIATION_ERROR | error={}", e.getMessage(), e))
            .subscribe();
    }
    
    /**
     * Reconcile terminal orders (COMPLETED, PARTIAL, CANCELLED, FAILED).
     */
    public Mono<ReconciliationResult> reconcileTerminalOrders() {
        return findTerminalOrdersWithRefunds()
            .flatMap(this::reconcileOrder)
            .collectList()
            .map(anomalies -> {
                int count = anomalies.stream().mapToInt(a -> a ? 1 : 0).sum();
                ordersReconciledCounter.increment(anomalies.size());
                return new ReconciliationResult(anomalies.size(), count);
            });
    }
    
    /**
     * Find terminal orders that have refund activity.
     */
    private Flux<OrderReconciliationData> findTerminalOrdersWithRefunds() {
        return databaseClient.sql("""
                SELECT 
                    o.id as order_id,
                    o.quantity,
                    o.refund_amount,
                    o.failed_permanent_plays,
                    COALESCE(o.price_per_unit, o.cost / NULLIF(o.quantity, 0)) as price_per_unit,
                    COALESCE(task_agg.refunded_count, 0) as refunded_task_count,
                    COALESCE(task_agg.refunded_quantity, 0) as refunded_quantity
                FROM orders o
                LEFT JOIN LATERAL (
                    SELECT 
                        COUNT(*) as refunded_count,
                        COALESCE(SUM(quantity), 0) as refunded_quantity
                    FROM order_tasks
                    WHERE order_id = o.id AND refunded = TRUE
                ) task_agg ON TRUE
                WHERE o.status IN ('COMPLETED', 'PARTIAL', 'CANCELLED', 'FAILED')
                  AND (o.refund_amount > 0 OR o.failed_permanent_plays > 0 OR task_agg.refunded_count > 0)
                LIMIT :batchSize
                """)
            .bind("batchSize", batchSize)
            .map((row, metadata) -> new OrderReconciliationData(
                row.get("order_id", UUID.class),
                row.get("quantity", Integer.class),
                row.get("refund_amount", BigDecimal.class),
                row.get("failed_permanent_plays", Integer.class),
                row.get("price_per_unit", BigDecimal.class),
                row.get("refunded_task_count", Long.class).intValue(),
                row.get("refunded_quantity", Long.class).intValue()
            ))
            .all();
    }
    
    /**
     * Reconcile a single order.
     * Returns true if an anomaly was detected and recorded.
     */
    private Mono<Boolean> reconcileOrder(OrderReconciliationData order) {
        // Calculate expected refund amount from task data
        BigDecimal expectedRefund = order.pricePerUnit() != null
            ? order.pricePerUnit().multiply(BigDecimal.valueOf(order.refundedQuantity()))
                .setScale(4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        
        BigDecimal actualRefund = order.refundAmount() != null ? order.refundAmount() : BigDecimal.ZERO;
        int actualFailedPlays = order.failedPermanentPlays() != null ? order.failedPermanentPlays() : 0;
        
        // Check for refund amount mismatch
        BigDecimal refundDiff = expectedRefund.subtract(actualRefund).abs();
        if (refundDiff.compareTo(TOLERANCE) > 0) {
            return recordAnomaly(RefundAnomalyEntity.refundMismatch(
                order.orderId(), expectedRefund, actualRefund, order.refundedTaskCount()
            )).thenReturn(true);
        }
        
        // Check for failed plays mismatch
        if (order.refundedQuantity() != actualFailedPlays) {
            return recordAnomaly(RefundAnomalyEntity.failedPlaysMismatch(
                order.orderId(), order.refundedQuantity(), actualFailedPlays
            )).thenReturn(true);
        }
        
        return Mono.just(false);
    }
    
    /**
     * Record an anomaly if one doesn't already exist for this order/type.
     */
    private Mono<Void> recordAnomaly(RefundAnomalyEntity anomaly) {
        return anomalyRepository.existsByOrderIdAndAnomalyTypeAndResolvedAtIsNull(
                anomaly.getOrderId(), anomaly.getAnomalyType())
            .flatMap(exists -> {
                if (exists) {
                    log.debug("ANOMALY_SKIP_DUPLICATE | orderId={} | type={}", 
                        anomaly.getOrderId(), anomaly.getAnomalyType());
                    return Mono.empty();
                }
                return anomalyRepository.save(anomaly)
                    .doOnSuccess(saved -> {
                        log.warn("ANOMALY_DETECTED | orderId={} | type={} | severity={}", 
                            saved.getOrderId(), saved.getAnomalyType(), saved.getSeverity());
                        anomaliesDetectedCounter.increment();
                    })
                    .then();
            });
    }
    
    // =========================================================================
    // FRAUD DETECTION
    // =========================================================================
    
    /**
     * Check for users with unusually high refund velocity.
     * Runs as part of reconciliation or can be called separately.
     */
    @Scheduled(cron = "${goodfellaz17.fraud.velocity.cron:0 0 * * * *}") // Every hour
    public void checkFraudVelocity() {
        if (!reconciliationEnabled) {
            return;
        }
        
        log.info("FRAUD_VELOCITY_CHECK_START | threshold={} | windowHours={}", 
            fraudVelocityThreshold, fraudVelocityWindowHours);
        
        Instant since = Instant.now().minus(Duration.ofHours(fraudVelocityWindowHours));
        
        refundEventRepository.findHighVelocityRefundUsers(since, fraudVelocityThreshold)
            .doOnNext(user -> log.warn(
                "FRAUD_VELOCITY_ALERT | userId={} | refundCount={} | totalAmount={} | windowHours={}",
                user.userId(), user.refundCount(), user.totalAmount(), fraudVelocityWindowHours))
            .count()
            .doOnSuccess(count -> log.info("FRAUD_VELOCITY_CHECK_COMPLETE | flaggedUsers={}", count))
            .subscribe();
    }
    
    // =========================================================================
    // DTOs
    // =========================================================================
    
    private record OrderReconciliationData(
        UUID orderId,
        Integer quantity,
        BigDecimal refundAmount,
        Integer failedPermanentPlays,
        BigDecimal pricePerUnit,
        int refundedTaskCount,
        int refundedQuantity
    ) {}
    
    public record ReconciliationResult(
        int ordersChecked,
        int anomaliesFound
    ) {}
}
