package com.goodfellaz17.application.service;

import java.security.SecureRandom;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.goodfellaz17.domain.model.StreamSession;

/**
 * SpotifyBehaviorEngine - Generates realistic streaming sessions.
 *
 * KTH/TU Research Parameters:
 * - Duration: Poisson(μ=65s, σ=20s) clamped to [35-90s]
 * - Skip probability: <5s early skip 25%, section skip 15%
 * - User agents: 65% iOS Safari (mobile), 35% other
 * - Peak hours: 18-22 EU timezone (higher activity)
 *
 * @author GoodFellaz17 Research Team
 */
@Service
public class SpotifyBehaviorEngine {
    private static final Logger log = LoggerFactory.getLogger(SpotifyBehaviorEngine.class);

    private final SecureRandom random = new SecureRandom();

    // Poisson distribution parameters
    @Value("${spotify.behavior.duration.mean:65}")
    private double durationMean;

    @Value("${spotify.behavior.duration.stddev:20}")
    private double durationStdDev;

    @Value("${spotify.behavior.duration.min:35}")
    private int durationMin;

    @Value("${spotify.behavior.duration.max:90}")
    private int durationMax;

    // Skip probabilities
    @Value("${spotify.behavior.skip.early:0.25}")
    private double earlySkipProbability;

    @Value("${spotify.behavior.skip.section:0.15}")
    private double sectionSkipProbability;

    // Device distribution
    @Value("${spotify.behavior.mobile.ratio:0.65}")
    private double mobileRatio;

    // EU peak hours
    @Value("${spotify.behavior.peak.start:18}")
    private int peakHourStart;

    @Value("${spotify.behavior.peak.end:22}")
    private int peakHourEnd;

    // User agent pool
    private static final String[] IOS_USER_AGENTS = {
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
    };

    private static final String[] DESKTOP_USER_AGENTS = {
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0"
    };

    private static final String[] ANDROID_USER_AGENTS = {
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    };

    /**
     * Generates a realistic streaming session with Poisson-distributed duration.
     *
     * @param trackId Spotify track URI
     * @return StreamSession with realistic behavior parameters
     */
    public StreamSession generateSession(String trackId) {
        String sessionId = UUID.randomUUID().toString();

        // Determine if this is an early skip (25% chance)
        if (random.nextDouble() < earlySkipProbability) {
            int skipDuration = random.nextInt(5) + 1; // 1-5 seconds
            String ua = selectUserAgent();
            log.debug("ZORG✓ Session {} - Early skip after {}s", sessionId, skipDuration);
            return StreamSession.skipped(sessionId, trackId, skipDuration, earlySkipProbability, ua);
        }

        // Determine if this is a section skip (15% chance)
        if (random.nextDouble() < sectionSkipProbability) {
            int skipDuration = 15 + random.nextInt(20); // 15-35 seconds (partial listen)
            String ua = selectUserAgent();
            log.debug("ZORG✓ Session {} - Section skip after {}s", sessionId, skipDuration);
            return StreamSession.skipped(sessionId, trackId, skipDuration, sectionSkipProbability, ua);
        }

        // Full listen with Poisson-distributed duration
        int duration = generatePoissonDuration();
        String userAgent = selectUserAgent();
        List<Integer> pausePoints = generatePausePoints(duration);

        log.debug("ZORG✓ Session {} - Full listen {}s, {} pauses, UA={}",
                  sessionId, duration, pausePoints.size(),
                  userAgent.contains("iPhone") ? "iOS" : "Desktop");

        return StreamSession.fullListen(sessionId, trackId, duration, userAgent, pausePoints);
    }

    /**
     * Generates duration using Poisson approximation (normal distribution for large λ).
     * Clamped to [35, 90] seconds as per research parameters.
     */
    private int generatePoissonDuration() {
        // For Poisson with large mean, use normal approximation
        double gaussian = random.nextGaussian();
        double duration = durationMean + (gaussian * durationStdDev);

        // Clamp to valid range
        int clamped = (int) Math.max(durationMin, Math.min(durationMax, Math.round(duration)));
        return clamped;
    }

    /**
     * Selects user agent based on device distribution.
     * 65% mobile (iOS Safari), 35% desktop/other
     */
    private String selectUserAgent() {
        double roll = random.nextDouble();

        if (roll < mobileRatio * 0.85) {
            // iOS Safari (55% of total)
            return IOS_USER_AGENTS[random.nextInt(IOS_USER_AGENTS.length)];
        } else if (roll < mobileRatio) {
            // Android (10% of total)
            return ANDROID_USER_AGENTS[random.nextInt(ANDROID_USER_AGENTS.length)];
        } else {
            // Desktop (35% of total)
            return DESKTOP_USER_AGENTS[random.nextInt(DESKTOP_USER_AGENTS.length)];
        }
    }

    /**
     * Generates natural pause points during playback.
     * Simulates user behavior: checking phone, changing volume, etc.
     */
    private List<Integer> generatePausePoints(int durationSeconds) {
        List<Integer> pauses = new ArrayList<>();
        int durationMs = durationSeconds * 1000;

        // 40% chance of at least one pause
        if (random.nextDouble() < 0.4) {
            int pauseCount = 1 + random.nextInt(3); // 1-3 pauses
            for (int i = 0; i < pauseCount; i++) {
                // Pause at random point (not in first 5s or last 5s)
                int pausePoint = 5000 + random.nextInt(Math.max(1, durationMs - 10000));
                pauses.add(pausePoint);
            }
            pauses.sort(Integer::compareTo);
        }

        return pauses;
    }

    /**
     * Checks if current time is within EU peak hours (18-22).
     */
    public boolean isPeakHour() {
        LocalTime now = LocalTime.now(ZoneId.of("Europe/Berlin"));
        int hour = now.getHour();
        return hour >= peakHourStart && hour < peakHourEnd;
    }

    /**
     * Returns optimal concurrency based on time of day.
     * Peak hours: higher concurrency for more natural traffic pattern.
     */
    public int getOptimalConcurrency() {
        return isPeakHour() ? 75 : 50;
    }

    /**
     * Generates delay between sessions to avoid detection.
     * Returns milliseconds to wait before next session.
     */
    public long getInterSessionDelayMs() {
        // Base delay 500-2000ms, with occasional longer pauses
        long baseDelay = 500 + random.nextInt(1500);

        // 10% chance of longer delay (simulating user browsing)
        if (random.nextDouble() < 0.10) {
            baseDelay += 3000 + random.nextInt(5000);
        }

        return baseDelay;
    }

    /**
     * Calculates expected success rate based on current parameters.
     */
    public double getExpectedSuccessRate() {
        // Success = not early skip AND not section skip
        return (1.0 - earlySkipProbability) * (1.0 - sectionSkipProbability);
    }
}
