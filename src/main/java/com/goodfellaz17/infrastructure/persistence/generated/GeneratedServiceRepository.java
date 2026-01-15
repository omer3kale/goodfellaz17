package com.goodfellaz17.infrastructure.persistence.generated;

import com.goodfellaz17.domain.model.generated.ServiceEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * Repository: GeneratedServiceRepository
 * Entity: Service
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
@Repository
public interface GeneratedServiceRepository extends R2dbcRepository<ServiceEntity, UUID> {
    
    // === Basic Queries ===
    
    Mono<ServiceEntity> findByName(String name);
    
    Flux<ServiceEntity> findByServiceType(String serviceType);
    
    Flux<ServiceEntity> findByIsActiveTrue();
    
    Flux<ServiceEntity> findByIsActiveTrueOrderBySortOrderAsc();
    
    // === Catalog Queries ===
    
    @Query("""
        SELECT * FROM services 
        WHERE is_active = true 
        AND service_type = :serviceType
        ORDER BY sort_order ASC
        """)
    Flux<ServiceEntity> findActiveByType(String serviceType);
    
    @Query("""
        SELECT * FROM services 
        WHERE is_active = true 
        AND geo_profiles::jsonb @> :geoProfile::jsonb
        ORDER BY sort_order ASC
        """)
    Flux<ServiceEntity> findActiveByGeoProfile(String geoProfile);
    
    @Query("""
        SELECT * FROM services 
        WHERE is_active = true 
        AND cost_per_1k <= :maxCost
        ORDER BY cost_per_1k ASC
        """)
    Flux<ServiceEntity> findActiveBelowPrice(BigDecimal maxCost);
    
    // === Pricing Queries ===
    
    @Query("""
        SELECT 
            CASE :tier
                WHEN 'CONSUMER' THEN cost_per_1k
                WHEN 'RESELLER' THEN reseller_cost_per_1k
                WHEN 'AGENCY' THEN agency_cost_per_1k
            END as price
        FROM services 
        WHERE id = :serviceId
        """)
    Mono<BigDecimal> getPriceForTier(UUID serviceId, String tier);
    
    // === Admin Queries ===
    
    @Query("""
        SELECT DISTINCT service_type FROM services 
        WHERE is_active = true
        ORDER BY service_type
        """)
    Flux<String> findAllActiveServiceTypes();
    
    @Query("""
        SELECT COUNT(*) FROM services WHERE is_active = true
        """)
    Mono<Long> countActive();
    
    @Query("""
        UPDATE services SET is_active = :isActive, updated_at = NOW()
        WHERE id = :id
        """)
    Mono<Void> updateActiveStatus(UUID id, boolean isActive);
    
    @Query("""
        UPDATE services SET 
            cost_per_1k = :costPer1k,
            reseller_cost_per_1k = :resellerCostPer1k,
            agency_cost_per_1k = :agencyCostPer1k,
            updated_at = NOW()
        WHERE id = :id
        """)
    Mono<Void> updatePricing(UUID id, BigDecimal costPer1k, 
            BigDecimal resellerCostPer1k, BigDecimal agencyCostPer1k);
}
