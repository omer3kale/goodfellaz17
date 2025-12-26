package com.goodfellaz17.infrastructure.persistence.repository;

import com.goodfellaz17.infrastructure.persistence.entity.ServiceEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service Repository - Package catalog.
 * R2DBC reactive repository for services table.
 */
@Repository
public interface ServiceRepository extends ReactiveCrudRepository<ServiceEntity, Integer> {

    /**
     * Find by service_id (integer PK).
     */
    @Query("SELECT * FROM services WHERE service_id = :serviceId")
    Mono<ServiceEntity> findByServiceId(Integer serviceId);

    /**
     * Find all active services (all services are active in current schema).
     */
    @Query("SELECT * FROM services ORDER BY rate ASC")
    Flux<ServiceEntity> findByIsActiveTrue();

    /**
     * Find by category.
     */
    Flux<ServiceEntity> findByCategory(String category);

    /**
     * Count all services.
     */
    @Query("SELECT COUNT(*) FROM services")
    Mono<Long> countByIsActiveTrue();

    /**
     * Find all ordered by rate (price).
     */
    @Query("SELECT * FROM services ORDER BY rate ASC")
    Flux<ServiceEntity> findAllActiveOrderedByPrice();

    /**
     * Find all ordered by category then rate.
     */
    @Query("SELECT * FROM services ORDER BY category, rate ASC")
    Flux<ServiceEntity> findAllActiveOrderedByCategoryAndPrice();
}
