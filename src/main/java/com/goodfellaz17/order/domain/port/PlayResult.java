package com.goodfellaz17.order.domain.port;

import java.time.Instant;

/**
 * Domain result: outcome of a play attempt.
 * Contains only observable facts, not HTTP details.
 */
public record PlayResult(
    // Outcome
    boolean success,
    String failureReason,    // null if success; "timeout", "account_not_found", "track_blocked", etc.

    // Metadata
    String executingNodeId,   // Which proxy/node actually executed
    int attempts,            // How many retry attempts were made

    // Timing (for SLO tracking)
    Instant startedAt,
    Instant completedAt,
    long durationMs
) {

    public PlayResult {
        if (!success && failureReason == null) {
            throw new IllegalArgumentException("failureReason required when success=false");
        }
        if (success && failureReason != null) {
            throw new IllegalArgumentException("failureReason must be null when success=true");
        }
    }
}
