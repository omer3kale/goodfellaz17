package com.goodfellaz17.infrastructure.persistence.repository;

import com.goodfellaz17.infrastructure.persistence.entity.ServiceEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * ServiceRepository — Reactive CRUD for services table.
 */
@Repository
public interface ServiceRepository extends R2dbcRepository<ServiceEntity, UUID> {

    /**
     * Find service by code (e.g., 'spotify_plays', 'spotify_followers').
     */
    Mono<ServiceEntity> findByCode(String code);

    /**
     * Find all active/inactive services.
     */
    Flux<ServiceEntity> findByActive(Boolean active);

    /**
     * Find service by ID (convenience method).
     */
    @Query("SELECT * FROM services WHERE id = :id")
    Mono<ServiceEntity> findByIdQuery(UUID id);

    /**
     * Count services by active status.
     */
    Mono<Long> countByActive(Boolean active);

    /**
     * Count all services.
     */
    Mono<Long> count();
}
