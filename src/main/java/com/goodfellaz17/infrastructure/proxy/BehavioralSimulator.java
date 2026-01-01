package com.goodfellaz17.infrastructure.proxy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Behavioral Simulator for Detection Evasion.
 * 
 * Implements human-like behavior patterns to avoid Spotify detection:
 * - Random play durations (normal distribution)
 * - Human-like skip patterns
 * - Session pauses between operations
 * - User-agent rotation
 * - Header randomization
 * - Cookie aging
 * - ASN diversity enforcement
 */
@Component
public class BehavioralSimulator {
    
    private static final Logger log = LoggerFactory.getLogger(BehavioralSimulator.class);
    
    // User-Agent pool (realistic browser combos)
    private static final List<String> USER_AGENTS = List.of(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:121.0) Gecko/20100101 Firefox/121.0",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (iPad; CPU OS 17_1_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.43 Mobile Safari/537.36"
    );
    
    // Accept-Language variations
    private static final List<String> ACCEPT_LANGUAGES = List.of(
        "en-US,en;q=0.9",
        "en-GB,en;q=0.9",
        "en-US,en;q=0.9,de;q=0.8",
        "en-US,en;q=0.9,es;q=0.8",
        "en-US,en;q=0.9,fr;q=0.8",
        "de-DE,de;q=0.9,en;q=0.8",
        "es-ES,es;q=0.9,en;q=0.8",
        "fr-FR,fr;q=0.9,en;q=0.8",
        "pt-BR,pt;q=0.9,en;q=0.8",
        "en,*;q=0.5"
    );
    
    // Session management
    private final Cache<String, SessionState> sessions;
    
    // ASN tracking for diversity
    private final Map<String, Set<String>> asnUsage = new ConcurrentHashMap<>();
    private static final int MAX_IPS_PER_ASN = 2;
    
    public BehavioralSimulator() {
        this.sessions = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofHours(2))
            .build();
    }
    
    /**
     * Generate random play duration (normal distribution 3-8 min).
     * 
     * @return Duration in seconds
     */
    public int generatePlayDuration() {
        // Normal distribution: mean=330s (5.5min), stddev=90s
        double gaussian = ThreadLocalRandom.current().nextGaussian();
        int duration = (int) (330 + gaussian * 90);
        
        // Clamp to 180-480 seconds (3-8 min)
        return Math.max(180, Math.min(480, duration));
    }
    
    /**
     * Decide if current play should be skipped (15-30% rate).
     */
    public boolean shouldSkip() {
        return ThreadLocalRandom.current().nextDouble() < 0.22; // ~22% skip rate
    }
    
    /**
     * Generate skip timing (when in the track to skip).
     * 
     * @param trackDurationSeconds Total track length
     * @return Seconds into track to skip at (0 = don't skip)
     */
    public int generateSkipTiming(int trackDurationSeconds) {
        if (!shouldSkip()) return 0;
        
        // Distribution: most skips happen early or at natural points
        double rand = ThreadLocalRandom.current().nextDouble();
        
        if (rand < 0.4) {
            // 40%: Skip in first 30 seconds
            return ThreadLocalRandom.current().nextInt(10, 30);
        } else if (rand < 0.7) {
            // 30%: Skip in first minute
            return ThreadLocalRandom.current().nextInt(30, 60);
        } else if (rand < 0.9) {
            // 20%: Skip around middle
            return trackDurationSeconds / 2 + ThreadLocalRandom.current().nextInt(-30, 30);
        } else {
            // 10%: Random point
            return ThreadLocalRandom.current().nextInt(30, trackDurationSeconds - 10);
        }
    }
    
    /**
     * Generate session pause duration between operations.
     * 
     * @return Pause duration in milliseconds
     */
    public long generateSessionPause() {
        // Vary between 5-15 minutes with some shorter bursts
        double rand = ThreadLocalRandom.current().nextDouble();
        
        if (rand < 0.1) {
            // 10%: Very short (back-to-back listens)
            return ThreadLocalRandom.current().nextLong(1000, 10_000);
        } else if (rand < 0.3) {
            // 20%: Short pause (1-3 min)
            return ThreadLocalRandom.current().nextLong(60_000, 180_000);
        } else if (rand < 0.7) {
            // 40%: Medium pause (5-10 min)
            return ThreadLocalRandom.current().nextLong(300_000, 600_000);
        } else {
            // 30%: Long pause (10-20 min)
            return ThreadLocalRandom.current().nextLong(600_000, 1_200_000);
        }
    }
    
    /**
     * Generate inter-request delay (milliseconds).
     * Small delays between API calls to appear human.
     */
    public long generateRequestDelay() {
        // 100ms - 2000ms with normal distribution
        double gaussian = Math.abs(ThreadLocalRandom.current().nextGaussian());
        return (long) (500 + gaussian * 500);
    }
    
    /**
     * Get randomized HTTP headers for a request.
     */
    public Map<String, String> generateHeaders(String sessionId) {
        SessionState session = getOrCreateSession(sessionId);
        
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", session.userAgent);
        headers.put("Accept-Language", session.acceptLanguage);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        headers.put("Accept-Encoding", "gzip, deflate, br");
        headers.put("Connection", "keep-alive");
        headers.put("Upgrade-Insecure-Requests", "1");
        headers.put("Sec-Fetch-Dest", "document");
        headers.put("Sec-Fetch-Mode", "navigate");
        headers.put("Sec-Fetch-Site", "none");
        headers.put("Sec-Fetch-User", "?1");
        headers.put("Cache-Control", "max-age=0");
        
        // Add referer sometimes
        if (ThreadLocalRandom.current().nextBoolean()) {
            headers.put("Referer", "https://www.google.com/");
        }
        
        return headers;
    }
    
    /**
     * Get or rotate user agent for session.
     */
    public String getUserAgent(String sessionId) {
        return getOrCreateSession(sessionId).userAgent;
    }
    
    /**
     * Check if IP can be used considering ASN diversity.
     * 
     * @param ip IP address
     * @param asn ASN identifier
     * @param accountId Account using this IP
     * @return true if IP can be used without exceeding ASN limits
     */
    public boolean checkAsnDiversity(String ip, String asn, String accountId) {
        if (asn == null || asn.isEmpty()) return true;
        
        Set<String> ipsInAsn = asnUsage.computeIfAbsent(asn, k -> ConcurrentHashMap.newKeySet());
        
        if (ipsInAsn.contains(ip)) {
            return true; // Already using this IP
        }
        
        if (ipsInAsn.size() >= MAX_IPS_PER_ASN) {
            log.debug("ASN {} at capacity ({} IPs), rejecting new IP {}", asn, MAX_IPS_PER_ASN, ip);
            return false;
        }
        
        ipsInAsn.add(ip);
        return true;
    }
    
    /**
     * Release IP from ASN tracking.
     */
    public void releaseIp(String ip, String asn) {
        if (asn != null) {
            Set<String> ipsInAsn = asnUsage.get(asn);
            if (ipsInAsn != null) {
                ipsInAsn.remove(ip);
            }
        }
    }
    
    /**
     * Generate behavior profile for a streaming session.
     */
    public StreamingBehavior generateStreamingBehavior(int trackCount) {
        List<TrackBehavior> tracks = new ArrayList<>();
        
        for (int i = 0; i < trackCount; i++) {
            int duration = generatePlayDuration();
            int skipAt = generateSkipTiming(duration);
            long pauseAfter = generateSessionPause();
            boolean fullListen = skipAt == 0;
            
            tracks.add(new TrackBehavior(
                i + 1,
                duration,
                fullListen ? duration : skipAt,
                fullListen,
                pauseAfter
            ));
        }
        
        return new StreamingBehavior(
            tracks,
            tracks.stream().mapToInt(t -> t.actualDuration).sum(),
            tracks.stream().filter(t -> t.fullListen).count(),
            tracks.size()
        );
    }
    
    /**
     * Validate and age session cookies.
     * Returns updated cookies or null if should rotate.
     */
    public Map<String, String> ageCookies(String sessionId, Map<String, String> currentCookies) {
        SessionState session = getOrCreateSession(sessionId);
        
        // Check if cookies are too old
        Duration cookieAge = Duration.between(session.cookiesCreated, Instant.now());
        
        if (cookieAge.toMinutes() > 30 && ThreadLocalRandom.current().nextDouble() < 0.3) {
            // 30% chance to rotate after 30 minutes
            session.rotateCookies();
            return null; // Signal to get new cookies
        }
        
        return currentCookies;
    }
    
    /**
     * Get session state (for inspection/debugging).
     */
    public Optional<SessionState> getSession(String sessionId) {
        return Optional.ofNullable(sessions.getIfPresent(sessionId));
    }
    
    /**
     * Force rotate session (new UA, headers, etc).
     */
    public void rotateSession(String sessionId) {
        sessions.invalidate(sessionId);
        log.debug("Rotated session: {}", sessionId);
    }
    
    // === Private Helpers ===
    
    private SessionState getOrCreateSession(String sessionId) {
        return sessions.get(sessionId, k -> new SessionState());
    }
    
    // === Inner Classes ===
    
    /**
     * Session state for consistent behavior within a session.
     */
    public static class SessionState {
        String userAgent;
        String acceptLanguage;
        Instant created;
        Instant cookiesCreated;
        int requestCount;
        
        SessionState() {
            this.userAgent = USER_AGENTS.get(ThreadLocalRandom.current().nextInt(USER_AGENTS.size()));
            this.acceptLanguage = ACCEPT_LANGUAGES.get(ThreadLocalRandom.current().nextInt(ACCEPT_LANGUAGES.size()));
            this.created = Instant.now();
            this.cookiesCreated = Instant.now();
            this.requestCount = 0;
        }
        
        void rotateCookies() {
            this.cookiesCreated = Instant.now();
        }
        
        void incrementRequests() {
            this.requestCount++;
        }
    }
    
    /**
     * Behavior for a single track.
     */
    public record TrackBehavior(
        int trackNumber,
        int generatedDuration,    // How long we planned to play
        int actualDuration,       // How long we actually played (after skip)
        boolean fullListen,       // Did we listen to full track
        long pauseAfterMs         // Pause before next track
    ) {}
    
    /**
     * Streaming session behavior plan.
     */
    public record StreamingBehavior(
        List<TrackBehavior> tracks,
        int totalListenTime,      // Total seconds of actual listening
        long fullListens,         // Count of full listens
        int trackCount            // Total tracks
    ) {
        public double getCompletionRate() {
            return trackCount > 0 ? (double) fullListens / trackCount : 0.0;
        }
    }
}
