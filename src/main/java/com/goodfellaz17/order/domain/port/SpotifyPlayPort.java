package com.goodfellaz17.order.domain.port;

import reactor.core.publisher.Mono;

/**
 * Domain port for Spotify play execution.
 * Decouples orchestrator from HTTP/proxy details.
 *
 * Invariants enforced:
 * - INV-4: Task must have startedAt + assignedProxyNode before execution
 * - INV-5: Result must include completedAt timestamp
 */
public interface SpotifyPlayPort {

    /**
     * Execute a single play against Spotify (via proxy or local automation).
     *
     * @param command Contains trackId, accountId, proxyNode, retry info
     * @return Mono<PlayResult> with success/failure + metadata
     */
    Mono<PlayResult> startPlay(SpotifyPlayCommand command);
}
