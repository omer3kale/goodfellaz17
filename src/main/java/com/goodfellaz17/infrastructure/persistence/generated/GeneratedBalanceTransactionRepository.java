package com.goodfellaz17.infrastructure.persistence.generated;

import com.goodfellaz17.domain.model.generated.BalanceTransactionEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * Repository: GeneratedBalanceTransactionRepository
 * Entity: BalanceTransaction
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
@Repository
public interface GeneratedBalanceTransactionRepository 
        extends R2dbcRepository<BalanceTransactionEntity, UUID> {
    
    // === Basic Queries ===
    
    Flux<BalanceTransactionEntity> findByUserId(UUID userId);
    
    Flux<BalanceTransactionEntity> findByUserIdOrderByTimestampDesc(UUID userId);
    
    Flux<BalanceTransactionEntity> findByOrderId(UUID orderId);
    
    Flux<BalanceTransactionEntity> findByType(String type);
    
    // === Paginated History ===
    
    @Query("""
        SELECT * FROM balance_transactions 
        WHERE user_id = :userId
        ORDER BY timestamp DESC
        LIMIT :limit OFFSET :offset
        """)
    Flux<BalanceTransactionEntity> findByUserIdPaginated(UUID userId, int limit, int offset);
    
    @Query("""
        SELECT COUNT(*) FROM balance_transactions WHERE user_id = :userId
        """)
    Mono<Long> countByUserId(UUID userId);
    
    // === Time Range Queries ===
    
    @Query("""
        SELECT * FROM balance_transactions 
        WHERE user_id = :userId 
        AND timestamp >= :start 
        AND timestamp < :end
        ORDER BY timestamp DESC
        """)
    Flux<BalanceTransactionEntity> findByUserIdInRange(UUID userId, Instant start, Instant end);
    
    @Query("""
        SELECT * FROM balance_transactions 
        WHERE type = :type 
        AND timestamp >= :start 
        AND timestamp < :end
        ORDER BY timestamp DESC
        """)
    Flux<BalanceTransactionEntity> findByTypeInRange(String type, Instant start, Instant end);
    
    // === Aggregation Queries ===
    
    @Query("""
        SELECT COALESCE(SUM(amount), 0) 
        FROM balance_transactions 
        WHERE user_id = :userId AND type = 'CREDIT'
        """)
    Mono<BigDecimal> sumCreditsForUser(UUID userId);
    
    @Query("""
        SELECT COALESCE(SUM(ABS(amount)), 0) 
        FROM balance_transactions 
        WHERE user_id = :userId AND type = 'DEBIT'
        """)
    Mono<BigDecimal> sumDebitsForUser(UUID userId);
    
    @Query("""
        SELECT COALESCE(SUM(amount), 0) 
        FROM balance_transactions 
        WHERE user_id = :userId AND type = 'REFUND'
        """)
    Mono<BigDecimal> sumRefundsForUser(UUID userId);
    
    // === Revenue Analytics ===
    
    @Query("""
        SELECT COALESCE(SUM(ABS(amount)), 0) 
        FROM balance_transactions 
        WHERE type = 'DEBIT' 
        AND timestamp >= :start 
        AND timestamp < :end
        """)
    Mono<BigDecimal> sumRevenueInPeriod(Instant start, Instant end);
    
    @Query("""
        SELECT COALESCE(SUM(amount), 0) 
        FROM balance_transactions 
        WHERE type = 'CREDIT' 
        AND payment_provider = :provider
        AND timestamp >= :start 
        AND timestamp < :end
        """)
    Mono<BigDecimal> sumDepositsByProviderInPeriod(String provider, Instant start, Instant end);
    
    @Query("""
        SELECT DATE_TRUNC('day', timestamp) as day, 
               COALESCE(SUM(ABS(amount)), 0) as revenue
        FROM balance_transactions 
        WHERE type = 'DEBIT' 
        AND timestamp >= :start 
        AND timestamp < :end
        GROUP BY DATE_TRUNC('day', timestamp)
        ORDER BY day
        """)
    Flux<Object[]> dailyRevenueInPeriod(Instant start, Instant end);
    
    // === Audit Queries ===
    
    @Query("""
        SELECT * FROM balance_transactions 
        WHERE external_tx_id = :externalTxId
        """)
    Mono<BalanceTransactionEntity> findByExternalTxId(String externalTxId);
    
    @Query("""
        SELECT * FROM balance_transactions 
        WHERE payment_provider = :provider 
        ORDER BY timestamp DESC 
        LIMIT :limit
        """)
    Flux<BalanceTransactionEntity> findRecentByProvider(String provider, int limit);
}
