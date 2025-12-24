package com.spotifybot.domain.model;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Domain Value Object - Human-like session profile.
 * 
 * Randomized parameters to avoid bot detection:
 * - Play duration (35s-120s for royalty eligibility)
 * - Skip probability (15% like real users)
 * - Seek behavior (occasional forward/back)
 */
public record SessionProfile(
        Duration playDuration,
        boolean willSkip,
        int seekOffsetSeconds,
        boolean addToLibrary,
        boolean followArtist
) {
    private static final int MIN_ROYALTY_DURATION = 35;
    private static final int MAX_DURATION = 180;
    private static final double SKIP_PROBABILITY = 0.15;
    
    /**
     * Generate human-like session profile.
     */
    public static SessionProfile randomHuman() {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        
        // 35-180s duration (royalty eligible + variance)
        int durationSec = rand.nextInt(MIN_ROYALTY_DURATION, MAX_DURATION);
        
        // 15% skip rate (human behavior)
        boolean skip = rand.nextDouble() < SKIP_PROBABILITY;
        
        // Occasional seek (10% of sessions)
        int seekOffset = rand.nextDouble() < 0.10 ? rand.nextInt(-10, 30) : 0;
        
        // Rare engagement actions (2-5%)
        boolean addLib = rand.nextDouble() < 0.02;
        boolean follow = rand.nextDouble() < 0.05;
        
        return new SessionProfile(
                Duration.ofSeconds(durationSec),
                skip,
                seekOffset,
                addLib,
                follow
        );
    }

    /**
     * Create default session profile for task decomposition.
     */
    public static SessionProfile createDefault() {
        return new SessionProfile(
                Duration.ofSeconds(45),  // Royalty eligible
                false,
                0,
                false,
                false
        );
    }
    
    /**
     * Check if session meets Spotify royalty threshold (35s+).
     */
    public boolean isRoyaltyEligible() {
        return playDuration.getSeconds() >= MIN_ROYALTY_DURATION;
    }
}
