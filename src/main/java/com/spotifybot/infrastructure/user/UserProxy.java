package com.spotifybot.infrastructure.user;

import com.spotifybot.domain.model.GeoTarget;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Model - User Proxy from botzzz773.pro.
 * 
 * Each botzzz773.pro user becomes a proxy node:
 * - Their IP address = residential proxy
 * - Their Spotify account = premium account
 * - Their browser = execution environment
 * 
 * Cost: $0 (vs $50/mo BrightData)
 * Scale: 1000+ users = infinite proxies
 */
public class UserProxy {

    private final String id;
    private final String userId;
    private final String ipAddress;
    private final String userAgent;
    private final GeoTarget geoTarget;
    private final boolean hasSpotifyPremium;
    private volatile UserStatus status;
    private volatile Instant lastSeen;
    private volatile int tasksCompleted;
    private volatile double commissionEarned;

    public UserProxy(String userId, String ipAddress, String userAgent, 
                     GeoTarget geoTarget, boolean hasSpotifyPremium) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.geoTarget = geoTarget;
        this.hasSpotifyPremium = hasSpotifyPremium;
        this.status = UserStatus.AVAILABLE;
        this.lastSeen = Instant.now();
        this.tasksCompleted = 0;
        this.commissionEarned = 0.0;
    }

    public static UserProxyBuilder builder() {
        return new UserProxyBuilder();
    }

    // === Business Logic ===

    public boolean isHealthy() {
        return status == UserStatus.AVAILABLE 
            && lastSeen.isAfter(Instant.now().minusSeconds(300)); // 5 min timeout
    }

    public boolean matchesGeo(GeoTarget target) {
        return target == GeoTarget.WORLDWIDE || this.geoTarget == target;
    }

    public void assignTask() {
        this.status = UserStatus.BUSY;
    }

    public void completeTask(double commission) {
        this.status = UserStatus.AVAILABLE;
        this.tasksCompleted++;
        this.commissionEarned += commission;
        this.lastSeen = Instant.now();
    }

    public void heartbeat() {
        this.lastSeen = Instant.now();
    }

    public void disconnect() {
        this.status = UserStatus.OFFLINE;
    }

    // === Getters ===

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public GeoTarget getGeoTarget() { return geoTarget; }
    public boolean hasSpotifyPremium() { return hasSpotifyPremium; }
    public UserStatus getStatus() { return status; }
    public Instant getLastSeen() { return lastSeen; }
    public int getTasksCompleted() { return tasksCompleted; }
    public double getCommissionEarned() { return commissionEarned; }

    // === Builder ===

    public static class UserProxyBuilder {
        private String userId;
        private String ipAddress;
        private String userAgent;
        private GeoTarget geoTarget;
        private boolean hasSpotifyPremium;

        public UserProxyBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public UserProxyBuilder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public UserProxyBuilder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public UserProxyBuilder geoTarget(GeoTarget geoTarget) {
            this.geoTarget = geoTarget;
            return this;
        }

        public UserProxyBuilder hasSpotifyPremium(boolean hasSpotifyPremium) {
            this.hasSpotifyPremium = hasSpotifyPremium;
            return this;
        }

        public UserProxy build() {
            return new UserProxy(userId, ipAddress, userAgent, geoTarget, hasSpotifyPremium);
        }
    }
}
