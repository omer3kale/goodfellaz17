package com.goodfellaz17.infrastructure.persistence.generated;

import com.goodfellaz17.domain.model.generated.ProxyMetricsEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * Repository: GeneratedProxyMetricsRepository
 * Entity: ProxyMetrics
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
@Repository
public interface GeneratedProxyMetricsRepository 
        extends R2dbcRepository<ProxyMetricsEntity, UUID> {
    
    // === Basic Queries ===
    
    Mono<ProxyMetricsEntity> findByProxyNodeId(UUID proxyNodeId);
    
    // === Health Queries ===
    
    @Query("""
        SELECT * FROM proxy_metrics 
        WHERE success_rate >= :minSuccessRate 
        AND ban_rate <= :maxBanRate
        """)
    Flux<ProxyMetricsEntity> findHealthyMetrics(double minSuccessRate, double maxBanRate);
    
    @Query("""
        SELECT * FROM proxy_metrics 
        WHERE success_rate < :threshold
        ORDER BY success_rate ASC
        """)
    Flux<ProxyMetricsEntity> findUnhealthyMetrics(double threshold);
    
    // === Performance Queries ===
    
    @Query("""
        SELECT * FROM proxy_metrics 
        WHERE latency_p95 <= :maxLatency
        ORDER BY latency_p95 ASC
        """)
    Flux<ProxyMetricsEntity> findByMaxLatency(int maxLatency);
    
    @Query("""
        SELECT pm.* FROM proxy_metrics pm
        JOIN proxy_nodes pn ON pm.proxy_node_id = pn.id
        WHERE pn.tier = :tier
        ORDER BY pm.success_rate DESC, pm.latency_p95 ASC
        """)
    Flux<ProxyMetricsEntity> findByTierSortedByPerformance(String tier);
    
    // === Cost Tracking ===
    
    @Query("""
        SELECT COALESCE(SUM(estimated_cost), 0) FROM proxy_metrics 
        WHERE window_start >= :start AND window_start < :end
        """)
    Mono<BigDecimal> sumEstimatedCostInPeriod(Instant start, Instant end);
    
    @Query("""
        SELECT COALESCE(SUM(bytes_transferred), 0) FROM proxy_metrics 
        WHERE window_start >= :start AND window_start < :end
        """)
    Mono<Long> sumBytesTransferredInPeriod(Instant start, Instant end);
    
    // === Metrics Update ===
    
    @Query("""
        UPDATE proxy_metrics SET 
            total_requests = total_requests + 1,
            successful_requests = successful_requests + 1,
            bytes_transferred = bytes_transferred + :bytes,
            success_rate = (successful_requests + 1)::DECIMAL / (total_requests + 1),
            last_updated = NOW()
        WHERE proxy_node_id = :proxyNodeId
        """)
    Mono<Void> recordSuccess(UUID proxyNodeId, long bytes);
    
    @Query("""
        UPDATE proxy_metrics SET 
            total_requests = total_requests + 1,
            failed_requests = failed_requests + 1,
            success_rate = successful_requests::DECIMAL / (total_requests + 1),
            last_updated = NOW()
        WHERE proxy_node_id = :proxyNodeId
        """)
    Mono<Void> recordFailure(UUID proxyNodeId);
    
    @Query("""
        UPDATE proxy_metrics SET 
            latency_p50 = :p50,
            latency_p95 = :p95,
            latency_p99 = :p99,
            last_updated = NOW()
        WHERE proxy_node_id = :proxyNodeId
        """)
    Mono<Void> updateLatencyPercentiles(UUID proxyNodeId, int p50, int p95, int p99);
    
    @Query("""
        UPDATE proxy_metrics SET 
            active_connections = :active,
            peak_connections = GREATEST(peak_connections, :active),
            last_updated = NOW()
        WHERE proxy_node_id = :proxyNodeId
        """)
    Mono<Void> updateConnections(UUID proxyNodeId, int active);
    
    // === Window Reset ===
    
    @Query("""
        UPDATE proxy_metrics SET 
            total_requests = 0,
            successful_requests = 0,
            failed_requests = 0,
            success_rate = 1.0,
            ban_rate = 0.0,
            bytes_transferred = 0,
            estimated_cost = 0,
            window_start = NOW(),
            last_updated = NOW()
        WHERE proxy_node_id = :proxyNodeId
        """)
    Mono<Void> resetMetricsWindow(UUID proxyNodeId);
    
    @Query("""
        UPDATE proxy_metrics SET 
            total_requests = 0,
            successful_requests = 0,
            failed_requests = 0,
            success_rate = 1.0,
            ban_rate = 0.0,
            bytes_transferred = 0,
            estimated_cost = 0,
            window_start = NOW(),
            last_updated = NOW()
        WHERE window_start < :threshold
        """)
    Mono<Long> resetOldMetricsWindows(Instant threshold);
    
    // === Dashboard Queries ===
    
    @Query("""
        SELECT 
            COALESCE(SUM(total_requests), 0) as total_requests,
            COALESCE(SUM(successful_requests), 0) as successful_requests,
            COALESCE(AVG(success_rate), 0) as avg_success_rate,
            COALESCE(AVG(latency_p95), 0) as avg_latency,
            COALESCE(SUM(bytes_transferred), 0) as total_bytes
        FROM proxy_metrics
        WHERE last_updated > NOW() - INTERVAL '1 hour'
        """)
    Mono<Object[]> getPoolSummary();
}
