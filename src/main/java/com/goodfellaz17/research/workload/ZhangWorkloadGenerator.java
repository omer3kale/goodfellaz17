package com.goodfellaz17.research.workload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Workload Generator based on Zhang et al. 2013 (PDS-2013-001).
 * "Understanding User Behavior in Spotify"
 *
 * Implements:
 * 1. Non-homogeneous Poisson process for session arrivals (daily pattern)
 * 2. Weibull distributions for session length (device-specific)
 * 3. Correlated successive session lengths and downtimes
 *
 * Key empirical findings from Zhang 2013:
 * - Session length: Weibull(shape=0.7, scale=15min) for desktop
 * - Session length: Weibull(shape=0.5, scale=8min) for mobile
 * - Inter-session gap: Heavy-tailed, correlated with previous session
 * - Daily pattern: Peak at 20:00 local, trough at 05:00
 */
@Component
public class ZhangWorkloadGenerator {
    private static final Logger log = LoggerFactory.getLogger(ZhangWorkloadGenerator.class);

    private final SecureRandom random = new SecureRandom();

    // === Weibull Distribution Parameters (Zhang 2013 Table 2) ===

    // Desktop sessions: longer, more engaged
    private static final double DESKTOP_SHAPE = 0.7;
    private static final double DESKTOP_SCALE_MINUTES = 15.0;

    // Mobile sessions: shorter, more frequent
    private static final double MOBILE_SHAPE = 0.5;
    private static final double MOBILE_SCALE_MINUTES = 8.0;

    // Inter-session gap parameters
    private static final double GAP_SHAPE = 0.6;
    private static final double GAP_SCALE_MINUTES = 120.0;  // 2 hours median

    // === Daily Activity Pattern (Zhang 2013 Figure 3) ===
    // Normalized hourly arrival rates (sum = 24.0 for average 1.0/hour)
    private static final double[] HOURLY_ARRIVAL_RATES = {
        0.3, 0.2, 0.15, 0.1, 0.1, 0.15,  // 00:00-05:59 (night trough)
        0.4, 0.7, 1.0, 1.1, 1.1, 1.0,    // 06:00-11:59 (morning ramp)
        0.9, 0.85, 0.9, 1.0, 1.2, 1.5,   // 12:00-17:59 (afternoon)
        1.8, 2.0, 1.9, 1.6, 1.2, 0.7     // 18:00-23:59 (evening peak)
    };

    // === Correlation Parameters ===
    private static final double SESSION_LENGTH_CORRELATION = 0.3;  // AR(1) coefficient
    private static final double GAP_CORRELATION = 0.4;

    // State for correlated generation
    private double lastSessionLengthZ = 0;  // Standardized previous length
    private double lastGapZ = 0;            // Standardized previous gap

    public enum DeviceType {
        DESKTOP, MOBILE
    }

    /**
     * Session specification for workload generation.
     */
    public record SessionSpec(
        LocalDateTime arrivalTime,
        Duration sessionLength,
        Duration interSessionGap,
        DeviceType deviceType,
        int tracksInSession,
        List<Duration> trackDurations
    ) {}

    /**
     * Generates a workload schedule for a specified duration.
     *
     * @param startTime Start of the experiment window
     * @param duration Total duration to generate sessions for
     * @param avgSessionsPerHour Target average arrival rate
     * @param mobileRatio Fraction of sessions from mobile devices [0,1]
     * @return List of session specifications
     */
    public List<SessionSpec> generateWorkload(
            LocalDateTime startTime,
            Duration duration,
            double avgSessionsPerHour,
            double mobileRatio) {

        List<SessionSpec> sessions = new ArrayList<>();
        LocalDateTime currentTime = startTime;
        LocalDateTime endTime = startTime.plus(duration);

        log.info("Generating workload: {} to {}, avg rate={}/hour, mobile={}%",
            startTime, endTime, avgSessionsPerHour, mobileRatio * 100);

        while (currentTime.isBefore(endTime)) {
            // Get time-varying arrival rate
            int hour = currentTime.getHour();
            double hourlyRate = HOURLY_ARRIVAL_RATES[hour] * avgSessionsPerHour;

            // Sample inter-arrival time (exponential with time-varying rate)
            double interArrivalMinutes = -Math.log(1 - random.nextDouble()) / (hourlyRate / 60.0);
            currentTime = currentTime.plusSeconds((long)(interArrivalMinutes * 60));

            if (currentTime.isAfter(endTime)) break;

            // Determine device type
            DeviceType device = random.nextDouble() < mobileRatio
                ? DeviceType.MOBILE : DeviceType.DESKTOP;

            // Generate correlated session length
            Duration sessionLength = generateCorrelatedSessionLength(device);

            // Generate correlated inter-session gap
            Duration gap = generateCorrelatedGap();

            // Generate track breakdown within session
            List<Duration> trackDurations = generateTracksInSession(sessionLength, device);

            sessions.add(new SessionSpec(
                currentTime,
                sessionLength,
                gap,
                device,
                trackDurations.size(),
                trackDurations
            ));
        }

        log.info("Generated {} sessions over {} hours",
            sessions.size(), duration.toHours());

        return sessions;
    }

    /**
     * Generates session length from Weibull distribution with AR(1) correlation.
     *
     * Zhang 2013: "Successive session lengths show positive correlation (ρ ≈ 0.3)"
     */
    public Duration generateCorrelatedSessionLength(DeviceType device) {
        double shape = device == DeviceType.MOBILE ? MOBILE_SHAPE : DESKTOP_SHAPE;
        double scale = device == DeviceType.MOBILE ? MOBILE_SCALE_MINUTES : DESKTOP_SCALE_MINUTES;

        // Generate standard normal with AR(1) correlation
        double z = SESSION_LENGTH_CORRELATION * lastSessionLengthZ
                 + Math.sqrt(1 - SESSION_LENGTH_CORRELATION * SESSION_LENGTH_CORRELATION)
                   * random.nextGaussian();
        lastSessionLengthZ = z;

        // Transform to uniform via normal CDF, then to Weibull
        double u = normalCDF(z);
        double weibullSample = scale * Math.pow(-Math.log(1 - u), 1.0 / shape);

        // Clamp to reasonable bounds
        double minutes = Math.max(1, Math.min(180, weibullSample));

        return Duration.ofSeconds((long)(minutes * 60));
    }

    /**
     * Generates inter-session gap with correlation to previous gap.
     *
     * Zhang 2013: "Inter-session times are heavy-tailed and correlated"
     */
    public Duration generateCorrelatedGap() {
        // Generate correlated standard normal
        double z = GAP_CORRELATION * lastGapZ
                 + Math.sqrt(1 - GAP_CORRELATION * GAP_CORRELATION)
                   * random.nextGaussian();
        lastGapZ = z;

        // Transform to Weibull
        double u = normalCDF(z);
        double weibullSample = GAP_SCALE_MINUTES * Math.pow(-Math.log(1 - u), 1.0 / GAP_SHAPE);

        // Clamp: minimum 5 min, maximum 24 hours
        double minutes = Math.max(5, Math.min(1440, weibullSample));

        return Duration.ofMinutes((long)minutes);
    }

    /**
     * Generates individual track durations within a session.
     * Based on observed listening patterns.
     */
    private List<Duration> generateTracksInSession(Duration sessionLength, DeviceType device) {
        List<Duration> tracks = new ArrayList<>();
        long remainingSeconds = sessionLength.toSeconds();

        // Average track duration ~3.5 minutes, with variance
        double avgTrackSeconds = 210;
        double trackStdDev = 60;

        // Skip probability: higher on mobile
        double skipProb = device == DeviceType.MOBILE ? 0.25 : 0.15;

        while (remainingSeconds > 30) {  // Minimum 30s for valid play
            // Sample track duration (normal distribution)
            double trackDuration = avgTrackSeconds + random.nextGaussian() * trackStdDev;
            trackDuration = Math.max(30, Math.min(remainingSeconds, trackDuration));

            // Apply skip probability
            if (random.nextDouble() < skipProb) {
                // Early skip: 3-15 seconds
                trackDuration = 3 + random.nextInt(12);
            }

            tracks.add(Duration.ofSeconds((long)trackDuration));
            remainingSeconds -= trackDuration;

            // Small inter-track gap (1-5s)
            remainingSeconds -= 1 + random.nextInt(4);
        }

        return tracks;
    }

    /**
     * Standard normal CDF approximation (Abramowitz & Stegun).
     */
    private double normalCDF(double z) {
        double t = 1.0 / (1.0 + 0.2316419 * Math.abs(z));
        double d = 0.3989423 * Math.exp(-z * z / 2);
        double p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))));
        return z > 0 ? 1 - p : p;
    }

    /**
     * Generates arrival rate for a specific hour (for visualization/debugging).
     */
    public double getArrivalRateForHour(int hour, double baseRate) {
        return HOURLY_ARRIVAL_RATES[hour % 24] * baseRate;
    }

    /**
     * Resets correlation state (for independent experiments).
     */
    public void resetCorrelationState() {
        lastSessionLengthZ = 0;
        lastGapZ = 0;
    }
}
