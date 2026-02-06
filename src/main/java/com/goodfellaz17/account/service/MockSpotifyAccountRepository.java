package com.goodfellaz17.account.service;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

/**
 * Mock SpotifyAccountRepository for thesis demo.
 *
 * TODO Phase 2: Implement actual Spotify account persistence.
 *
 * Implementation path when needed:
 * 1. Create SpotifyAccountEntity with R2DBC mapping
 *    - Fields: id (UUID), email, password (encrypted), spotifyUserId, status, createdAt, lastPlayedAt
 *    - Annotations: @Table("spotify_accounts"), @Id, @Column
 * 2. Extend ReactiveCrudRepository<SpotifyAccountEntity, UUID>
 * 3. Add queries: findByEmail, findByStatus, findAvailableForTasks
 * 4. Schema migration with Flyway for spotify_accounts table
 * 5. Replace this mock in Spring context
 *
 * Current behavior: save() returns empty Mono (no persistence)
 *
 * @see com.goodfellaz17.domain.model.SpotifyAccount for domain model
 */
@Service
public class MockSpotifyAccountRepository {

    /**
     * Mock save - no-op for thesis demo.
     * In Phase 2, this will persist to spotify_accounts table via R2DBC.
     *
     * @param account the account to save (currently ignored)
     * @return empty Mono (no persistence)
     */
    public Mono<Void> save(Object account) {
        return Mono.empty();
    }
}
