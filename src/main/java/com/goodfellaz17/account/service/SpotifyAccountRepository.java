package com.goodfellaz17.account.service;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * R2DBC reactive repository for SpotifyAccount entities.
 * Provides reactive queries for account management.
 */
@Repository
public interface SpotifyAccountRepository extends R2dbcRepository<SpotifyAccount, Long> {

    /**
     * Find all accounts by status
     */
    Flux<SpotifyAccount> findByStatus(String status);

    /**
     * Count accounts by status
     */
    Mono<Long> countByStatus(String status);

    /**
     * Find account by email
     */
    Mono<SpotifyAccount> findByEmail(String email);

    /**
     * Find account by Spotify user ID
     */
    Mono<SpotifyAccount> findBySpotifyUserId(String spotifyUserId);

    /**
     * Find all active accounts (ACTIVE status)
     */
    @Query("SELECT * FROM pipeline_spotify_accounts WHERE status = 'ACTIVE'")
    Flux<SpotifyAccount> findAllActive();

    /**
     * Find all pending email verification accounts
     */
    @Query("SELECT * FROM pipeline_spotify_accounts WHERE status = 'PENDING_EMAIL_VERIFICATION'")
    Flux<SpotifyAccount> findAllPendingEmailVerification();

    /**
     * Find all degraded accounts
     */
    @Query("SELECT * FROM pipeline_spotify_accounts WHERE status = 'DEGRADED'")
    Flux<SpotifyAccount> findAllDegraded();

    /**
     * Find all banned accounts
     */
    @Query("SELECT * FROM pipeline_spotify_accounts WHERE status = 'BANNED'")
    Flux<SpotifyAccount> findAllBanned();

    /**
     * Count total active accounts
     */
    @Query("SELECT COUNT(*) FROM pipeline_spotify_accounts WHERE status = 'ACTIVE'")
    Mono<Long> countActive();

    /**
     * Find accounts created in the last N hours
     */
    @Query("SELECT * FROM pipeline_spotify_accounts WHERE created_at > NOW() - INTERVAL '1 hour' * :hours")
    Flux<SpotifyAccount> findRecentlyCreated(int hours);

    /**
     * Update account status
     */
    @Query("UPDATE pipeline_spotify_accounts SET status = :status, updated_date = NOW() WHERE id = :id")
    Mono<Void> updateStatus(Long id, String status);

    /**
     * Update last played timestamp
     */
    @Query("UPDATE pipeline_spotify_accounts SET last_played_at = NOW() WHERE id = :id")
    Mono<Void> updateLastPlayed(Long id);
}
