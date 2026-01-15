package com.goodfellaz17.infrastructure.persistence.generated;

import com.goodfellaz17.domain.model.generated.DeviceNodeEntity;
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
 * Repository: GeneratedDeviceNodeRepository
 * Entity: DeviceNode
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
@Repository
public interface GeneratedDeviceNodeRepository 
        extends R2dbcRepository<DeviceNodeEntity, UUID> {
    
    // === Basic Queries ===
    
    Mono<DeviceNodeEntity> findByDeviceId(String deviceId);
    
    Flux<DeviceNodeEntity> findByIsActiveTrue();
    
    Flux<DeviceNodeEntity> findByPlatform(String platform);
    
    Flux<DeviceNodeEntity> findByBrowser(String browser);
    
    // === Fingerprint Selection ===
    
    @Query("""
        SELECT * FROM device_nodes 
        WHERE is_active = true 
        AND (banned_sessions::DECIMAL / NULLIF(total_sessions, 0)) < :maxBanRate
        ORDER BY RANDOM()
        LIMIT :limit
        """)
    Flux<DeviceNodeEntity> findRandomHealthyDevices(double maxBanRate, int limit);
    
    @Query("""
        SELECT * FROM device_nodes 
        WHERE is_active = true 
        AND platform = :platform
        AND (banned_sessions::DECIMAL / NULLIF(total_sessions, 0)) < :maxBanRate
        ORDER BY RANDOM()
        LIMIT 1
        """)
    Mono<DeviceNodeEntity> findRandomHealthyByPlatform(String platform, double maxBanRate);
    
    @Query("""
        SELECT * FROM device_nodes 
        WHERE is_active = true 
        AND timezone = :timezone
        ORDER BY total_sessions ASC
        LIMIT 1
        """)
    Mono<DeviceNodeEntity> findLeastUsedByTimezone(String timezone);
    
    // === Geo-Compatible Selection ===
    
    @Query("""
        SELECT * FROM device_nodes 
        WHERE is_active = true 
        AND language LIKE :languagePrefix || '%'
        ORDER BY successful_sessions DESC
        LIMIT :limit
        """)
    Flux<DeviceNodeEntity> findByLanguagePrefix(String languagePrefix, int limit);
    
    // === Health Monitoring ===
    
    @Query("""
        SELECT * FROM device_nodes 
        WHERE is_active = true 
        AND total_sessions > 0
        AND (successful_sessions::DECIMAL / total_sessions) < :threshold
        """)
    Flux<DeviceNodeEntity> findLowPerformingDevices(double threshold);
    
    @Query("""
        SELECT * FROM device_nodes 
        WHERE is_active = true 
        AND (banned_sessions::DECIMAL / NULLIF(total_sessions, 0)) > :threshold
        """)
    Flux<DeviceNodeEntity> findHighBanRateDevices(double threshold);
    
    // === Session Tracking ===
    
    @Query("""
        UPDATE device_nodes SET 
            total_sessions = total_sessions + 1,
            successful_sessions = successful_sessions + :success,
            banned_sessions = banned_sessions + :banned,
            last_used = NOW()
        WHERE id = :id
        """)
    Mono<Void> recordSessionResult(UUID id, int success, int banned);
    
    @Query("""
        UPDATE device_nodes SET is_active = false
        WHERE (banned_sessions::DECIMAL / NULLIF(total_sessions, 0)) > :threshold
        RETURNING id
        """)
    Flux<UUID> disableHighBanRateDevices(double threshold);
    
    // === Statistics ===
    
    @Query("SELECT COUNT(*) FROM device_nodes WHERE is_active = true")
    Mono<Long> countActive();
    
    @Query("""
        SELECT platform, COUNT(*) as count 
        FROM device_nodes 
        WHERE is_active = true 
        GROUP BY platform
        """)
    Flux<Object[]> countByPlatform();
    
    @Query("""
        SELECT browser, COUNT(*) as count 
        FROM device_nodes 
        WHERE is_active = true 
        GROUP BY browser
        """)
    Flux<Object[]> countByBrowser();
    
    @Query("""
        SELECT 
            COUNT(*) FILTER (WHERE is_active = true) as active,
            COUNT(*) FILTER (WHERE is_active = false) as inactive,
            AVG(successful_sessions::DECIMAL / NULLIF(total_sessions, 0)) as avg_success_rate
        FROM device_nodes
        """)
    Mono<Object[]> getDevicePoolStats();
}
