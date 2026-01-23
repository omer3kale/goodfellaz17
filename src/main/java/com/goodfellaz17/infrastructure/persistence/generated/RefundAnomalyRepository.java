package com.goodfellaz17.infrastructure.persistence.generated;

import com.goodfellaz17.domain.model.generated.RefundAnomalyEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Repository for RefundAnomalyEntity - reconciliation anomaly tracking.
 * 
 * @author goodfellaz17
 * @since 1.0.0
 */
@Repository
public interface RefundAnomalyRepository extends R2dbcRepository<RefundAnomalyEntity, UUID> {
    
    /**
     * Find unresolved anomalies.
     */
    @Query("""
        SELECT * FROM refund_anomalies
        WHERE resolved_at IS NULL
        ORDER BY 
            CASE severity 
                WHEN 'CRITICAL' THEN 1 
                WHEN 'WARNING' THEN 2 
                ELSE 3 
            END,
            detected_at DESC
        """)
    Flux<RefundAnomalyEntity> findUnresolvedAnomalies();
    
    /**
     * Find anomalies by severity.
     */
    Flux<RefundAnomalyEntity> findBySeverityAndResolvedAtIsNull(String severity);
    
    /**
     * Find anomalies for an order.
     */
    Flux<RefundAnomalyEntity> findByOrderId(UUID orderId);
    
    /**
     * Count unresolved anomalies by severity.
     */
    @Query("""
        SELECT severity, COUNT(*) as count
        FROM refund_anomalies
        WHERE resolved_at IS NULL
        GROUP BY severity
        """)
    Flux<SeverityCount> countUnresolvedBySeverity();
    
    /**
     * Check if an anomaly already exists for this order and type (avoid duplicates).
     */
    Mono<Boolean> existsByOrderIdAndAnomalyTypeAndResolvedAtIsNull(UUID orderId, String anomalyType);
    
    /**
     * Mark an anomaly as resolved.
     */
    @Query("""
        UPDATE refund_anomalies 
        SET resolved_at = :resolvedAt, resolution_notes = :notes
        WHERE id = :id
        """)
    Mono<Integer> resolve(UUID id, Instant resolvedAt, String notes);
    
    record SeverityCount(String severity, Long count) {}
}
