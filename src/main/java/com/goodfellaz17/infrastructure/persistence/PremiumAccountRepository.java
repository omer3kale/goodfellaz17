package com.goodfellaz17.infrastructure.persistence;

import com.goodfellaz17.domain.model.PremiumAccount;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * R2DBC Repository - Premium Accounts from Neon PostgreSQL.
 * 
 * REAL DATABASE - NO MOCKS.
 */
@Repository
public interface PremiumAccountRepository extends ReactiveCrudRepository<PremiumAccount, UUID> {
    
    /**
     * Find all active (non-expired) premium accounts.
     */
    @Query("SELECT * FROM premium_accounts WHERE premium_expiry > :today")
    Flux<PremiumAccount> findAllActive(LocalDate today);
    
    /**
     * Find by region/geo target.
     */
    @Query("SELECT * FROM premium_accounts WHERE region = :region AND premium_expiry > :today")
    Flux<PremiumAccount> findByRegion(String region, LocalDate today);
    
    /**
     * Find account by email.
     */
    Mono<PremiumAccount> findByEmail(String email);
    
    /**
     * Count active accounts.
     */
    @Query("SELECT COUNT(*) FROM premium_accounts WHERE premium_expiry > :today")
    Mono<Long> countActive(LocalDate today);
    
    /**
     * Update refresh token after OAuth.
     */
    @Query("UPDATE premium_accounts SET spotify_refresh_token = :token WHERE id = :id")
    Mono<Void> updateRefreshToken(UUID id, String token);
    
    /**
     * Find accounts with valid refresh tokens.
     */
    @Query("SELECT * FROM premium_accounts WHERE spotify_refresh_token IS NOT NULL AND premium_expiry > :today")
    Flux<PremiumAccount> findWithValidTokens(LocalDate today);
}
