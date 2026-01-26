package com.goodfellaz17.order.domain.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain command: what we need to tell Spotify/proxy to do a play.
 * Zero framework dependencies. Pure data.
 */
public record SpotifyPlayCommand(
    // Task identity
    UUID taskId,
    UUID orderId,

    // What to play
    String trackId,         // Spotify track ID (not full URL)
    String accountId,       // Spotify account identifier

    // Execution context
    String assignedProxyNode,  // Which proxy node should execute this
    int retryCount,         // How many times we've tried (for metrics)
    int maxRetries,         // When to give up

    // Metadata
    Instant createdAt,
    long requestTimeoutMs   // How long to wait before timeout
) {

    public SpotifyPlayCommand {
        if (taskId == null || orderId == null || trackId == null || accountId == null) {
            throw new IllegalArgumentException("taskId, orderId, trackId, accountId required");
        }
        if (requestTimeoutMs <= 0 || requestTimeoutMs > 30000) {
            throw new IllegalArgumentException("requestTimeoutMs must be > 0 and <= 30s");
        }
    }
}
