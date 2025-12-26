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
     * Find by service_id string.
     */
    Mono<ServiceEntity> findByServiceId(String serviceId);

    /**
     * Find all active services.
     */
    Flux<ServiceEntity> findByIsActiveTrue();

    /**
     * Find by category.
     */
    Flux<ServiceEntity> findByCategory(String category);

    /**
     * Find by category (active only).
     */
    Flux<ServiceEntity> findByCategoryAndIsActiveTrue(String category);

    /**
     * Find by speed tier.
     */
    Flux<ServiceEntity> findBySpeedTier(String speedTier);

    /**
     * Count active services.
     */
    Mono<Long> countByIsActiveTrue();

    /**
     * Find all ordered by price.
     */
    @Query("SELECT * FROM services WHERE is_active = true ORDER BY price_per_1000 ASC")
    Flux<ServiceEntity> findAllActiveOrderedByPrice();

    /**
     * Find all ordered by category then price.
     */
    @Query("SELECT * FROM services WHERE is_active = true ORDER BY category, price_per_1000 ASC")
    Flux<ServiceEntity> findAllActiveOrderedByCategoryAndPrice();
}
