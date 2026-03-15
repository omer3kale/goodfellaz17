package com.goodfellaz17.infrastructure.persistence.repository;

import com.goodfellaz17.infrastructure.persistence.entity.OrderMSSQLEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Order MSSQL Repository — FTL-B MSSQL schema.
 * 
 * Reactive repository for the new MSSQL orders table.
 * Supports finding orders by ID, tenant, status, and other dimensions.
 */
@Repository
public interface OrderMSSQLRepository extends ReactiveCrudRepository<OrderMSSQLEntity, UUID> {

    /**
     * Find an order by ID and tenant (isolation check).
     */
    Mono<OrderMSSQLEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Find an order by ID only (for read endpoints).
     */
    @Query("SELECT * FROM orders WHERE id = :id")
    Mono<OrderMSSQLEntity> findByIdQuery(UUID id);

    /**
     * Count orders for a tenant (metrics).
     */
    @Query("SELECT COUNT(*) FROM orders WHERE tenant_id = :tenantId")
    Mono<Long> countByTenantId(UUID tenantId);

    /**
     * Check if MSSQL connection is alive (health check).
     */
    @Query("SELECT COUNT(*) FROM orders")
    Mono<Long> countAll();
}
