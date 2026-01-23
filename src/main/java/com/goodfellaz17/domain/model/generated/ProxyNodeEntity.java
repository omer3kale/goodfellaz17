package com.goodfellaz17.domain.model.generated;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * Entity: ProxyNode
 * Table: proxy_nodes
 * 
 * Represents a proxy server in the distributed pool.
 * Tracks provider, tier, capacity, and health status.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
@Table("proxy_nodes")
public class ProxyNodeEntity implements Serializable, Persistable<UUID> {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    @NotNull
    private UUID id;
    
    /**
     * Cloud/VPS provider: VULTR, HETZNER, NETCUP, CONTABO, OVH, AWS, DIGITALOCEAN, LINODE
     * Stored as STRING in database for forward compatibility.
     */
    @NotNull
    @Column("provider")
    private String provider;
    
    @Nullable
    @Size(max = 64)
    @Column("provider_instance_id")
    private String providerInstanceId;
    
    @NotNull
    @Size(max = 45)
    @Column("public_ip")
    private String publicIp;
    
    @NotNull
    @Min(1)
    @Max(65535)
    @Column("port")
    private Integer port;
    
    @NotNull
    @Size(max = 50)
    @Column("region")
    private String region;
    
    @NotNull
    @Size(max = 2)
    @Column("country")
    private String country;
    
    @Nullable
    @Size(max = 100)
    @Column("city")
    private String city;
    
    /**
     * Proxy tier: DATACENTER, ISP, TOR, RESIDENTIAL, MOBILE
     * Higher tiers have lower ban rates but higher cost.
     */
    @NotNull
    @Column("tier")
    private String tier;
    
    @NotNull
    @Min(1)
    @Max(10000)
    @Column("capacity")
    private Integer capacity = 100;
    
    @NotNull
    @Min(0)
    @Column("current_load")
    private Integer currentLoad = 0;
    
    @NotNull
    @DecimalMin("0.00")
    @Column("cost_per_hour")
    private BigDecimal costPerHour = BigDecimal.ZERO;
    
    @Nullable
    @Size(max = 64)
    @Column("auth_username")
    private String authUsername;
    
    @Nullable
    @Size(max = 128)
    @Column("auth_password")
    private String authPassword;
    
    @NotNull
    @CreatedDate
    @Column("registered_at")
    private Instant registeredAt = Instant.now();
    
    @Nullable
    @Column("last_healthcheck")
    private Instant lastHealthcheck;
    
    /**
     * Node status: ONLINE, OFFLINE, MAINTENANCE, BANNED, RATE_LIMITED
     * Stored as STRING in database for forward compatibility.
     */
    @NotNull
    @Column("status")
    private String status = ProxyStatus.ONLINE.name();
    
    /**
     * Health state for selection algorithm: HEALTHY, DEGRADED, OFFLINE
     * - HEALTHY: successRate >= 0.85, preferred for task assignment
     * - DEGRADED: successRate >= 0.70 && < 0.85, fallback only (logged)
     * - OFFLINE: successRate < 0.70 or operational issues, never selected
     * 
     * Auto-updated by database trigger from proxy_metrics.success_rate
     */
    @NotNull
    @Column("health_state")
    private String healthState = ProxyHealthState.HEALTHY.name();
    
    @Nullable
    @Column("tags")
    private String tags; // JSON array
    
    @Transient
    private boolean isNew = true;
    
    // Default constructor for R2DBC
    public ProxyNodeEntity() {
        this.id = UUID.randomUUID();
        this.registeredAt = Instant.now();
        this.isNew = true;
    }
    
    @Override
    public boolean isNew() {
        return isNew;
    }
    
    public void markNotNew() {
        this.isNew = false;
    }
    
    // Builder pattern constructor
    private ProxyNodeEntity(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID();
        this.provider = Objects.requireNonNull(builder.provider, "provider is required");
        this.providerInstanceId = builder.providerInstanceId;
        this.publicIp = Objects.requireNonNull(builder.publicIp, "publicIp is required");
        this.port = Objects.requireNonNull(builder.port, "port is required");
        this.region = Objects.requireNonNull(builder.region, "region is required");
        this.country = Objects.requireNonNull(builder.country, "country is required");
        this.city = builder.city;
        this.tier = Objects.requireNonNull(builder.tier, "tier is required");
        this.capacity = builder.capacity != null ? builder.capacity : 100;
        this.currentLoad = builder.currentLoad != null ? builder.currentLoad : 0;
        this.costPerHour = builder.costPerHour != null ? builder.costPerHour : BigDecimal.ZERO;
        this.authUsername = builder.authUsername;
        this.authPassword = builder.authPassword;
        this.registeredAt = Instant.now();
        this.lastHealthcheck = builder.lastHealthcheck;
        this.status = builder.status != null ? builder.status : ProxyStatus.ONLINE.name();
        this.healthState = builder.healthState != null ? builder.healthState : ProxyHealthState.HEALTHY.name();
        this.tags = builder.tags;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public UUID getId() { return id; }
    public String getProvider() { return provider; }
    public ProxyProvider getProviderEnum() { return ProxyProvider.valueOf(provider); }
    @Nullable public String getProviderInstanceId() { return providerInstanceId; }
    public String getPublicIp() { return publicIp; }
    public Integer getPort() { return port; }
    public String getRegion() { return region; }
    public String getCountry() { return country; }
    @Nullable public String getCity() { return city; }
    public String getTier() { return tier; }
    public ProxyTier getTierEnum() { return ProxyTier.valueOf(tier); }
    public Integer getCapacity() { return capacity; }
    public Integer getCurrentLoad() { return currentLoad; }
    public BigDecimal getCostPerHour() { return costPerHour; }
    @Nullable public String getAuthUsername() { return authUsername; }
    @Nullable public String getAuthPassword() { return authPassword; }
    public Instant getRegisteredAt() { return registeredAt; }
    @Nullable public Instant getLastHealthcheck() { return lastHealthcheck; }
    public String getStatus() { return status; }
    public ProxyStatus getStatusEnum() { return ProxyStatus.valueOf(status); }
    public String getHealthState() { return healthState; }
    public ProxyHealthState getHealthStateEnum() { return ProxyHealthState.valueOf(healthState); }
    @Nullable public String getTags() { return tags; }
    
    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setProvider(String provider) { this.provider = provider; }
    public void setProvider(ProxyProvider provider) { this.provider = provider.name(); }
    public void setProviderInstanceId(@Nullable String providerInstanceId) { this.providerInstanceId = providerInstanceId; }
    public void setPublicIp(String publicIp) { this.publicIp = publicIp; }
    public void setPort(Integer port) { this.port = port; }
    public void setRegion(String region) { this.region = region; }
    public void setCountry(String country) { this.country = country; }
    public void setCity(@Nullable String city) { this.city = city; }
    public void setTier(String tier) { this.tier = tier; }
    public void setTier(ProxyTier tier) { this.tier = tier.name(); }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public void setCurrentLoad(Integer currentLoad) { this.currentLoad = currentLoad; }
    public void setCostPerHour(BigDecimal costPerHour) { this.costPerHour = costPerHour; }
    public void setAuthUsername(@Nullable String authUsername) { this.authUsername = authUsername; }
    public void setAuthPassword(@Nullable String authPassword) { this.authPassword = authPassword; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }
    public void setLastHealthcheck(@Nullable Instant lastHealthcheck) { this.lastHealthcheck = lastHealthcheck; }
    public void setStatus(String status) { this.status = status; }
    public void setStatus(ProxyStatus status) { this.status = status.name(); }
    public void setHealthState(String healthState) { this.healthState = healthState; }
    public void setHealthState(ProxyHealthState healthState) { this.healthState = healthState.name(); }
    public void setTags(@Nullable String tags) { this.tags = tags; }
    
    // Domain methods
    
    /**
     * Get proxy URL for connection
     */
    public String getProxyUrl() {
        if (authUsername != null && authPassword != null) {
            return String.format("http://%s:%s@%s:%d", authUsername, authPassword, publicIp, port);
        }
        return String.format("http://%s:%d", publicIp, port);
    }
    
    /**
     * Check if proxy has available capacity
     */
    public boolean hasCapacity() {
        return currentLoad < capacity;
    }
    
    /**
     * Get available capacity
     */
    public int getAvailableCapacity() {
        return Math.max(0, capacity - currentLoad);
    }
    
    /**
     * Increment load
     */
    public void incrementLoad() {
        this.currentLoad++;
    }
    
    /**
     * Decrement load
     */
    public void decrementLoad() {
        this.currentLoad = Math.max(0, this.currentLoad - 1);
    }
    
    /**
     * Mark healthcheck
     */
    public void recordHealthcheck() {
        this.lastHealthcheck = Instant.now();
    }
    
    /**
     * Check if proxy is available for use
     */
    public boolean isAvailable() {
        return ProxyStatus.ONLINE.name().equals(this.status) && hasCapacity();
    }
    
    /**
     * Check if proxy is selectable for task assignment.
     * Must be ONLINE and not in OFFLINE health state.
     */
    public boolean isSelectable() {
        return ProxyStatus.ONLINE.name().equals(this.status) 
            && !ProxyHealthState.OFFLINE.name().equals(this.healthState)
            && hasCapacity();
    }
    
    /**
     * Check if proxy is in preferred (HEALTHY) state.
     */
    public boolean isHealthy() {
        return ProxyHealthState.HEALTHY.name().equals(this.healthState);
    }
    
    /**
     * Check if proxy is degraded (should log when used).
     */
    public boolean isDegraded() {
        return ProxyHealthState.DEGRADED.name().equals(this.healthState);
    }
    
    /**
     * Calculate load percentage
     */
    public double getLoadPercent() {
        if (capacity == 0) return 100.0;
        return (double) currentLoad / capacity * 100.0;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProxyNodeEntity that = (ProxyNodeEntity) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "ProxyNodeEntity{" +
            "id=" + id +
            ", provider='" + provider + '\'' +
            ", publicIp='" + publicIp + '\'' +
            ", port=" + port +
            ", region='" + region + '\'' +
            ", tier='" + tier + '\'' +
            ", status='" + status + '\'' +
            ", healthState='" + healthState + '\'' +
            ", load=" + currentLoad + "/" + capacity +
            '}';
    }
    
    // Builder
    public static class Builder {
        private UUID id;
        private String provider;
        private String providerInstanceId;
        private String publicIp;
        private Integer port;
        private String region;
        private String country;
        private String city;
        private String tier;
        private Integer capacity;
        private Integer currentLoad;
        private BigDecimal costPerHour;
        private String authUsername;
        private String authPassword;
        private Instant lastHealthcheck;
        private String status;
        private String healthState;
        private String tags;
        
        public Builder id(UUID id) { this.id = id; return this; }
        public Builder provider(String provider) { this.provider = provider; return this; }
        public Builder provider(ProxyProvider provider) { this.provider = provider.name(); return this; }
        public Builder providerInstanceId(String providerInstanceId) { this.providerInstanceId = providerInstanceId; return this; }
        public Builder publicIp(String publicIp) { this.publicIp = publicIp; return this; }
        public Builder port(Integer port) { this.port = port; return this; }
        public Builder region(String region) { this.region = region; return this; }
        public Builder country(String country) { this.country = country; return this; }
        public Builder city(String city) { this.city = city; return this; }
        public Builder tier(String tier) { this.tier = tier; return this; }
        public Builder tier(ProxyTier tier) { this.tier = tier.name(); return this; }
        public Builder capacity(Integer capacity) { this.capacity = capacity; return this; }
        public Builder currentLoad(Integer currentLoad) { this.currentLoad = currentLoad; return this; }
        public Builder costPerHour(BigDecimal costPerHour) { this.costPerHour = costPerHour; return this; }
        public Builder authUsername(String authUsername) { this.authUsername = authUsername; return this; }
        public Builder authPassword(String authPassword) { this.authPassword = authPassword; return this; }
        public Builder lastHealthcheck(Instant lastHealthcheck) { this.lastHealthcheck = lastHealthcheck; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder status(ProxyStatus status) { this.status = status.name(); return this; }
        public Builder healthState(String healthState) { this.healthState = healthState; return this; }
        public Builder healthState(ProxyHealthState healthState) { this.healthState = healthState.name(); return this; }
        public Builder tags(String tags) { this.tags = tags; return this; }
        
        public Builder auth(String username, String password) {
            this.authUsername = username;
            this.authPassword = password;
            return this;
        }
        
        public ProxyNodeEntity build() {
            return new ProxyNodeEntity(this);
        }
    }
}
