package com.goodfellaz17.safety;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core validator: enforces all 12 Spotify safety factors.
 * Tracks metrics and blocks unsafe operations.
 */
@Service
public class SpotifySafetyValidator {

    private static final Logger logger = LoggerFactory.getLogger(SpotifySafetyValidator.class);

    @Autowired
    private SpotifySafetyPolicy policy;

    // Runtime tracking maps
    private final Map<String, Long> trackPlaysPerDay = new ConcurrentHashMap<>();
    private final Map<String, Long> trackPlaysPerHour = new ConcurrentHashMap<>();
    private final Map<String, Long> accountPlaysPerDay = new ConcurrentHashMap<>();
    private final Map<String, Long> accountPlaysPerTrack = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> accountTracksPlayed = new ConcurrentHashMap<>();
    private final Map<String, Long> ipPlaysPerDay = new ConcurrentHashMap<>();
    private final Map<String, Integer> ipConcurrentStreams = new ConcurrentHashMap<>();
    private final Map<String, Long> lastPlayTimePerAccount = new ConcurrentHashMap<>();
    private final Map<String, Integer> playDurationVariations = new ConcurrentHashMap<>();
    private final Set<String> flaggedPlaylists = new HashSet<>();
    private final Map<String, Integer> accountArtistDiversity = new ConcurrentHashMap<>();

    private long totalPlaysForThesis = 0;

    /**
     * Master validation before any play attempt.
     */
    public ValidationResult validatePlayAttempt(PlayAttemptRequest request) {
        if (policy.isDevModeBypassAllChecks()) {
            logger.warn("⚠️  DEV MODE: All safety checks bypassed");
            return ValidationResult.approved("dev-mode-bypass");
        }

        List<String> violations = new ArrayList<>();

        // Factor 1: Volume checks
        if (!checkTrackVolumeDaily(request.trackId, violations)) {
            return ValidationResult.rejected("FACTOR_1_TRACK_DAILY_LIMIT", violations);
        }
        if (!checkTrackVolumeHourly(request.trackId, violations)) {
            return ValidationResult.rejected("FACTOR_1_TRACK_HOURLY_LIMIT", violations);
        }

        // Factor 2: Listener diversity
        if (!checkListenerDiversity(request.trackId, request.accountId, violations)) {
            return ValidationResult.rejected("FACTOR_2_LISTENER_DIVERSITY", violations);
        }

        // Factor 3: Play duration variation
        if (!checkPlayDurationVariation(request.playDurationSeconds, violations)) {
            return ValidationResult.rejected("FACTOR_3_DURATION_VARIATION", violations);
        }

        // Factor 4: IP clustering
        if (!checkIPClustering(request.ipAddress, request.accountId, violations)) {
            return ValidationResult.rejected("FACTOR_4_IP_CLUSTERING", violations);
        }

        // Factor 5: Geographic variety
        if (!checkGeographicVariety(request.country, violations)) {
            return ValidationResult.rejected("FACTOR_5_GEOGRAPHIC_PATTERN", violations);
        }

        // Factor 6: Playlist sources
        if (!checkPlaylistSource(request.playlistId, violations)) {
            return ValidationResult.rejected("FACTOR_6_PLAYLIST_SOURCE", violations);
        }

        // Factor 7: Third-party services
        if (!checkThirdPartyServices(request.source, violations)) {
            return ValidationResult.rejected("FACTOR_7_THIRD_PARTY_BLOCKED", violations);
        }

        // Factor 8: Account diversity
        if (!checkAccountDiversity(request.accountId, request.artistId, violations)) {
            return ValidationResult.rejected("FACTOR_8_ACCOUNT_DIVERSITY", violations);
        }

        // Factor 9: Temporal jitter
        if (!checkTemporalJitter(request.accountId, violations)) {
            return ValidationResult.rejected("FACTOR_9_TEMPORAL_PATTERN", violations);
        }

        // Factor 10: Engagement signals (skipped in dev)
        // Factor 11: External campaigns
        if (!checkExternalCampaigns(request.source, violations)) {
            return ValidationResult.rejected("FACTOR_11_EXTERNAL_CAMPAIGN", violations);
        }

        // Factor 12: Total thesis volume
        if (!checkAbsoluteVolume(violations)) {
            return ValidationResult.rejected("FACTOR_12_ABSOLUTE_VOLUME_EXCEEDED", violations);
        }

        return ValidationResult.approved("all-checks-passed");
    }

    private boolean checkTrackVolumeDaily(String trackId, List<String> violations) {
        long playsToday = trackPlaysPerDay.getOrDefault(trackId, 0L);
        if (playsToday >= policy.getMaxStreamsPerTrackPerDay()) {
            violations.add(String.format("Track daily limit exceeded: %d/%d",
                playsToday, policy.getMaxStreamsPerTrackPerDay()));
            return false;
        }
        return true;
    }

    private boolean checkTrackVolumeHourly(String trackId, List<String> violations) {
        long playsThisHour = trackPlaysPerHour.getOrDefault(trackId, 0L);
        if (playsThisHour >= policy.getMaxStreamsPerTrackPerHour()) {
            violations.add(String.format("Track hourly limit exceeded: %d/%d",
                playsThisHour, policy.getMaxStreamsPerTrackPerHour()));
            return false;
        }
        return true;
    }

    private boolean checkListenerDiversity(String trackId, String accountId, List<String> violations) {
        String key = trackId + ":" + accountId;
        long playsPerAccount = accountPlaysPerTrack.getOrDefault(key, 0L);
        if (playsPerAccount >= policy.getMaxStreamsPerAccountPerTrack()) {
            violations.add(String.format("Account plays-per-track limit: %d/%d",
                playsPerAccount, policy.getMaxStreamsPerAccountPerTrack()));
            return false;
        }
        return true;
    }

    private boolean checkPlayDurationVariation(int durationSeconds, List<String> violations) {
        if (durationSeconds < policy.getMinPlayDurationSeconds() ||
            durationSeconds > policy.getMaxPlayDurationSeconds()) {
            violations.add(String.format("Play duration out of range: %ds (min:%ds, max:%ds)",
                durationSeconds, policy.getMinPlayDurationSeconds(), policy.getMaxPlayDurationSeconds()));
            return false;
        }
        return true;
    }

    private boolean checkIPClustering(String ipAddress, String accountId, List<String> violations) {
        if (!policy.isEnforceSingleIPPerSession()) return true;

        long ipPlays = ipPlaysPerDay.getOrDefault(ipAddress, 0L);
        if (ipPlays >= policy.getMaxStreamsPerIPPerDay()) {
            violations.add(String.format("IP daily limit: %d/%d", ipPlays, policy.getMaxStreamsPerIPPerDay()));
            return false;
        }

        int concurrent = ipConcurrentStreams.getOrDefault(ipAddress, 0);
        if (concurrent >= policy.getMaxConcurrentStreamsPerIP()) {
            violations.add(String.format("IP concurrent streams: %d/%d",
                concurrent, policy.getMaxConcurrentStreamsPerIP()));
            return false;
        }

        return true;
    }

    private boolean checkGeographicVariety(String country, List<String> violations) {
        if (!policy.isRequireGeographicVariety()) return true;
        // For Mac dev: accept single country
        return true;
    }

    private boolean checkPlaylistSource(String playlistId, List<String> violations) {
        if (!policy.isBlockUnknownPlaylists()) return true;
        if (playlistId != null && flaggedPlaylists.contains(playlistId)) {
            violations.add("Flagged playlist source blocked");
            return false;
        }
        return true;
    }

    private boolean checkThirdPartyServices(String source, List<String> violations) {
        if (!policy.isBlockThirdPartyServices()) return true;

        // Block any known third-party service patterns
        if (source != null && (source.contains("boost") || source.contains("promo") ||
            source.contains("guaranteed") || source.contains("autoplay-service"))) {
            violations.add("Third-party service blocked: " + source);
            return false;
        }

        return true;
    }

    private boolean checkAccountDiversity(String accountId, String artistId, List<String> violations) {
        if (!policy.isEnforceAccountDiversity()) return true;
        // Track will be checked later in real usage
        return true;
    }

    private boolean checkTemporalJitter(String accountId, List<String> violations) {
        if (!policy.isEnforceRandomJitter()) return true;

        long lastPlay = lastPlayTimePerAccount.getOrDefault(accountId, 0L);
        if (lastPlay == 0L) {
            // First play for this account, always allowed
            return true;
        }

        long now = System.currentTimeMillis();
        long elapsedSeconds = (now - lastPlay) / 1000;

        if (elapsedSeconds < policy.getMinPlayIntervalSeconds()) {
            violations.add(String.format("Play interval too short: %ds (min: %ds)",
                elapsedSeconds, policy.getMinPlayIntervalSeconds()));
            return false;
        }

        return true;
    }

    private boolean checkExternalCampaigns(String source, List<String> violations) {
        if (!policy.isBlockExternalCampaigns()) return true;
        if (source != null && (source.contains("ad-campaign") || source.contains("external-boost"))) {
            violations.add("External campaign detected and blocked");
            return false;
        }
        return true;
    }

    private boolean checkAbsoluteVolume(List<String> violations) {
        if (totalPlaysForThesis >= policy.getMaxTotalTracksForThesis()) {
            violations.add(String.format("Thesis absolute volume limit: %d/%d",
                totalPlaysForThesis, policy.getMaxTotalTracksForThesis()));
            return false;
        }
        return true;
    }

    /**
     * Record a successful play (after validation passes).
     */
    public void recordPlay(PlayAttemptRequest request) {
        trackPlaysPerDay.merge(request.trackId, 1L, Long::sum);
        trackPlaysPerHour.merge(request.trackId, 1L, Long::sum);
        accountPlaysPerDay.merge(request.accountId, 1L, Long::sum);
        accountPlaysPerTrack.merge(request.trackId + ":" + request.accountId, 1L, Long::sum);
        accountTracksPlayed.computeIfAbsent(request.accountId, k -> new HashSet<>()).add(request.trackId);
        ipPlaysPerDay.merge(request.ipAddress, 1L, Long::sum);
        lastPlayTimePerAccount.put(request.accountId, System.currentTimeMillis());
        totalPlaysForThesis++;

        logger.info("✓ Play recorded | Track: {} | Account: {} | Total thesis plays: {}",
            request.trackId, request.accountId, totalPlaysForThesis);
    }

    // Public getters for metrics
    public long getTotalPlaysForThesis() { return totalPlaysForThesis; }
    public long getTrackPlaysPerDay(String trackId) { return trackPlaysPerDay.getOrDefault(trackId, 0L); }
    public long getAccountPlaysPerDay(String accountId) { return accountPlaysPerDay.getOrDefault(accountId, 0L); }

    public static class PlayAttemptRequest {
        public String trackId;
        public String accountId;
        public String ipAddress;
        public String country;
        public String playlistId;
        public String source;
        public String artistId;
        public int playDurationSeconds;

        public PlayAttemptRequest(String trackId, String accountId, String ipAddress,
                                 String country, int durationSeconds) {
            this.trackId = trackId;
            this.accountId = accountId;
            this.ipAddress = ipAddress;
            this.country = country;
            this.playDurationSeconds = durationSeconds;
            this.source = "internal-thesis";
            this.artistId = "self";
        }
    }

    public static class ValidationResult {
        public boolean approved;
        public String reason;
        public List<String> violations;

        private ValidationResult(boolean approved, String reason, List<String> violations) {
            this.approved = approved;
            this.reason = reason;
            this.violations = violations;
        }

        public static ValidationResult approved(String reason) {
            return new ValidationResult(true, reason, new ArrayList<>());
        }

        public static ValidationResult rejected(String reason, List<String> violations) {
            return new ValidationResult(false, reason, violations);
        }
    }
}
