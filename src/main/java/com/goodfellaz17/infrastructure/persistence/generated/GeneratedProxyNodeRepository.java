package com.goodfellaz17.infrastructure.persistence.generated;

import com.goodfellaz17.domain.model.generated.ProxyNodeEntity;
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
 * Repository: ProxyNodeRepository
 * 
 * R2DBC reactive repository for ProxyNode entity operations.
 * Optimized for high-throughput proxy selection.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
@Repository
public interface GeneratedProxyNodeRepository extends R2dbcRepository<ProxyNodeEntity, UUID> {
    
    // === Basic Queries ===
    
    /**
     * Find proxies by tier and status.
     */
    Flux<ProxyNodeEntity> findByTierAndStatus(String tier, String status);
    
    /**
     * Find proxies by provider and status.
     */
    Flux<ProxyNodeEntity> findByProviderAndStatus(String provider, String status);
    
    /**
     * Find proxies by region, tier, and status.
     */
    Flux<ProxyNodeEntity> findByRegionAndTierAndStatus(String region, String tier, String status);
    
    /**
     * Find proxies by country, tier, and status.
     */
    Flux<ProxyNodeEntity> findByCountryAndTierAndStatus(String country, String tier, String status);
    
    /**
     * Find proxy by public IP.
     */
    Mono<ProxyNodeEntity> findByPublicIp(String publicIp);
    
    // === Count Queries ===
    
    /**
     * Count proxies by tier and status.
     */
    Mono<Long> countByTierAndStatus(String tier, String status);
    
    /**
     * Count all online proxies.
     */
    Mono<Long> countByStatus(String status);
    
    /**
     * Find distinct tiers that have ONLINE nodes with capacity.
     * Used to determine which ProxyTier enum values are actually backed by data.
     */
    @Query("SELECT DISTINCT tier FROM proxy_nodes WHERE status = 'ONLINE' AND current_load < capacity")
    Flux<String> findDistinctTiers();
    
    // === Custom Queries for Proxy Selection ===
    
    /**
     * Find available proxies for a tier with capacity.
     */
    @Query("""
        SELECT * FROM proxy_nodes 
        WHERE tier = :tier 
          AND status = 'ONLINE' 
          AND current_load < capacity
        ORDER BY current_load ASC
        LIMIT :limit
        """)
    Flux<ProxyNodeEntity> findAvailableByTier(String tier, int limit);
    
    /**
     * Find available proxies for country and tier.
     */
    @Query("""
        SELECT * FROM proxy_nodes 
        WHERE country = :country 
          AND tier = :tier 
          AND status = 'ONLINE' 
          AND current_load < capacity
        ORDER BY current_load ASC
        LIMIT :limit
        """)
    Flux<ProxyNodeEntity> findAvailableByCountryAndTier(String country, String tier, int limit);
    
    /**
     * Find best proxies based on metrics join.
     */
    @Query("""
        SELECT pn.* FROM proxy_nodes pn
        LEFT JOIN proxy_metrics pm ON pn.id = pm.proxy_node_id
        WHERE pn.tier = :tier 
          AND pn.status = 'ONLINE' 
          AND pn.current_load < pn.capacity
          AND (pm.success_rate IS NULL OR pm.success_rate >= :minSuccessRate)
        ORDER BY COALESCE(pm.success_rate, 1.0) DESC, pn.current_load ASC
        LIMIT :limit
        """)
    Flux<ProxyNodeEntity> findBestByTier(String tier, double minSuccessRate, int limit);
    
    /**
     * Increment proxy load atomically.
     */
    @Query("UPDATE proxy_nodes SET current_load = current_load + 1 WHERE id = :proxyId AND current_load < capacity RETURNING *")
    Mono<ProxyNodeEntity> incrementLoad(UUID proxyId);
    
    /**
     * Decrement proxy load atomically.
     */
    @Query("UPDATE proxy_nodes SET current_load = GREATEST(0, current_load - 1) WHERE id = :proxyId RETURNING *")
    Mono<ProxyNodeEntity> decrementLoad(UUID proxyId);
    
    /**
     * Update healthcheck timestamp.
     */
    @Query("UPDATE proxy_nodes SET last_healthcheck = CURRENT_TIMESTAMP WHERE id = :proxyId RETURNING *")
    Mono<ProxyNodeEntity> recordHealthcheck(UUID proxyId);
    
    /**
     * Mark proxy as offline.
     */
    @Query("UPDATE proxy_nodes SET status = 'OFFLINE' WHERE id = :proxyId RETURNING *")
    Mono<ProxyNodeEntity> markOffline(UUID proxyId);
    
    /**
     * Mark proxy as online.
     */
    @Query("UPDATE proxy_nodes SET status = 'ONLINE', last_healthcheck = CURRENT_TIMESTAMP WHERE id = :proxyId RETURNING *")
    Mono<ProxyNodeEntity> markOnline(UUID proxyId);
    
    /**
     * Find proxies needing healthcheck.
     */
    @Query("SELECT * FROM proxy_nodes WHERE status = 'ONLINE' AND (last_healthcheck IS NULL OR last_healthcheck < :cutoff)")
    Flux<ProxyNodeEntity> findNeedingHealthcheck(Instant cutoff);
    
    /**
     * Get pool statistics.
     */
    @Query("""
        SELECT tier, status, 
               COUNT(*) as count, 
               SUM(capacity) as total_capacity, 
               SUM(current_load) as total_load
        FROM proxy_nodes 
        GROUP BY tier, status
        """)
    Flux<PoolStatistics> getPoolStatistics();
    
    /**
     * Statistics projection interface.
     */
    interface PoolStatistics {
        String getTier();
        String getStatus();
        Long getCount();
        Long getTotalCapacity();
        Long getTotalLoad();
    }
}
