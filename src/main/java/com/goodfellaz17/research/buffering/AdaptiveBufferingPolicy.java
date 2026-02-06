package com.goodfellaz17.research.buffering;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Adaptive Buffering Policy based on Schwind 2019 observations.
 * "QoE Analysis of Spotify Audio Streaming and App Browsing"
 *
 * Implements Spotify-like buffering strategy:
 * 1. Full-song prefetch for current track
 * 2. Pre-buffering of next 1-2 songs in playlist
 * 3. Adaptive buffer targets based on bandwidth (150-500 kbps)
 * 4. Quality switching based on buffer health
 *
 * Key observations from Schwind 2019:
 * - Spotify uses aggressive prefetching (full track buffered ahead)
 * - Initial buffering targets ~3-5 seconds of playback
 * - Rebuffering triggers at ~1-2 seconds remaining
 * - Quality degrades gracefully under bandwidth constraints
 */
@Component
public class AdaptiveBufferingPolicy {
    private static final Logger log = LoggerFactory.getLogger(AdaptiveBufferingPolicy.class);

    // === Buffer Configuration ===

    // Target buffer levels (in seconds of playback)
    private static final double INITIAL_BUFFER_TARGET_SECONDS = 5.0;
    private static final double MIN_BUFFER_THRESHOLD_SECONDS = 2.0;
    private static final double MAX_BUFFER_CAPACITY_SECONDS = 30.0;

    // Prefetch configuration
    private static final int PREFETCH_TRACKS_AHEAD = 2;
    private static final double PREFETCH_TRIGGER_PERCENT = 0.7;  // Start prefetch at 70% played

    // Bandwidth tiers (kbps)
    private static final int LOW_BANDWIDTH_KBPS = 150;
    private static final int MEDIUM_BANDWIDTH_KBPS = 300;
    private static final int HIGH_BANDWIDTH_KBPS = 500;

    // Quality levels (Spotify OGG Vorbis bitrates)
    public enum QualityLevel {
        LOW(96),        // 96 kbps - Low quality
        NORMAL(160),    // 160 kbps - Normal quality
        HIGH(320),      // 320 kbps - High quality (Premium)
        VERY_HIGH(320); // 320 kbps + hardware EQ (Premium)

        public final int bitrateKbps;
        QualityLevel(int bitrate) { this.bitrateKbps = bitrate; }
    }

    // === Buffer State ===

    /**
     * Buffer state for a single track.
     */
    public static class TrackBuffer {
        public final String trackId;
        public final int durationMs;
        public QualityLevel quality;
        public long bufferedBytes;
        public long totalBytes;
        public int bufferedMs;
        public boolean fullyBuffered;
        public long lastUpdateTime;

        public TrackBuffer(String trackId, int durationMs, QualityLevel quality) {
            this.trackId = trackId;
            this.durationMs = durationMs;
            this.quality = quality;
            this.bufferedBytes = 0;
            this.totalBytes = estimateTotalBytes(durationMs, quality);
            this.bufferedMs = 0;
            this.fullyBuffered = false;
            this.lastUpdateTime = System.currentTimeMillis();
        }

        private long estimateTotalBytes(int durationMs, QualityLevel quality) {
            // bytes = (bitrate_kbps * 1000 / 8) * (duration_ms / 1000)
            return (long)(quality.bitrateKbps * 125.0 * durationMs / 1000);
        }

        public double getBufferProgress() {
            return totalBytes > 0 ? (double) bufferedBytes / totalBytes : 0;
        }

        public double getBufferedSeconds() {
            return bufferedMs / 1000.0;
        }
    }

    /**
     * Session buffer manager for playlist playback.
     */
    public static class SessionBufferManager {
        private final String sessionId;
        private final Queue<TrackBuffer> prefetchQueue = new ConcurrentLinkedQueue<>();
        private final ConcurrentHashMap<String, TrackBuffer> bufferCache = new ConcurrentHashMap<>();
        private volatile TrackBuffer currentTrack;
        private volatile int currentPlaybackMs;
        private volatile double estimatedBandwidthKbps;
        private volatile QualityLevel targetQuality;

        public SessionBufferManager(String sessionId, double initialBandwidthKbps) {
            this.sessionId = sessionId;
            this.estimatedBandwidthKbps = initialBandwidthKbps;
            this.targetQuality = selectQualityForBandwidth(initialBandwidthKbps);
        }

        public String getSessionId() { return sessionId; }
        public TrackBuffer getCurrentTrack() { return currentTrack; }
        public int getCurrentPlaybackMs() { return currentPlaybackMs; }
        public double getEstimatedBandwidthKbps() { return estimatedBandwidthKbps; }
        public QualityLevel getTargetQuality() { return targetQuality; }
    }

    // === Buffering Logic ===

    /**
     * Selects quality level based on available bandwidth.
     * Implements ABR (Adaptive Bitrate) selection.
     */
    public static QualityLevel selectQualityForBandwidth(double bandwidthKbps) {
        // Leave 20% headroom for network variance
        double effectiveBandwidth = bandwidthKbps * 0.8;

        if (effectiveBandwidth >= QualityLevel.HIGH.bitrateKbps) {
            return QualityLevel.HIGH;
        } else if (effectiveBandwidth >= QualityLevel.NORMAL.bitrateKbps) {
            return QualityLevel.NORMAL;
        } else {
            return QualityLevel.LOW;
        }
    }

    /**
     * Calculates buffer health score [0, 1].
     * 0 = empty/stalling, 1 = healthy buffer
     */
    public double calculateBufferHealth(TrackBuffer buffer, int playbackPositionMs) {
        if (buffer == null) return 0;

        int remainingBufferMs = buffer.bufferedMs - playbackPositionMs;
        double remainingBufferSeconds = remainingBufferMs / 1000.0;

        if (remainingBufferSeconds <= 0) {
            return 0;  // Buffer underrun
        } else if (remainingBufferSeconds >= INITIAL_BUFFER_TARGET_SECONDS) {
            return 1.0;  // Healthy
        } else {
            return remainingBufferSeconds / INITIAL_BUFFER_TARGET_SECONDS;
        }
    }

    /**
     * Determines if rebuffering is needed.
     */
    public boolean needsRebuffering(TrackBuffer buffer, int playbackPositionMs) {
        int remainingBufferMs = buffer.bufferedMs - playbackPositionMs;
        return remainingBufferMs < (MIN_BUFFER_THRESHOLD_SECONDS * 1000);
    }

    /**
     * Determines if prefetch should start for next track.
     */
    public boolean shouldStartPrefetch(TrackBuffer buffer, int playbackPositionMs) {
        if (buffer == null || buffer.durationMs == 0) return false;
        double progress = (double) playbackPositionMs / buffer.durationMs;
        return progress >= PREFETCH_TRIGGER_PERCENT;
    }

    /**
     * Calculates optimal buffer target based on conditions.
     */
    public Duration calculateBufferTarget(
            double bandwidthKbps,
            QualityLevel quality,
            boolean isPlaylistMode) {

        // Base target
        double targetSeconds = INITIAL_BUFFER_TARGET_SECONDS;

        // Increase target for low bandwidth (more headroom needed)
        if (bandwidthKbps < LOW_BANDWIDTH_KBPS) {
            targetSeconds *= 1.5;
        }

        // Playlist mode: can use more aggressive prefetch
        if (isPlaylistMode) {
            targetSeconds *= 1.2;
        }

        // Cap at maximum
        targetSeconds = Math.min(targetSeconds, MAX_BUFFER_CAPACITY_SECONDS);

        return Duration.ofMillis((long)(targetSeconds * 1000));
    }

    /**
     * Simulates buffer fill rate based on bandwidth and quality.
     * Returns bytes that can be buffered in given time window.
     */
    public long calculateBufferFillBytes(
            double bandwidthKbps,
            Duration timeWindow,
            QualityLevel quality) {

        // Available bandwidth for buffering (minus playback consumption)
        double availableKbps = bandwidthKbps - quality.bitrateKbps;

        if (availableKbps <= 0) {
            // Can barely keep up with playback, no extra buffering
            return 0;
        }

        // bytes = kbps * 125 * seconds
        return (long)(availableKbps * 125 * timeWindow.toMillis() / 1000);
    }

    /**
     * Estimates time to buffer enough for playback start.
     */
    public Duration estimateInitialBufferTime(
            double bandwidthKbps,
            QualityLevel quality) {

        // Need INITIAL_BUFFER_TARGET_SECONDS worth of data
        long bytesNeeded = (long)(quality.bitrateKbps * 125 * INITIAL_BUFFER_TARGET_SECONDS);

        // Time = bytes / (bandwidth_bytes_per_second)
        double seconds = bytesNeeded / (bandwidthKbps * 125);

        return Duration.ofMillis((long)(seconds * 1000));
    }

    // === Prefetch Policy ===

    /**
     * Prefetch policy decision for playlist context.
     */
    public record PrefetchDecision(
        boolean shouldPrefetch,
        int tracksToBuffer,
        QualityLevel prefetchQuality,
        String reason
    ) {}

    /**
     * Determines prefetch strategy based on current conditions.
     */
    public PrefetchDecision decidePrefetch(
            SessionBufferManager session,
            int remainingTracksInPlaylist,
            double currentBandwidthKbps) {

        TrackBuffer current = session.getCurrentTrack();
        if (current == null) {
            return new PrefetchDecision(false, 0, null, "No current track");
        }

        // Check if we should start prefetching
        if (!shouldStartPrefetch(current, session.getCurrentPlaybackMs())) {
            return new PrefetchDecision(false, 0, null,
                "Current track not at prefetch threshold");
        }

        // Determine how many tracks to prefetch
        int tracksToBuffer = Math.min(PREFETCH_TRACKS_AHEAD, remainingTracksInPlaylist);

        if (tracksToBuffer == 0) {
            return new PrefetchDecision(false, 0, null, "No more tracks in playlist");
        }

        // Select quality for prefetch (may be lower than current if bandwidth constrained)
        QualityLevel prefetchQuality = selectQualityForBandwidth(
            currentBandwidthKbps - session.getTargetQuality().bitrateKbps);

        return new PrefetchDecision(true, tracksToBuffer, prefetchQuality,
            String.format("Prefetching %d tracks at %s quality",
                tracksToBuffer, prefetchQuality));
    }

    // === Bandwidth Estimation ===

    /**
     * Simple EWMA bandwidth estimator.
     */
    public static class BandwidthEstimator {
        private static final double ALPHA = 0.3;  // Smoothing factor
        private double estimate;
        private long lastSampleTime;

        public BandwidthEstimator(double initialEstimate) {
            this.estimate = initialEstimate;
            this.lastSampleTime = System.currentTimeMillis();
        }

        public synchronized void addSample(long bytes, long durationMs) {
            if (durationMs <= 0) return;

            double sampleKbps = (bytes * 8.0) / durationMs;
            estimate = ALPHA * sampleKbps + (1 - ALPHA) * estimate;
            lastSampleTime = System.currentTimeMillis();
        }

        public synchronized double getEstimate() {
            // Decay estimate if no recent samples (assume degraded network)
            long timeSinceLastSample = System.currentTimeMillis() - lastSampleTime;
            if (timeSinceLastSample > 10000) {  // 10 seconds
                double decayFactor = Math.pow(0.9, timeSinceLastSample / 10000.0);
                return estimate * decayFactor;
            }
            return estimate;
        }
    }
}
