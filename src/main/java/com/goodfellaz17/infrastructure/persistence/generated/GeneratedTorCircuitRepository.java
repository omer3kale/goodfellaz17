package com.goodfellaz17.infrastructure.persistence.generated;

import com.goodfellaz17.domain.model.generated.TorCircuitEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * Repository: GeneratedTorCircuitRepository
 * Entity: TorCircuit
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
@Repository
public interface GeneratedTorCircuitRepository 
        extends R2dbcRepository<TorCircuitEntity, UUID> {
    
    // === Basic Queries ===
    
    Mono<TorCircuitEntity> findByCircuitId(String circuitId);
    
    Flux<TorCircuitEntity> findByStatus(String status);
    
    Flux<TorCircuitEntity> findByExitCountry(String exitCountry);
    
    // === Active Circuit Selection ===
    
    @Query("""
        SELECT * FROM tor_circuits 
        WHERE status = 'ACTIVE' 
        AND (expires_at IS NULL OR expires_at > NOW())
        ORDER BY bandwidth_kbps DESC
        LIMIT :limit
        """)
    Flux<TorCircuitEntity> findActiveHighBandwidth(int limit);
    
    @Query("""
        SELECT * FROM tor_circuits 
        WHERE status = 'ACTIVE' 
        AND exit_country = :country
        AND (expires_at IS NULL OR expires_at > NOW())
        ORDER BY bandwidth_kbps DESC
        LIMIT 1
        """)
    Mono<TorCircuitEntity> findActiveByCountry(String country);
    
    @Query("""
        SELECT * FROM tor_circuits 
        WHERE status = 'ACTIVE' 
        AND is_stable = true
        AND (expires_at IS NULL OR expires_at > NOW())
        ORDER BY latency_ms ASC
        LIMIT :limit
        """)
    Flux<TorCircuitEntity> findStableCircuits(int limit);
    
    // === Rotation Management ===
    
    @Query("""
        SELECT * FROM tor_circuits 
        WHERE status = 'ACTIVE' 
        AND (
            expires_at < NOW() 
            OR (last_rotated IS NOT NULL AND last_rotated < NOW() - INTERVAL '10 minutes')
        )
        """)
    Flux<TorCircuitEntity> findCircuitsNeedingRotation();
    
    @Query("""
        UPDATE tor_circuits SET 
            circuit_id = :newCircuitId,
            exit_ip = :newExitIp,
            exit_country = :newExitCountry,
            last_rotated = NOW(),
            requests_served = 0,
            bytes_transferred = 0
        WHERE id = :id
        """)
    Mono<Void> rotateCircuit(UUID id, String newCircuitId, String newExitIp, String newExitCountry);
    
    // === Traffic Recording ===
    
    @Query("""
        UPDATE tor_circuits SET 
            requests_served = requests_served + 1,
            bytes_transferred = bytes_transferred + :bytes
        WHERE id = :id
        """)
    Mono<Void> recordRequest(UUID id, long bytes);
    
    // === Circuit Lifecycle ===
    
    @Query("""
        UPDATE tor_circuits SET status = 'CLOSED'
        WHERE id = :id
        """)
    Mono<Void> closeCircuit(UUID id);
    
    @Query("""
        UPDATE tor_circuits SET status = 'CLOSED'
        WHERE status = 'ACTIVE' AND expires_at < NOW()
        RETURNING id
        """)
    Flux<UUID> closeExpiredCircuits();
    
    @Query("""
        DELETE FROM tor_circuits 
        WHERE status = 'CLOSED' AND created_at < :threshold
        """)
    Mono<Long> deleteOldClosedCircuits(Instant threshold);
    
    // === Statistics ===
    
    @Query("""
        SELECT COUNT(*) FROM tor_circuits WHERE status = 'ACTIVE'
        """)
    Mono<Long> countActiveCircuits();
    
    @Query("""
        SELECT exit_country, COUNT(*) as count 
        FROM tor_circuits 
        WHERE status = 'ACTIVE'
        GROUP BY exit_country
        ORDER BY count DESC
        """)
    Flux<Object[]> countActiveByCountry();
    
    @Query("""
        SELECT 
            COUNT(*) as total,
            COUNT(*) FILTER (WHERE status = 'ACTIVE') as active,
            COUNT(*) FILTER (WHERE is_stable = true) as stable,
            AVG(bandwidth_kbps) FILTER (WHERE status = 'ACTIVE') as avg_bandwidth,
            AVG(latency_ms) FILTER (WHERE status = 'ACTIVE') as avg_latency
        FROM tor_circuits
        """)
    Mono<Object[]> getTorPoolStats();
}
