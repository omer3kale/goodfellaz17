package com.goodfellaz17.infrastructure.persistence.generated;

import com.goodfellaz17.domain.model.generated.UserEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * Repository: UserRepository
 * 
 * R2DBC reactive repository for User entity operations.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
@Repository
public interface GeneratedUserRepository extends R2dbcRepository<UserEntity, UUID> {
    
    // === Basic Queries ===
    
    /**
     * Find user by email (for login).
     */
    Mono<UserEntity> findByEmail(String email);
    
    /**
     * Find user by API key (for API auth).
     */
    Mono<UserEntity> findByApiKey(String apiKey);
    
    /**
     * Find all users with specific tier.
     */
    Flux<UserEntity> findByTier(String tier);
    
    /**
     * Find all users with specific status.
     */
    Flux<UserEntity> findByStatus(String status);
    
    /**
     * Find users referred by a specific user.
     */
    Flux<UserEntity> findByReferredBy(UUID referredBy);
    
    /**
     * Find user by referral code.
     */
    Mono<UserEntity> findByReferralCode(String referralCode);
    
    // === Count Queries ===
    
    /**
     * Count users by tier and status.
     */
    Mono<Long> countByTierAndStatus(String tier, String status);
    
    /**
     * Count all active users.
     */
    Mono<Long> countByStatus(String status);
    
    // === Custom Queries ===
    
    /**
     * Check if email exists.
     */
    Mono<Boolean> existsByEmail(String email);
    
    /**
     * Check if API key exists.
     */
    Mono<Boolean> existsByApiKey(String apiKey);
    
    /**
     * Update user balance atomically.
     */
    @Query("UPDATE users SET balance = balance + :amount WHERE id = :userId AND balance + :amount >= 0 RETURNING *")
    Mono<UserEntity> updateBalance(UUID userId, BigDecimal amount);
    
    /**
     * Record login timestamp.
     */
    @Query("UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE id = :userId RETURNING *")
    Mono<UserEntity> recordLogin(UUID userId);
    
    /**
     * Generate and set API key.
     */
    @Query("UPDATE users SET api_key = :apiKey WHERE id = :userId AND api_key IS NULL RETURNING *")
    Mono<UserEntity> setApiKey(UUID userId, String apiKey);
    
    /**
     * Revoke API key.
     */
    @Query("UPDATE users SET api_key = NULL WHERE id = :userId RETURNING *")
    Mono<UserEntity> revokeApiKey(UUID userId);
    
    /**
     * Update webhook URL.
     */
    @Query("UPDATE users SET webhook_url = :webhookUrl WHERE id = :userId RETURNING *")
    Mono<UserEntity> updateWebhookUrl(UUID userId, String webhookUrl);
    
    /**
     * Suspend user account.
     */
    @Query("UPDATE users SET status = 'SUSPENDED' WHERE id = :userId RETURNING *")
    Mono<UserEntity> suspendUser(UUID userId);
    
    /**
     * Verify email.
     */
    @Query("UPDATE users SET email_verified = true, status = 'ACTIVE' WHERE id = :userId RETURNING *")
    Mono<UserEntity> verifyEmail(UUID userId);
    
    /**
     * Get users with low balance (for notification).
     */
    @Query("SELECT * FROM users WHERE status = 'ACTIVE' AND balance < :threshold")
    Flux<UserEntity> findUsersWithLowBalance(BigDecimal threshold);
    
    /**
     * Get top users by balance.
     */
    @Query("SELECT * FROM users WHERE status = 'ACTIVE' ORDER BY balance DESC LIMIT :limit")
    Flux<UserEntity> findTopUsersByBalance(int limit);
}
