package com.goodfellaz17.domain.model;

import java.util.List;

/**
 * Represents a realistic Spotify streaming session with behavior parameters.
 * KTH/TU Research: Poisson-distributed listen durations with skip modeling.
 */
public record StreamSession(
    String sessionId,
    String trackId,
    int durationSeconds,          // Poisson(65s, σ=20s), clamped [35-90s]
    double skipProbability,       // <5s skip: 25%, section skip: 15%
    String userAgent,             // 65% iOS Safari, 35% other
    List<Integer> pausePointsMs,  // Natural pause moments
    String deviceType,            // MOBILE, DESKTOP, TABLET
    String peakHour,              // EU peak: 18-22
    boolean fullListen,           // duration >= 30s (counts as play)
    double engagementScore        // 0.0-1.0 based on behavior realism
) {

    /**
     * Creates a session indicating successful full listen (>30s).
     */
    public static StreamSession fullListen(String sessionId, String trackId, int duration,
                                           String userAgent, List<Integer> pauses) {
        return new StreamSession(
            sessionId, trackId, duration, 0.0, userAgent, pauses,
            userAgent.contains("iPhone") ? "MOBILE" : "DESKTOP",
            "20:00", true, calculateEngagement(duration, pauses.size())
        );
    }

    /**
     * Creates a session indicating early skip (<5s or section skip).
     */
    public static StreamSession skipped(String sessionId, String trackId, int duration,
                                        double skipProb, String userAgent) {
        return new StreamSession(
            sessionId, trackId, duration, skipProb, userAgent, List.of(),
            userAgent.contains("iPhone") ? "MOBILE" : "DESKTOP",
            "20:00", false, 0.1
        );
    }

    private static double calculateEngagement(int duration, int pauseCount) {
        // Higher duration + some pauses = more realistic = higher engagement
        double durationScore = Math.min(1.0, duration / 90.0);
        double pauseScore = pauseCount > 0 && pauseCount < 5 ? 0.2 : 0.0;
        return Math.min(1.0, durationScore * 0.8 + pauseScore);
    }

    /**
     * Returns true if this session counts as a valid Spotify play (>30s).
     */
    public boolean countsAsPlay() {
        return durationSeconds >= 30 && fullListen;
    }
}
