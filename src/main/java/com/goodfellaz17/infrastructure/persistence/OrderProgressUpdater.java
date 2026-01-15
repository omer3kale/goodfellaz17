package com.goodfellaz17.infrastructure.persistence;

import com.goodfellaz17.domain.model.generated.OrderEntity;
import com.goodfellaz17.infrastructure.persistence.generated.GeneratedOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Service for atomic order progress updates using DatabaseClient.
 * 
 * Spring Data R2DBC @Query with @Modifying doesn't work well for UPDATE statements,
 * so we use DatabaseClient directly for atomic increments.
 */
@Service
public class OrderProgressUpdater {
    
    private static final Logger log = LoggerFactory.getLogger(OrderProgressUpdater.class);
    
    private final DatabaseClient databaseClient;
    private final GeneratedOrderRepository orderRepository;
    
    public OrderProgressUpdater(DatabaseClient databaseClient, GeneratedOrderRepository orderRepository) {
        this.databaseClient = databaseClient;
        this.orderRepository = orderRepository;
    }
    
    /**
     * Atomically increment delivered count and decrement remains.
     * Uses raw SQL UPDATE to prevent lost updates from concurrent task completions.
     * Returns the updated order for completion checking.
     */
    public Mono<OrderEntity> atomicIncrementDelivered(UUID orderId, int deliveredPlays) {
        // R2DBC DatabaseClient uses :name parameter placeholders with .bind("name", value)
        // Note: orders table has no updated_at column - only created_at, started_at, completed_at
        return databaseClient.sql(
                "UPDATE orders SET delivered = delivered + :plays, " +
                "remains = GREATEST(0, quantity - (delivered + :plays)) " +
                "WHERE id = :orderId")
            .bind("plays", deliveredPlays)
            .bind("orderId", orderId)
            .fetch()
            .rowsUpdated()
            .doOnNext(rows -> log.debug("ORDER_INCREMENT | orderId={} | plays={} | rows={}", 
                orderId, deliveredPlays, rows))
            .then(orderRepository.findById(orderId));
    }
    
    /**
     * Atomically increment failed_permanent_plays count.
     */
    public Mono<Integer> atomicIncrementFailedPermanent(UUID orderId, int failedPlays) {
        // R2DBC DatabaseClient uses :name parameter placeholders with .bind("name", value)
        // Note: orders table has no updated_at column
        return databaseClient.sql(
                "UPDATE orders SET failed_permanent_plays = COALESCE(failed_permanent_plays, 0) + :plays " +
                "WHERE id = :orderId")
            .bind("plays", failedPlays)
            .bind("orderId", orderId)
            .fetch()
            .rowsUpdated()
            .map(Long::intValue)
            .doOnNext(rows -> log.debug("ORDER_FAILED_INCREMENT | orderId={} | failedPlays={} | rows={}", 
                orderId, failedPlays, rows));
    }
}
