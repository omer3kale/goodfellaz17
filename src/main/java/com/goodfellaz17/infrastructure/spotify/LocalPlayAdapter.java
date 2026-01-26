package com.goodfellaz17.infrastructure.spotify;

import com.goodfellaz17.order.domain.port.SpotifyPlayCommand;
import com.goodfellaz17.order.domain.port.SpotifyPlayPort;
import com.goodfellaz17.order.domain.port.PlayResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Random;

/**
 * LocalPlayAdapter: Dummy Spotify execution adapter for local development.
 *
 * For now, logs the command locally. Later, swap this bean for RealProxySpotifyAdapter
 * (no domain changes needed).
 *
 * Invariants enforced:
 * - INV-5: Always returns PlayResult with completedAt timestamp
 */
@Service
@ConditionalOnProperty(name = "app.spotify.adapter", havingValue = "local", matchIfMissing = true)
public class LocalPlayAdapter implements SpotifyPlayPort {

    private static final Logger log = LoggerFactory.getLogger(LocalPlayAdapter.class);
    private final Random random = new Random();

    @Override
    public Mono<PlayResult> startPlay(SpotifyPlayCommand command) {
        Instant startedAt = Instant.now();

        // Simulate execution (90% success rate for testing)
        boolean simulatedSuccess = random.nextDouble() < 0.9;
        long durationMs = 100 + random.nextInt(400);  // 100-500ms

        // Log the command (would call real proxy in production)
        log.info("LocalPlayAdapter.startPlay | taskId={} | track={} | account={} | retries={}/{}",
            command.taskId(), command.trackId(), command.accountId(),
            command.retryCount(), command.maxRetries());

        // Return result with all invariants satisfied
        PlayResult result = new PlayResult(
            simulatedSuccess,
            simulatedSuccess ? null : "simulated_network_error",
            "local-executor-1",
            command.retryCount() + 1,
            startedAt,
            Instant.now(),  // INV-5: completedAt must be set
            durationMs
        );

        return Mono.just(result);
    }
}
