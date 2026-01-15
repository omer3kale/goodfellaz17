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
 * Entity: TorCircuit
 * Table: tor_circuits
 * 
 * Manages Tor circuit sessions for deep anonymization.
 * Tracks circuit identity and rotation schedules.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
@Table("tor_circuits")
public class TorCircuitEntity implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    @NotNull
    private UUID id;
    
    @NotNull
    @Size(max = 16)
    @Column("circuit_id")
    private String circuitId;
    
    @NotNull
    @Size(max = 64)
    @Column("fingerprint")
    private String fingerprint;
    
    @NotNull
    @Size(max = 45)
    @Column("exit_ip")
    private String exitIp;
    
    @NotNull
    @Size(max = 2)
    @Column("exit_country")
    private String exitCountry;
    
    @NotNull
    @Column("status")
    private String status = "ACTIVE";
    
    @NotNull
    @Column("bandwidth_kbps")
    private Integer bandwidthKbps = 0;
    
    @NotNull
    @Column("latency_ms")
    private Integer latencyMs = 0;
    
    @NotNull
    @Column("requests_served")
    private Long requestsServed = 0L;
    
    @NotNull
    @Column("bytes_transferred")
    private Long bytesTransferred = 0L;
    
    @NotNull
    @Column("is_stable")
    private Boolean isStable = false;
    
    @NotNull
    @Column("is_guard")
    private Boolean isGuard = false;
    
    @NotNull
    @Column("is_exit")
    private Boolean isExit = false;
    
    @NotNull
    @Column("created_at")
    private Instant createdAt = Instant.now();
    
    @Nullable
    @Column("expires_at")
    private Instant expiresAt;
    
    @Nullable
    @Column("last_rotated")
    private Instant lastRotated;
    
    public TorCircuitEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }
    
    private TorCircuitEntity(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID();
        this.circuitId = Objects.requireNonNull(builder.circuitId);
        this.fingerprint = Objects.requireNonNull(builder.fingerprint);
        this.exitIp = Objects.requireNonNull(builder.exitIp);
        this.exitCountry = Objects.requireNonNull(builder.exitCountry);
        this.status = builder.status != null ? builder.status : "ACTIVE";
        this.bandwidthKbps = builder.bandwidthKbps != null ? builder.bandwidthKbps : 0;
        this.latencyMs = builder.latencyMs != null ? builder.latencyMs : 0;
        this.requestsServed = 0L;
        this.bytesTransferred = 0L;
        this.isStable = builder.isStable != null ? builder.isStable : false;
        this.isGuard = builder.isGuard != null ? builder.isGuard : false;
        this.isExit = builder.isExit != null ? builder.isExit : false;
        this.createdAt = Instant.now();
        this.expiresAt = builder.expiresAt;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public UUID getId() { return id; }
    public String getCircuitId() { return circuitId; }
    public String getFingerprint() { return fingerprint; }
    public String getExitIp() { return exitIp; }
    public String getExitCountry() { return exitCountry; }
    public String getStatus() { return status; }
    public Integer getBandwidthKbps() { return bandwidthKbps; }
    public Integer getLatencyMs() { return latencyMs; }
    public Long getRequestsServed() { return requestsServed; }
    public Long getBytesTransferred() { return bytesTransferred; }
    public Boolean getIsStable() { return isStable; }
    public Boolean getIsGuard() { return isGuard; }
    public Boolean getIsExit() { return isExit; }
    public Instant getCreatedAt() { return createdAt; }
    @Nullable public Instant getExpiresAt() { return expiresAt; }
    @Nullable public Instant getLastRotated() { return lastRotated; }
    
    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setCircuitId(String circuitId) { this.circuitId = circuitId; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public void setExitIp(String exitIp) { this.exitIp = exitIp; }
    public void setExitCountry(String exitCountry) { this.exitCountry = exitCountry; }
    public void setStatus(String status) { this.status = status; }
    public void setBandwidthKbps(Integer bandwidthKbps) { this.bandwidthKbps = bandwidthKbps; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public void setRequestsServed(Long requestsServed) { this.requestsServed = requestsServed; }
    public void setBytesTransferred(Long bytesTransferred) { this.bytesTransferred = bytesTransferred; }
    public void setIsStable(Boolean isStable) { this.isStable = isStable; }
    public void setIsGuard(Boolean isGuard) { this.isGuard = isGuard; }
    public void setIsExit(Boolean isExit) { this.isExit = isExit; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setExpiresAt(@Nullable Instant expiresAt) { this.expiresAt = expiresAt; }
    public void setLastRotated(@Nullable Instant lastRotated) { this.lastRotated = lastRotated; }
    
    // Domain methods
    
    /**
     * Check if circuit is active.
     */
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
    
    /**
     * Check if circuit is expired.
     */
    public boolean isExpired() {
        if (expiresAt == null) return false;
        return Instant.now().isAfter(expiresAt);
    }
    
    /**
     * Check if circuit needs rotation.
     */
    public boolean needsRotation() {
        if (isExpired()) return true;
        if (lastRotated == null) return false;
        // Rotate every 10 minutes by default
        return Instant.now().isAfter(lastRotated.plusSeconds(600));
    }
    
    /**
     * Record a request through this circuit.
     */
    public void recordRequest(long bytes) {
        requestsServed++;
        bytesTransferred += bytes;
    }
    
    /**
     * Mark circuit as rotated.
     */
    public void markRotated(String newCircuitId, String newExitIp, String newExitCountry) {
        this.circuitId = newCircuitId;
        this.exitIp = newExitIp;
        this.exitCountry = newExitCountry;
        this.lastRotated = Instant.now();
        this.requestsServed = 0L;
        this.bytesTransferred = 0L;
    }
    
    /**
     * Close the circuit.
     */
    public void close() {
        this.status = "CLOSED";
    }
    
    /**
     * Get circuit quality score.
     */
    public double getQualityScore() {
        double score = 0.5;
        if (isStable) score += 0.2;
        if (bandwidthKbps > 1000) score += 0.15;
        if (latencyMs < 500) score += 0.15;
        return Math.min(1.0, score);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TorCircuitEntity that = (TorCircuitEntity) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "TorCircuitEntity{" +
            "circuitId='" + circuitId + '\'' +
            ", exitIp='" + exitIp + '\'' +
            ", exitCountry='" + exitCountry + '\'' +
            ", status='" + status + '\'' +
            ", isStable=" + isStable +
            '}';
    }
    
    public static class Builder {
        private UUID id;
        private String circuitId;
        private String fingerprint;
        private String exitIp;
        private String exitCountry;
        private String status;
        private Integer bandwidthKbps;
        private Integer latencyMs;
        private Boolean isStable;
        private Boolean isGuard;
        private Boolean isExit;
        private Instant expiresAt;
        
        public Builder id(UUID id) { this.id = id; return this; }
        public Builder circuitId(String circuitId) { this.circuitId = circuitId; return this; }
        public Builder fingerprint(String fingerprint) { this.fingerprint = fingerprint; return this; }
        public Builder exitIp(String exitIp) { this.exitIp = exitIp; return this; }
        public Builder exitCountry(String exitCountry) { this.exitCountry = exitCountry; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder bandwidthKbps(Integer bandwidthKbps) { this.bandwidthKbps = bandwidthKbps; return this; }
        public Builder latencyMs(Integer latencyMs) { this.latencyMs = latencyMs; return this; }
        public Builder isStable(Boolean isStable) { this.isStable = isStable; return this; }
        public Builder isGuard(Boolean isGuard) { this.isGuard = isGuard; return this; }
        public Builder isExit(Boolean isExit) { this.isExit = isExit; return this; }
        public Builder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }
        
        public TorCircuitEntity build() {
            return new TorCircuitEntity(this);
        }
    }
}
