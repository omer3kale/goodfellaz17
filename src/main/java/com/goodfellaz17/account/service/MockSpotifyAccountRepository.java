package com.goodfellaz17.account.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Mock SpotifyAccountRepository for thesis demo.
 * TODO: Implement actual Spotify account persistence.
 */
@Service
public class MockSpotifyAccountRepository {

    public Mono<Void> save(Object account) {
        return Mono.empty();
    }
}
