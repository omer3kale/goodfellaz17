package com.goodfellaz17.research.testbed;

import com.goodfellaz17.research.qoe.model.QoEMetrics;
import com.goodfellaz17.research.qoe.model.QoEMetrics.QoEMetricsBuilder;
import com.goodfellaz17.research.workload.ZhangWorkloadGenerator;
import com.goodfellaz17.research.workload.ZhangWorkloadGenerator.SessionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * QoE Research Testbed Controller.
 *
 * Architecture based on Schwind 2019 Android testbed, adapted for Node/Java:
 * - Controller: Schedules synthetic listening sessions
 * - Workers: Execute sessions via browser automation layer
 * - Metrics Export: Aggregates and exports for analysis
 *
 * Supports:
 * - Synthetic workload generation (Zhang 2013 distributions)
 * - Real-time QoE measurement (Schwind 2019 methodology)
 * - 4-week experiment with summary statistics
 */
@Service
public class QoETestbedController {
    private static final Logger log = LoggerFactory.getLogger(QoETestbedController.class);

    private final ZhangWorkloadGenerator workloadGenerator;

    // === Experiment State ===
    private final ConcurrentLinkedQueue<QoEMetrics> collectedMetrics = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, SessionState> activeSessions = new ConcurrentHashMap<>();
    private final AtomicInteger totalSessionsScheduled = new AtomicInteger(0);
    private final AtomicInteger totalSessionsCompleted = new AtomicInteger(0);
    private final AtomicLong experimentStartTime = new AtomicLong(0);

    // === Configuration ===
    private volatile boolean experimentRunning = false;
    private volatile int maxConcurrentSessions = 50;
    private volatile double targetSessionsPerHour = 100;
    private volatile double mobileRatio = 0.65;

    public QoETestbedController(ZhangWorkloadGenerator workloadGenerator) {
        this.workloadGenerator = workloadGenerator;
    }

    /**
     * Tracks state of an active session.
     */
    private static class SessionState {
        final String sessionId;
        final Instant startTime;
        final QoEMetricsBuilder metricsBuilder;
        volatile Instant playbackStartTime;
        volatile long bytesReceived;

        SessionState(String sessionId, QoEMetrics.DeviceType deviceType) {
            this.sessionId = sessionId;
            this.startTime = Instant.now();
            this.metricsBuilder = QoEMetrics.builder()
                .sessionId(sessionId)
                .deviceType(deviceType)
                .sessionStart(startTime);
        }
    }

    // ==================== EXPERIMENT CONTROL ====================

    /**
     * Starts a QoE measurement experiment.
     *
     * @param durationHours Total experiment duration in hours
     * @param sessionsPerHour Average session arrival rate
     * @param mobileRatio Fraction of mobile sessions [0,1]
     * @return CompletableFuture that completes when experiment ends
     */
    @Async
    public CompletableFuture<ExperimentSummary> startExperiment(
            int durationHours,
            double sessionsPerHour,
            double mobileRatio) {

        if (experimentRunning) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Experiment already running"));
        }

        experimentRunning = true;
        this.targetSessionsPerHour = sessionsPerHour;
        this.mobileRatio = mobileRatio;
        experimentStartTime.set(System.currentTimeMillis());

        log.info("Starting QoE experiment: duration={}h, rate={}/h, mobile={}%",
            durationHours, sessionsPerHour, mobileRatio * 100);

        // Generate workload schedule
        List<SessionSpec> workload = workloadGenerator.generateWorkload(
            LocalDateTime.now(),
            Duration.ofHours(durationHours),
            sessionsPerHour,
            mobileRatio
        );

        totalSessionsScheduled.set(workload.size());

        return executeWorkload(workload)
            .thenApply(v -> generateSummary())
            .whenComplete((summary, error) -> {
                experimentRunning = false;
                if (error != null) {
                    log.error("Experiment failed", error);
                } else {
                    log.info("Experiment completed: {} sessions",
                        totalSessionsCompleted.get());
                }
            });
    }

    /**
     * Stops the running experiment gracefully.
     */
    public void stopExperiment() {
        experimentRunning = false;
        log.info("Experiment stop requested");
    }

    // ==================== SESSION EXECUTION ====================

    /**
     * Executes the workload by scheduling sessions according to their arrival times.
     */
    private CompletableFuture<Void> executeWorkload(List<SessionSpec> workload) {
        return Flux.fromIterable(workload)
            .delayUntil(spec -> {
                // Calculate delay until session should start
                Duration delay = Duration.between(LocalDateTime.now(), spec.arrivalTime());
                if (delay.isNegative()) delay = Duration.ZERO;
                return Mono.delay(delay);
            })
            .flatMap(spec -> executeSession(spec), maxConcurrentSessions)
            .then()
            .toFuture();
    }

    /**
     * Executes a single synthetic listening session.
     */
    private Mono<QoEMetrics> executeSession(SessionSpec spec) {
        if (!experimentRunning) {
            return Mono.empty();
        }

        String sessionId = UUID.randomUUID().toString();
        QoEMetrics.DeviceType deviceType = spec.deviceType() == ZhangWorkloadGenerator.DeviceType.MOBILE
            ? QoEMetrics.DeviceType.MOBILE_IOS
            : QoEMetrics.DeviceType.DESKTOP;

        SessionState state = new SessionState(sessionId, deviceType);
        activeSessions.put(sessionId, state);

        return Mono.fromCallable(() -> {
            log.debug("Starting session {}: device={}, tracks={}",
                sessionId, spec.deviceType(), spec.tracksInSession());

            // Simulate initial connection/navigation delay
            Duration navDelay = simulateNavigationDelay();
            state.metricsBuilder.navigationTime(navDelay);

            // Execute track playback sequence
            Duration totalPlayback = Duration.ZERO;
            int stallingEvents = 0;
            Duration totalStalling = Duration.ZERO;

            for (int i = 0; i < spec.trackDurations().size() && experimentRunning; i++) {
                Duration trackDuration = spec.trackDurations().get(i);

                // Simulate initial buffering delay for each track
                Duration initialDelay = simulateInitialDelay();
                if (i == 0) {
                    state.metricsBuilder.initialDelay(initialDelay);
                }

                // Simulate playback with potential stalling
                StallingResult stalling = simulatePlaybackWithStalling(trackDuration);
                totalPlayback = totalPlayback.plus(trackDuration);
                stallingEvents += stalling.count;
                totalStalling = totalStalling.plus(stalling.duration);

                // Inter-track gap
                Thread.sleep(1000 + (long)(Math.random() * 4000));
            }

            state.metricsBuilder
                .playbackDuration(totalPlayback)
                .stallingCount(stallingEvents)
                .totalStallingTime(totalStalling)
                .completed(totalPlayback.toSeconds() >= 30)
                .playbackEnd(Instant.now());

            QoEMetrics metrics = state.metricsBuilder.build();
            collectedMetrics.add(metrics);
            totalSessionsCompleted.incrementAndGet();

            return metrics;

        })
        .subscribeOn(Schedulers.boundedElastic())
        .doFinally(signal -> activeSessions.remove(sessionId));
    }

    // ==================== SIMULATION HELPERS ====================

    /**
     * Simulates navigation/app loading delay.
     * Based on Schwind 2019: typically 0.5-3s for responsive apps.
     */
    private Duration simulateNavigationDelay() {
        // Log-normal distribution for navigation time
        double navSeconds = Math.exp(Math.log(1.0) + 0.5 * Math.random());
        try {
            Thread.sleep((long)(navSeconds * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Duration.ofMillis((long)(navSeconds * 1000));
    }

    /**
     * Simulates initial buffering delay.
     * Based on Schwind 2019: Spotify typically achieves <2s.
     */
    private Duration simulateInitialDelay() {
        // Exponential distribution with mean 0.8s (good network)
        double delaySeconds = -0.8 * Math.log(1 - Math.random());
        delaySeconds = Math.min(delaySeconds, 10);  // Cap at 10s

        try {
            Thread.sleep((long)(delaySeconds * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Duration.ofMillis((long)(delaySeconds * 1000));
    }

    private record StallingResult(int count, Duration duration) {}

    /**
     * Simulates playback with potential stalling events.
     * Stalling probability based on simulated bandwidth variation.
     */
    private StallingResult simulatePlaybackWithStalling(Duration playbackDuration) {
        int stallingCount = 0;
        Duration totalStalling = Duration.ZERO;

        // Probability of stalling per minute of playback
        double stallingProbPerMinute = 0.02;  // 2% per minute

        long minutes = playbackDuration.toMinutes();
        for (int m = 0; m < minutes; m++) {
            if (Math.random() < stallingProbPerMinute) {
                // Stalling event: 0.5-5 seconds
                Duration stallDuration = Duration.ofMillis(
                    500 + (long)(Math.random() * 4500));
                stallingCount++;
                totalStalling = totalStalling.plus(stallDuration);

                try {
                    Thread.sleep(stallDuration.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // Simulate actual playback time (compressed for testing)
        long simulatedPlaybackMs = Math.min(playbackDuration.toMillis(), 5000);
        try {
            Thread.sleep(simulatedPlaybackMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return new StallingResult(stallingCount, totalStalling);
    }

    // ==================== METRICS & REPORTING ====================

    /**
     * Generates experiment summary statistics.
     */
    public ExperimentSummary generateSummary() {
        List<QoEMetrics> metrics = List.copyOf(collectedMetrics);

        if (metrics.isEmpty()) {
            return new ExperimentSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        // Calculate statistics
        double avgMOS = metrics.stream()
            .mapToDouble(QoEMetrics::moScore)
            .average().orElse(0);

        double avgInitialDelay = metrics.stream()
            .mapToDouble(m -> m.initialDelay().toMillis())
            .average().orElse(0);

        double avgStallingCount = metrics.stream()
            .mapToDouble(QoEMetrics::stallingCount)
            .average().orElse(0);

        double avgStallingDuration = metrics.stream()
            .mapToDouble(m -> m.totalStallingTime().toMillis())
            .average().orElse(0);

        long sessionsWithStalling = metrics.stream()
            .filter(m -> m.stallingCount() > 0)
            .count();

        double completionRate = metrics.stream()
            .filter(QoEMetrics::completed)
            .count() * 100.0 / metrics.size();

        // MOS distribution
        long excellentCount = metrics.stream()
            .filter(m -> m.moScore() >= 4.3).count();
        long goodCount = metrics.stream()
            .filter(m -> m.moScore() >= 3.6 && m.moScore() < 4.3).count();
        long fairCount = metrics.stream()
            .filter(m -> m.moScore() >= 2.6 && m.moScore() < 3.6).count();
        long poorCount = metrics.stream()
            .filter(m -> m.moScore() < 2.6).count();

        return new ExperimentSummary(
            metrics.size(),
            avgMOS,
            avgInitialDelay,
            avgStallingCount,
            avgStallingDuration,
            sessionsWithStalling * 100.0 / metrics.size(),
            completionRate,
            excellentCount,
            goodCount,
            fairCount,
            poorCount
        );
    }

    /**
     * Experiment summary record for reporting.
     */
    public record ExperimentSummary(
        int totalSessions,
        double averageMOS,
        double averageInitialDelayMs,
        double averageStallingCount,
        double averageStallingDurationMs,
        double sessionsWithStallingPercent,
        double completionRatePercent,
        long mosExcellent,
        long mosGood,
        long mosFair,
        long mosPoor
    ) {
        public String toMarkdownTable() {
            return String.format("""
                ## QoE Experiment Summary

                | Metric | Value |
                |--------|-------|
                | Total Sessions | %d |
                | Average MOS | %.2f |
                | Avg Initial Delay | %.0f ms |
                | Avg Stalling Events | %.2f |
                | Avg Stalling Duration | %.0f ms |
                | Sessions with Stalling | %.1f%% |
                | Completion Rate | %.1f%% |

                ### MOS Distribution
                | Rating | Count | Percentage |
                |--------|-------|------------|
                | Excellent (≥4.3) | %d | %.1f%% |
                | Good (3.6-4.3) | %d | %.1f%% |
                | Fair (2.6-3.6) | %d | %.1f%% |
                | Poor (<2.6) | %d | %.1f%% |
                """,
                totalSessions, averageMOS,
                averageInitialDelayMs, averageStallingCount,
                averageStallingDurationMs, sessionsWithStallingPercent,
                completionRatePercent,
                mosExcellent, mosExcellent * 100.0 / totalSessions,
                mosGood, mosGood * 100.0 / totalSessions,
                mosFair, mosFair * 100.0 / totalSessions,
                mosPoor, mosPoor * 100.0 / totalSessions
            );
        }
    }

    /**
     * Returns current experiment status.
     */
    public ExperimentStatus getStatus() {
        return new ExperimentStatus(
            experimentRunning,
            totalSessionsScheduled.get(),
            totalSessionsCompleted.get(),
            activeSessions.size(),
            experimentStartTime.get() > 0
                ? Duration.ofMillis(System.currentTimeMillis() - experimentStartTime.get())
                : Duration.ZERO
        );
    }

    public record ExperimentStatus(
        boolean running,
        int sessionsScheduled,
        int sessionsCompleted,
        int activeSessions,
        Duration elapsedTime
    ) {}

    /**
     * Exports collected metrics for external analysis.
     */
    public List<QoEMetrics> exportMetrics() {
        return List.copyOf(collectedMetrics);
    }

    /**
     * Clears collected metrics (for new experiment).
     */
    public void clearMetrics() {
        collectedMetrics.clear();
        totalSessionsScheduled.set(0);
        totalSessionsCompleted.set(0);
        workloadGenerator.resetCorrelationState();
    }
}
