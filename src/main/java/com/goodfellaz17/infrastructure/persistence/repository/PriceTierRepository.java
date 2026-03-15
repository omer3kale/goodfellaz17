package com.goodfellaz17.infrastructure.persistence.repository;

import com.goodfellaz17.infrastructure.persistence.entity.PriceTierEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * PriceTierRepository — Reactive CRUD for price_tiers table.
 */
@Repository
public interface PriceTierRepository extends R2dbcRepository<PriceTierEntity, UUID> {

    /**
     * Find all price tiers for a specific service.
     */
    Flux<PriceTierEntity> findByServiceId(UUID serviceId);

    /**
     * Find all active price tiers for a specific service.
     */
    @Query("SELECT * FROM price_tiers WHERE service_id = :serviceId AND active = 1 ORDER BY min_quantity ASC")
    Flux<PriceTierEntity> findByServiceIdAndActive(UUID serviceId);

    /**
     * Count price tiers for a specific service.
     */
    Mono<Long> countByServiceId(UUID serviceId);

    /**
     * Count all price tiers.
     */
    Mono<Long> count();

    /**
     * Find price tier by ID.
     */
    @Query("SELECT * FROM price_tiers WHERE id = :id")
    Mono<PriceTierEntity> findByIdQuery(UUID id);
}
