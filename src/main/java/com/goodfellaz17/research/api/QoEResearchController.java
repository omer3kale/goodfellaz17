package com.goodfellaz17.research.api;

import com.goodfellaz17.research.qoe.model.QoEMetrics;
import com.goodfellaz17.research.qoe.model.QoEMOSCalculator;
import com.goodfellaz17.research.testbed.QoETestbedController;
import com.goodfellaz17.research.testbed.QoETestbedController.ExperimentStatus;
import com.goodfellaz17.research.testbed.QoETestbedController.ExperimentSummary;
import com.goodfellaz17.research.workload.ZhangWorkloadGenerator;
import com.goodfellaz17.research.workload.ZhangWorkloadGenerator.SessionSpec;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST API for QoE Research Testbed.
 *
 * Provides endpoints for:
 * - Starting/stopping experiments
 * - Generating workloads
 * - Collecting and exporting metrics
 * - Real-time experiment status
 */
@RestController
@RequestMapping("/api/research/qoe")
public class QoEResearchController {

    private final QoETestbedController testbedController;
    private final ZhangWorkloadGenerator workloadGenerator;

    public QoEResearchController(
            QoETestbedController testbedController,
            ZhangWorkloadGenerator workloadGenerator) {
        this.testbedController = testbedController;
        this.workloadGenerator = workloadGenerator;
    }

    // ==================== EXPERIMENT CONTROL ====================

    /**
     * Starts a new QoE measurement experiment.
     *
     * POST /api/research/qoe/experiment/start
     * {
     *   "durationHours": 24,
     *   "sessionsPerHour": 100,
     *   "mobileRatio": 0.65
     * }
     */
    @PostMapping("/experiment/start")
    public ResponseEntity<Map<String, Object>> startExperiment(
            @RequestBody ExperimentConfig config) {

        testbedController.startExperiment(
            config.durationHours(),
            config.sessionsPerHour(),
            config.mobileRatio()
        );

        return ResponseEntity.ok(Map.of(
            "status", "started",
            "config", config,
            "message", String.format(
                "Experiment started: %d hours, %.0f sessions/hour, %.0f%% mobile",
                config.durationHours(), config.sessionsPerHour(), config.mobileRatio() * 100)
        ));
    }

    public record ExperimentConfig(
        int durationHours,
        double sessionsPerHour,
        double mobileRatio
    ) {
        public ExperimentConfig {
            if (durationHours <= 0) durationHours = 1;
            if (sessionsPerHour <= 0) sessionsPerHour = 10;
            if (mobileRatio < 0 || mobileRatio > 1) mobileRatio = 0.65;
        }
    }

    /**
     * Stops the running experiment.
     *
     * POST /api/research/qoe/experiment/stop
     */
    @PostMapping("/experiment/stop")
    public ResponseEntity<Map<String, String>> stopExperiment() {
        testbedController.stopExperiment();
        return ResponseEntity.ok(Map.of(
            "status", "stopping",
            "message", "Experiment stop requested. Active sessions will complete."
        ));
    }

    /**
     * Gets current experiment status.
     *
     * GET /api/research/qoe/experiment/status
     */
    @GetMapping("/experiment/status")
    public ResponseEntity<ExperimentStatus> getExperimentStatus() {
        return ResponseEntity.ok(testbedController.getStatus());
    }

    // ==================== METRICS & REPORTING ====================

    /**
     * Gets experiment summary statistics.
     *
     * GET /api/research/qoe/metrics/summary
     */
    @GetMapping("/metrics/summary")
    public ResponseEntity<ExperimentSummary> getMetricsSummary() {
        return ResponseEntity.ok(testbedController.generateSummary());
    }

    /**
     * Gets experiment summary as markdown table.
     *
     * GET /api/research/qoe/metrics/summary/markdown
     */
    @GetMapping(value = "/metrics/summary/markdown", produces = "text/markdown")
    public ResponseEntity<String> getMetricsSummaryMarkdown() {
        return ResponseEntity.ok(testbedController.generateSummary().toMarkdownTable());
    }

    /**
     * Exports all collected metrics.
     *
     * GET /api/research/qoe/metrics/export
     */
    @GetMapping("/metrics/export")
    public ResponseEntity<List<QoEMetrics>> exportMetrics() {
        return ResponseEntity.ok(testbedController.exportMetrics());
    }

    /**
     * Clears collected metrics.
     *
     * DELETE /api/research/qoe/metrics
     */
    @DeleteMapping("/metrics")
    public ResponseEntity<Map<String, String>> clearMetrics() {
        testbedController.clearMetrics();
        return ResponseEntity.ok(Map.of(
            "status", "cleared",
            "message", "All metrics cleared"
        ));
    }

    // ==================== WORKLOAD GENERATION ====================

    /**
     * Generates a workload preview (without execution).
     *
     * POST /api/research/qoe/workload/preview
     */
    @PostMapping("/workload/preview")
    public ResponseEntity<WorkloadPreview> previewWorkload(
            @RequestBody WorkloadConfig config) {

        List<SessionSpec> sessions = workloadGenerator.generateWorkload(
            LocalDateTime.now(),
            Duration.ofHours(config.durationHours()),
            config.sessionsPerHour(),
            config.mobileRatio()
        );

        // Calculate summary statistics
        long mobileSessions = sessions.stream()
            .filter(s -> s.deviceType() == ZhangWorkloadGenerator.DeviceType.MOBILE)
            .count();

        double avgSessionLength = sessions.stream()
            .mapToDouble(s -> s.sessionLength().toMinutes())
            .average().orElse(0);

        double avgTracksPerSession = sessions.stream()
            .mapToDouble(SessionSpec::tracksInSession)
            .average().orElse(0);

        return ResponseEntity.ok(new WorkloadPreview(
            sessions.size(),
            mobileSessions,
            sessions.size() - mobileSessions,
            avgSessionLength,
            avgTracksPerSession,
            sessions.subList(0, Math.min(10, sessions.size()))  // First 10 as sample
        ));
    }

    public record WorkloadConfig(
        int durationHours,
        double sessionsPerHour,
        double mobileRatio
    ) {}

    public record WorkloadPreview(
        int totalSessions,
        long mobileSessions,
        long desktopSessions,
        double avgSessionLengthMinutes,
        double avgTracksPerSession,
        List<SessionSpec> sampleSessions
    ) {}

    // ==================== MOS CALCULATOR ====================

    /**
     * Calculates MOS for given QoE parameters.
     *
     * POST /api/research/qoe/mos/calculate
     */
    @PostMapping("/mos/calculate")
    public ResponseEntity<MOSResult> calculateMOS(@RequestBody MOSRequest request) {
        Duration initialDelay = Duration.ofMillis(request.initialDelayMs());
        Duration stallingTime = Duration.ofMillis(request.stallingDurationMs());
        Duration navTime = Duration.ofMillis(request.navigationTimeMs());

        double mos = QoEMOSCalculator.calculateMOS(
            initialDelay,
            request.stallingCount(),
            stallingTime,
            navTime
        );

        double mosInitialDelay = QoEMOSCalculator.calculateInitialDelayMOS(initialDelay);
        double mosStalling = QoEMOSCalculator.calculateStallingMOS(
            request.stallingCount(), stallingTime);
        double mosNavigation = QoEMOSCalculator.calculateNavigationMOS(navTime);

        return ResponseEntity.ok(new MOSResult(
            mos,
            QoEMOSCalculator.mosToQualityLabel(mos),
            mosInitialDelay,
            mosStalling,
            mosNavigation,
            QoEMOSCalculator.predictAbandonmentProbability(
                initialDelay, request.stallingCount())
        ));
    }

    public record MOSRequest(
        long initialDelayMs,
        int stallingCount,
        long stallingDurationMs,
        long navigationTimeMs
    ) {}

    public record MOSResult(
        double combinedMOS,
        String qualityLabel,
        double mosInitialDelay,
        double mosStalling,
        double mosNavigation,
        double abandonmentProbability
    ) {}

    // ==================== HOURLY PATTERN ====================

    /**
     * Gets the daily arrival rate pattern (Zhang 2013).
     *
     * GET /api/research/qoe/pattern/hourly?baseRate=100
     */
    @GetMapping("/pattern/hourly")
    public ResponseEntity<List<HourlyRate>> getHourlyPattern(
            @RequestParam(defaultValue = "100") double baseRate) {

        List<HourlyRate> rates = new java.util.ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            double rate = workloadGenerator.getArrivalRateForHour(hour, baseRate);
            rates.add(new HourlyRate(hour, rate));
        }
        return ResponseEntity.ok(rates);
    }

    public record HourlyRate(int hour, double sessionsPerHour) {}
}
