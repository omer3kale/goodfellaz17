package com.goodfellaz17.infrastructure.persistence.repository;

import com.goodfellaz17.infrastructure.persistence.entity.TenantEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * TenantRepository — Reactive CRUD for tenants table.
 */
@Repository
public interface TenantRepository extends R2dbcRepository<TenantEntity, UUID> {

    /**
     * Find tenant by code.
     */
    Mono<TenantEntity> findByCode(String code);

    /**
     * Find all active/inactive tenants.
     */
    Flux<TenantEntity> findByActive(Boolean active);

    /**
     * Count active/inactive tenants.
     */
    Mono<Long> countByActive(Boolean active);

    /**
     * Count all tenants.
     */
    Mono<Long> count();

    /**
     * Find tenant by ID with custom query.
     */
    @Query("SELECT * FROM tenants WHERE id = :id")
    Mono<TenantEntity> findByIdQuery(UUID id);
}
