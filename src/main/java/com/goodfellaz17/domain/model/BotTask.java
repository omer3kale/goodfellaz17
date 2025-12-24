package com.goodfellaz17.domain.model;

import java.util.UUID;

/**
 * Domain Value Object - Bot execution task.
 * 
 * Represents a single Chrome worker assignment:
 * - Which proxy to use (geo-targeted)
 * - Which account to login with
 * - Which track to play
 * - Session parameters (duration, skip behavior)
 */
public record BotTask(
        UUID id,
        UUID orderId,
        String trackUrl,
        Proxy proxy,
        PremiumAccount account,
        SessionProfile sessionProfile
) {
    public static BotTask create(UUID orderId, String trackUrl, Proxy proxy, 
                                  PremiumAccount account, SessionProfile profile) {
        return new BotTask(UUID.randomUUID(), orderId, trackUrl, proxy, account, profile);
    }
}
