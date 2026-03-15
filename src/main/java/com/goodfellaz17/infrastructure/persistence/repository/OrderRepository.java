package com.goodfellaz17.infrastructure.persistence.repository;

import com.goodfellaz17.infrastructure.persistence.entity.OrderEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * OrderRepository — R2DBC reactive repository for orders table (V15 MSSQL schema).
 * Handles CRUD + custom queries for Order aggregates.
 */
@Repository
public interface OrderRepository extends ReactiveCrudRepository<OrderEntity, UUID> {

    /**
     * Find all orders for a tenant.
     */
    Flux<OrderEntity> findByTenantId(UUID tenantId);

    /**
     * Count orders for a tenant.
     */
    Mono<Long> countByTenantId(UUID tenantId);

    /**
     * Find orders by status (PENDING, ACTIVE, DELIVERING, COMPLETED, FAILED).
     */
    Flux<OrderEntity> findByStatus(String status);

    /**
     * Count orders by status.
     */
    Mono<Long> countByStatus(String status);

    /**
     * Find orders by service.
     */
    Flux<OrderEntity> findByServiceId(UUID serviceId);

    /**
     * Count orders by service.
     */
    Mono<Long> countByServiceId(UUID serviceId);

    /**
     * Find pending orders (status = 'PENDING') for background executor.
     */
    @Query("SELECT * FROM orders WHERE status = 'PENDING' ORDER BY created_at ASC")
    Flux<OrderEntity> findPendingOrders();

    /**
     * Find orders created by a specific API key.
     */
    Flux<OrderEntity> findByCreatedByApiKeyId(UUID apiKeyId);
}
