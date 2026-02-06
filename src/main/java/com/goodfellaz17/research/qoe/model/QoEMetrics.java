package com.goodfellaz17.research.qoe.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

/**
 * QoE Metrics Data Structure based on Schwind et al. 2019.
 * "QoE Analysis of Spotify Audio Streaming and App Browsing"
 *
 * Tracks: Initial delay, stalling events, navigation time, and MOS mapping.
 */
public record QoEMetrics(
    String sessionId,
    String trackId,
    DeviceType deviceType,

    // === Initial Delay Metrics ===
    Duration initialDelay,         // Time from play request to first audio
    Duration timeToFirstByte,      // Network TTFB
    Duration bufferingTime,        // Pre-playback buffer fill time

    // === Stalling Metrics ===
    int stallingCount,             // Number of rebuffering events
    Duration totalStallingTime,    // Cumulative stalling duration
    List<StallingEvent> stallingEvents,

    // === Navigation Metrics ===
    Duration navigationTime,       // Time to load track metadata/UI
    Duration seekLatency,          // Average seek response time
    int seekCount,

    // === Playback Metrics ===
    Duration playbackDuration,     // Actual listened duration
    Duration trackDuration,        // Total track length
    boolean completed,             // Listened >30s (Spotify play threshold)
    boolean skipped,               // User-initiated skip

    // === Network Metrics ===
    long bytesDownloaded,
    double averageBitrate,         // kbps
    double bandwidthEstimate,      // Measured available bandwidth

    // === Timestamps ===
    Instant sessionStart,
    Instant playbackStart,
    Instant playbackEnd,

    // === Computed QoE ===
    double moScore                 // Mean Opinion Score [1.0-5.0]
) {

    public enum DeviceType {
        MOBILE_IOS, MOBILE_ANDROID, DESKTOP, TABLET
    }

    /**
     * Stalling event record per Schwind 2019 methodology.
     */
    public record StallingEvent(
        Instant timestamp,
        Duration duration,
        long playbackPositionMs,
        String cause  // BUFFER_UNDERRUN, NETWORK_CHANGE, SEEK
    ) {}

    /**
     * Builder for constructing QoE metrics during session.
     */
    public static class QoEMetricsBuilder {
        private String sessionId;
        private String trackId;
        private DeviceType deviceType = DeviceType.DESKTOP;
        private Duration initialDelay = Duration.ZERO;
        private Duration timeToFirstByte = Duration.ZERO;
        private Duration bufferingTime = Duration.ZERO;
        private int stallingCount = 0;
        private Duration totalStallingTime = Duration.ZERO;
        private List<StallingEvent> stallingEvents = new ArrayList<>();
        private Duration navigationTime = Duration.ZERO;
        private Duration seekLatency = Duration.ZERO;
        private int seekCount = 0;
        private Duration playbackDuration = Duration.ZERO;
        private Duration trackDuration = Duration.ZERO;
        private boolean completed = false;
        private boolean skipped = false;
        private long bytesDownloaded = 0;
        private double averageBitrate = 0;
        private double bandwidthEstimate = 0;
        private Instant sessionStart;
        private Instant playbackStart;
        private Instant playbackEnd;
        private double moScore = 0;

        public QoEMetricsBuilder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public QoEMetricsBuilder trackId(String trackId) { this.trackId = trackId; return this; }
        public QoEMetricsBuilder deviceType(DeviceType deviceType) { this.deviceType = deviceType; return this; }
        public QoEMetricsBuilder initialDelay(Duration initialDelay) { this.initialDelay = initialDelay; return this; }
        public QoEMetricsBuilder timeToFirstByte(Duration ttfb) { this.timeToFirstByte = ttfb; return this; }
        public QoEMetricsBuilder bufferingTime(Duration bufferingTime) { this.bufferingTime = bufferingTime; return this; }
        public QoEMetricsBuilder stallingCount(int count) { this.stallingCount = count; return this; }
        public QoEMetricsBuilder totalStallingTime(Duration total) { this.totalStallingTime = total; return this; }
        public QoEMetricsBuilder stallingEvents(List<StallingEvent> events) { this.stallingEvents = events; return this; }
        public QoEMetricsBuilder navigationTime(Duration navTime) { this.navigationTime = navTime; return this; }
        public QoEMetricsBuilder seekLatency(Duration seekLatency) { this.seekLatency = seekLatency; return this; }
        public QoEMetricsBuilder seekCount(int count) { this.seekCount = count; return this; }
        public QoEMetricsBuilder playbackDuration(Duration duration) { this.playbackDuration = duration; return this; }
        public QoEMetricsBuilder trackDuration(Duration duration) { this.trackDuration = duration; return this; }
        public QoEMetricsBuilder completed(boolean completed) { this.completed = completed; return this; }
        public QoEMetricsBuilder skipped(boolean skipped) { this.skipped = skipped; return this; }
        public QoEMetricsBuilder bytesDownloaded(long bytes) { this.bytesDownloaded = bytes; return this; }
        public QoEMetricsBuilder averageBitrate(double bitrate) { this.averageBitrate = bitrate; return this; }
        public QoEMetricsBuilder bandwidthEstimate(double bw) { this.bandwidthEstimate = bw; return this; }
        public QoEMetricsBuilder sessionStart(Instant start) { this.sessionStart = start; return this; }
        public QoEMetricsBuilder playbackStart(Instant start) { this.playbackStart = start; return this; }
        public QoEMetricsBuilder playbackEnd(Instant end) { this.playbackEnd = end; return this; }

        public QoEMetricsBuilder addStallingEvent(StallingEvent event) {
            this.stallingEvents.add(event);
            this.stallingCount++;
            this.totalStallingTime = this.totalStallingTime.plus(event.duration());
            return this;
        }

        public QoEMetrics build() {
            // Calculate MOS before building
            this.moScore = QoEMOSCalculator.calculateMOS(
                initialDelay, stallingCount, totalStallingTime, navigationTime
            );

            return new QoEMetrics(
                sessionId, trackId, deviceType,
                initialDelay, timeToFirstByte, bufferingTime,
                stallingCount, totalStallingTime, stallingEvents,
                navigationTime, seekLatency, seekCount,
                playbackDuration, trackDuration, completed, skipped,
                bytesDownloaded, averageBitrate, bandwidthEstimate,
                sessionStart, playbackStart, playbackEnd,
                moScore
            );
        }
    }

    public static QoEMetricsBuilder builder() {
        return new QoEMetricsBuilder();
    }
}
