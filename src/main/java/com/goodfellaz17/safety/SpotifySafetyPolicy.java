package com.goodfellaz17.safety;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Defines all Spotify safety guardrails per the 12-factor risk model.
 * These are hard boundaries—violations trigger automatic stops.
 */
@Component
@ConfigurationProperties(prefix = "spotify.safety")
public class SpotifySafetyPolicy {

    // Factor 1: Stream volume & spikes
    private int maxStreamsPerTrackPerDay = 50;        // Hard limit: never exceed
    private int maxStreamsPerTrackPerHour = 10;       // Rate limit per hour
    private int maxStreamsPerAccountPerDay = 30;      // Per-account daily limit

    // Factor 2: Streams per listener
    private int maxStreamsPerAccountPerTrack = 20;    // One account can't dominate a track
    private int minUniqueListenerRatio = 3;           // At least 3 unique listeners per 10 plays

    // Factor 3: Play duration & interaction
    private int minPlayDurationSeconds = 15;          // Don't skip immediately
    private int maxPlayDurationSeconds = 300;         // Cap at 5 min (realistic listening)
    private int playVariationPercent = 40;            // 40% of plays should vary duration

    // Factor 4: IP / device clustering
    private boolean enforceSingleIPPerSession = true; // One IP per streaming session
    private int maxConcurrentStreamsPerIP = 2;        // Max parallel streams from one IP
    private int maxStreamsPerIPPerDay = 100;          // Total IP-level daily limit

    // Factor 5: Geographic patterns
    private boolean requireGeographicVariety = true;  // Don't cluster all in one region
    private int minCountriesRequired = 1;             // For Mac dev: just 1 is fine
    private long timezoneVarianceMinutesRequired = 0; // No timezone req during dev

    // Factor 6: Playlist sources
    private boolean blockUnknownPlaylists = true;     // Auto-reject unknown playlist sources
    private boolean blockPlaylistBoosts = true;       // Never accept bot-playlist traffic
    private long maxAutoPlaylistAge = 7 * 24 * 60 * 60 * 1000; // Don't use playlists >7d old

    // Factor 7: Third-party services
    private boolean blockThirdPartyServices = true;   // CRITICAL: always true
    private String[] whitelistedServices = {};        // Empty = no third parties allowed

    // Factor 8: Account behavior realism
    private boolean enforceAccountDiversity = true;   // Accounts must play other artists
    private int minOtherArtistsPerAccount = 5;        // Must listen to 5+ other artists
    private int minPlaylistsPerAccount = 3;           // Natural account should have 3+ playlists

    // Factor 9: Temporal patterns & jitter
    private boolean enforceRandomJitter = true;       // Add jitter to timing
    private int minJitterMs = 500;                     // Minimum random delay
    private int maxJitterMs = 5000;                    // Maximum random delay (5s)
    private int minPlayIntervalSeconds = 30;          // At least 30s between plays from same account

    // Factor 10: Engagement vs streams
    private boolean requireEngagementSignals = false; // Optional during dev (off)
    private int minSavesPerThousandStreams = 5;       // Realistic engagement ratio
    private int minFollowersPerThousandStreams = 3;   // Realistic follow ratio

    // Factor 11: External traffic anomalies
    private boolean blockExternalCampaigns = true;    // Don't couple to ad campaigns
    private int maxSourcesPerTrack = 2;               // Limit traffic sources (internal only)

    // Factor 12: Total lifetime volume
    private int maxTotalTracksForThesis = 100;        // Absolute total play cap for thesis work
    private boolean enableAbsoluteVolumeAudit = true; // Log all plays for audit

    // Developer mode: disable all checks (for testing against mocks only)
    private boolean devModeBypassAllChecks = false;   // ONLY set true for local mock testing

    // Mode: "MOCK" (local testing) vs "SAFE" (real Spotify, guardrails on)
    private String operatingMode = "MOCK";            // Default: safe mode

    // Getters (Spring will auto-inject from application.yml)
    public int getMaxStreamsPerTrackPerDay() { return maxStreamsPerTrackPerDay; }
    public void setMaxStreamsPerTrackPerDay(int val) { this.maxStreamsPerTrackPerDay = val; }

    public int getMaxStreamsPerTrackPerHour() { return maxStreamsPerTrackPerHour; }
    public void setMaxStreamsPerTrackPerHour(int val) { this.maxStreamsPerTrackPerHour = val; }

    public int getMaxStreamsPerAccountPerDay() { return maxStreamsPerAccountPerDay; }
    public void setMaxStreamsPerAccountPerDay(int val) { this.maxStreamsPerAccountPerDay = val; }

    public int getMaxStreamsPerAccountPerTrack() { return maxStreamsPerAccountPerTrack; }
    public void setMaxStreamsPerAccountPerTrack(int val) { this.maxStreamsPerAccountPerTrack = val; }

    public int getMinUniqueListenerRatio() { return minUniqueListenerRatio; }
    public void setMinUniqueListenerRatio(int val) { this.minUniqueListenerRatio = val; }

    public int getMinPlayDurationSeconds() { return minPlayDurationSeconds; }
    public void setMinPlayDurationSeconds(int val) { this.minPlayDurationSeconds = val; }

    public int getMaxPlayDurationSeconds() { return maxPlayDurationSeconds; }
    public void setMaxPlayDurationSeconds(int val) { this.maxPlayDurationSeconds = val; }

    public int getPlayVariationPercent() { return playVariationPercent; }
    public void setPlayVariationPercent(int val) { this.playVariationPercent = val; }

    public boolean isEnforceSingleIPPerSession() { return enforceSingleIPPerSession; }
    public void setEnforceSingleIPPerSession(boolean val) { this.enforceSingleIPPerSession = val; }

    public int getMaxConcurrentStreamsPerIP() { return maxConcurrentStreamsPerIP; }
    public void setMaxConcurrentStreamsPerIP(int val) { this.maxConcurrentStreamsPerIP = val; }

    public int getMaxStreamsPerIPPerDay() { return maxStreamsPerIPPerDay; }
    public void setMaxStreamsPerIPPerDay(int val) { this.maxStreamsPerIPPerDay = val; }

    public boolean isRequireGeographicVariety() { return requireGeographicVariety; }
    public void setRequireGeographicVariety(boolean val) { this.requireGeographicVariety = val; }

    public int getMinCountriesRequired() { return minCountriesRequired; }
    public void setMinCountriesRequired(int val) { this.minCountriesRequired = val; }

    public long getTimezoneVarianceMinutesRequired() { return timezoneVarianceMinutesRequired; }
    public void setTimezoneVarianceMinutesRequired(long val) { this.timezoneVarianceMinutesRequired = val; }

    public boolean isBlockUnknownPlaylists() { return blockUnknownPlaylists; }
    public void setBlockUnknownPlaylists(boolean val) { this.blockUnknownPlaylists = val; }

    public boolean isBlockPlaylistBoosts() { return blockPlaylistBoosts; }
    public void setBlockPlaylistBoosts(boolean val) { this.blockPlaylistBoosts = val; }

    public long getMaxAutoPlaylistAge() { return maxAutoPlaylistAge; }
    public void setMaxAutoPlaylistAge(long val) { this.maxAutoPlaylistAge = val; }

    public boolean isBlockThirdPartyServices() { return blockThirdPartyServices; }
    public void setBlockThirdPartyServices(boolean val) { this.blockThirdPartyServices = val; }

    public String[] getWhitelistedServices() { return whitelistedServices; }
    public void setWhitelistedServices(String[] val) { this.whitelistedServices = val; }

    public boolean isEnforceAccountDiversity() { return enforceAccountDiversity; }
    public void setEnforceAccountDiversity(boolean val) { this.enforceAccountDiversity = val; }

    public int getMinOtherArtistsPerAccount() { return minOtherArtistsPerAccount; }
    public void setMinOtherArtistsPerAccount(int val) { this.minOtherArtistsPerAccount = val; }

    public int getMinPlaylistsPerAccount() { return minPlaylistsPerAccount; }
    public void setMinPlaylistsPerAccount(int val) { this.minPlaylistsPerAccount = val; }

    public boolean isEnforceRandomJitter() { return enforceRandomJitter; }
    public void setEnforceRandomJitter(boolean val) { this.enforceRandomJitter = val; }

    public int getMinJitterMs() { return minJitterMs; }
    public void setMinJitterMs(int val) { this.minJitterMs = val; }

    public int getMaxJitterMs() { return maxJitterMs; }
    public void setMaxJitterMs(int val) { this.maxJitterMs = val; }

    public int getMinPlayIntervalSeconds() { return minPlayIntervalSeconds; }
    public void setMinPlayIntervalSeconds(int val) { this.minPlayIntervalSeconds = val; }

    public boolean isRequireEngagementSignals() { return requireEngagementSignals; }
    public void setRequireEngagementSignals(boolean val) { this.requireEngagementSignals = val; }

    public int getMinSavesPerThousandStreams() { return minSavesPerThousandStreams; }
    public void setMinSavesPerThousandStreams(int val) { this.minSavesPerThousandStreams = val; }

    public int getMinFollowersPerThousandStreams() { return minFollowersPerThousandStreams; }
    public void setMinFollowersPerThousandStreams(int val) { this.minFollowersPerThousandStreams = val; }

    public boolean isBlockExternalCampaigns() { return blockExternalCampaigns; }
    public void setBlockExternalCampaigns(boolean val) { this.blockExternalCampaigns = val; }

    public int getMaxSourcesPerTrack() { return maxSourcesPerTrack; }
    public void setMaxSourcesPerTrack(int val) { this.maxSourcesPerTrack = val; }

    public int getMaxTotalTracksForThesis() { return maxTotalTracksForThesis; }
    public void setMaxTotalTracksForThesis(int val) { this.maxTotalTracksForThesis = val; }

    public boolean isEnableAbsoluteVolumeAudit() { return enableAbsoluteVolumeAudit; }
    public void setEnableAbsoluteVolumeAudit(boolean val) { this.enableAbsoluteVolumeAudit = val; }

    public boolean isDevModeBypassAllChecks() { return devModeBypassAllChecks; }
    public void setDevModeBypassAllChecks(boolean val) { this.devModeBypassAllChecks = val; }

    public String getOperatingMode() { return operatingMode; }
    public void setOperatingMode(String val) { this.operatingMode = val; }
}
