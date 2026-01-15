package com.goodfellaz17.domain.model.generated;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * Entity: DeviceNode
 * Table: device_nodes
 * 
 * Represents a device fingerprint profile for anti-detection.
 * Maintains realistic device characteristics for Spotify bot sessions.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
@Table("device_nodes")
public class DeviceNodeEntity implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    @NotNull
    private UUID id;
    
    @NotNull
    @Size(max = 64)
    @Column("device_id")
    private String deviceId;
    
    @NotNull
    @Size(max = 512)
    @Column("user_agent")
    private String userAgent;
    
    @NotNull
    @Size(max = 64)
    @Column("platform")
    private String platform;
    
    @NotNull
    @Size(max = 32)
    @Column("browser")
    private String browser;
    
    @NotNull
    @Size(max = 32)
    @Column("browser_version")
    private String browserVersion;
    
    @NotNull
    @Size(max = 32)
    @Column("os")
    private String os;
    
    @NotNull
    @Size(max = 32)
    @Column("os_version")
    private String osVersion;
    
    @NotNull
    @Size(max = 16)
    @Column("screen_resolution")
    private String screenResolution;
    
    @NotNull
    @Size(max = 8)
    @Column("language")
    private String language;
    
    @NotNull
    @Size(max = 64)
    @Column("timezone")
    private String timezone;
    
    @Nullable
    @Column("canvas_hash")
    private String canvasHash;
    
    @Nullable
    @Column("webgl_hash")
    private String webglHash;
    
    @Nullable
    @Column("audio_hash")
    private String audioHash;
    
    @NotNull
    @Column("total_sessions")
    private Long totalSessions = 0L;
    
    @NotNull
    @Column("successful_sessions")
    private Long successfulSessions = 0L;
    
    @NotNull
    @Column("banned_sessions")
    private Long bannedSessions = 0L;
    
    @NotNull
    @Column("is_active")
    private Boolean isActive = true;
    
    @Nullable
    @Column("last_used")
    private Instant lastUsed;
    
    @NotNull
    @Column("created_at")
    private Instant createdAt = Instant.now();
    
    public DeviceNodeEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }
    
    private DeviceNodeEntity(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID();
        this.deviceId = Objects.requireNonNull(builder.deviceId);
        this.userAgent = Objects.requireNonNull(builder.userAgent);
        this.platform = Objects.requireNonNull(builder.platform);
        this.browser = Objects.requireNonNull(builder.browser);
        this.browserVersion = Objects.requireNonNull(builder.browserVersion);
        this.os = Objects.requireNonNull(builder.os);
        this.osVersion = Objects.requireNonNull(builder.osVersion);
        this.screenResolution = builder.screenResolution != null ? builder.screenResolution : "1920x1080";
        this.language = builder.language != null ? builder.language : "en-US";
        this.timezone = builder.timezone != null ? builder.timezone : "UTC";
        this.canvasHash = builder.canvasHash;
        this.webglHash = builder.webglHash;
        this.audioHash = builder.audioHash;
        this.totalSessions = 0L;
        this.successfulSessions = 0L;
        this.bannedSessions = 0L;
        this.isActive = true;
        this.createdAt = Instant.now();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public UUID getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public String getUserAgent() { return userAgent; }
    public String getPlatform() { return platform; }
    public String getBrowser() { return browser; }
    public String getBrowserVersion() { return browserVersion; }
    public String getOs() { return os; }
    public String getOsVersion() { return osVersion; }
    public String getScreenResolution() { return screenResolution; }
    public String getLanguage() { return language; }
    public String getTimezone() { return timezone; }
    @Nullable public String getCanvasHash() { return canvasHash; }
    @Nullable public String getWebglHash() { return webglHash; }
    @Nullable public String getAudioHash() { return audioHash; }
    public Long getTotalSessions() { return totalSessions; }
    public Long getSuccessfulSessions() { return successfulSessions; }
    public Long getBannedSessions() { return bannedSessions; }
    public Boolean getIsActive() { return isActive; }
    @Nullable public Instant getLastUsed() { return lastUsed; }
    public Instant getCreatedAt() { return createdAt; }
    
    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public void setPlatform(String platform) { this.platform = platform; }
    public void setBrowser(String browser) { this.browser = browser; }
    public void setBrowserVersion(String browserVersion) { this.browserVersion = browserVersion; }
    public void setOs(String os) { this.os = os; }
    public void setOsVersion(String osVersion) { this.osVersion = osVersion; }
    public void setScreenResolution(String screenResolution) { this.screenResolution = screenResolution; }
    public void setLanguage(String language) { this.language = language; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public void setCanvasHash(@Nullable String canvasHash) { this.canvasHash = canvasHash; }
    public void setWebglHash(@Nullable String webglHash) { this.webglHash = webglHash; }
    public void setAudioHash(@Nullable String audioHash) { this.audioHash = audioHash; }
    public void setTotalSessions(Long totalSessions) { this.totalSessions = totalSessions; }
    public void setSuccessfulSessions(Long successfulSessions) { this.successfulSessions = successfulSessions; }
    public void setBannedSessions(Long bannedSessions) { this.bannedSessions = bannedSessions; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public void setLastUsed(@Nullable Instant lastUsed) { this.lastUsed = lastUsed; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    // Domain methods
    
    /**
     * Record a session result.
     */
    public void recordSession(boolean success, boolean banned) {
        totalSessions++;
        if (success) successfulSessions++;
        if (banned) {
            bannedSessions++;
            // Auto-disable if ban rate too high
            if (getBanRate() > 0.2) {
                isActive = false;
            }
        }
        lastUsed = Instant.now();
    }
    
    /**
     * Get success rate.
     */
    public double getSuccessRate() {
        if (totalSessions == 0) return 1.0;
        return (double) successfulSessions / totalSessions;
    }
    
    /**
     * Get ban rate.
     */
    public double getBanRate() {
        if (totalSessions == 0) return 0.0;
        return (double) bannedSessions / totalSessions;
    }
    
    /**
     * Check if device profile is usable.
     */
    public boolean isUsable() {
        return isActive && getBanRate() < 0.1 && getSuccessRate() > 0.8;
    }
    
    /**
     * Get fingerprint uniqueness score.
     */
    public double getUniquenessScore() {
        double score = 0.0;
        if (canvasHash != null) score += 0.25;
        if (webglHash != null) score += 0.25;
        if (audioHash != null) score += 0.25;
        if (screenResolution != null && !screenResolution.equals("1920x1080")) score += 0.125;
        if (!language.equals("en-US")) score += 0.125;
        return score;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeviceNodeEntity that = (DeviceNodeEntity) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "DeviceNodeEntity{" +
            "deviceId='" + deviceId + '\'' +
            ", platform='" + platform + '\'' +
            ", browser='" + browser + '\'' +
            ", successRate=" + String.format("%.2f", getSuccessRate()) +
            ", isActive=" + isActive +
            '}';
    }
    
    public static class Builder {
        private UUID id;
        private String deviceId;
        private String userAgent;
        private String platform;
        private String browser;
        private String browserVersion;
        private String os;
        private String osVersion;
        private String screenResolution;
        private String language;
        private String timezone;
        private String canvasHash;
        private String webglHash;
        private String audioHash;
        
        public Builder id(UUID id) { this.id = id; return this; }
        public Builder deviceId(String deviceId) { this.deviceId = deviceId; return this; }
        public Builder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        public Builder platform(String platform) { this.platform = platform; return this; }
        public Builder browser(String browser) { this.browser = browser; return this; }
        public Builder browserVersion(String browserVersion) { this.browserVersion = browserVersion; return this; }
        public Builder os(String os) { this.os = os; return this; }
        public Builder osVersion(String osVersion) { this.osVersion = osVersion; return this; }
        public Builder screenResolution(String screenResolution) { this.screenResolution = screenResolution; return this; }
        public Builder language(String language) { this.language = language; return this; }
        public Builder timezone(String timezone) { this.timezone = timezone; return this; }
        public Builder canvasHash(String canvasHash) { this.canvasHash = canvasHash; return this; }
        public Builder webglHash(String webglHash) { this.webglHash = webglHash; return this; }
        public Builder audioHash(String audioHash) { this.audioHash = audioHash; return this; }
        
        public DeviceNodeEntity build() {
            return new DeviceNodeEntity(this);
        }
    }
}
