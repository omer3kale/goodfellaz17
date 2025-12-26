package com.goodfellaz17.infrastructure.persistence.repository;

import com.goodfellaz17.infrastructure.persistence.entity.ApiKeyEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * API Key Repository - Customer wallet operations.
 * R2DBC reactive repository for api_keys table.
 */
@Repository
public interface ApiKeyRepository extends ReactiveCrudRepository<ApiKeyEntity, UUID> {

    /**
     * Find by API key string.
     */
    Mono<ApiKeyEntity> findByApiKey(String apiKey);

    /**
     * Check if API key exists.
     */
    Mono<Boolean> existsByApiKey(String apiKey);

    /**
     * Get balance by API key.
     */
    @Query("SELECT balance FROM api_keys WHERE api_key = :apiKey")
    Mono<BigDecimal> findBalanceByApiKey(String apiKey);

    /**
     * Update balance (add or subtract).
     * For charges: pass negative amount.
     * For deposits/refunds: pass positive amount.
     */
    @Modifying
    @Query("UPDATE api_keys SET balance = balance + :amount, updated_at = NOW() WHERE api_key = :apiKey")
    Mono<Integer> updateBalance(String apiKey, BigDecimal amount);

    /**
     * Update balance and total_spent for charges.
     */
    @Modifying
    @Query("""
        UPDATE api_keys 
        SET balance = balance - :chargeAmount, 
            total_spent = total_spent + :chargeAmount,
            updated_at = NOW() 
        WHERE api_key = :apiKey AND balance >= :chargeAmount
        """)
    Mono<Integer> chargeBalance(String apiKey, BigDecimal chargeAmount);

    /**
     * Add funds (deposit).
     */
    @Modifying
    @Query("UPDATE api_keys SET balance = balance + :amount WHERE api_key = :apiKey")
    Mono<Integer> addFunds(String apiKey, BigDecimal amount);

    /**
     * Refund to balance.
     */
    @Modifying
    @Query("UPDATE api_keys SET balance = balance + :amount WHERE api_key = :apiKey")
    Mono<Integer> refund(String apiKey, BigDecimal amount);
}
