package com.goodfellaz17.domain.model.generated;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
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
 * Entity: ProxyMetrics
 * Table: proxy_metrics
 * 
 * Real-time rolling metrics for proxy health monitoring.
 * Used by HybridProxyRouter for selection decisions.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
@Table("proxy_metrics")
public class ProxyMetricsEntity implements Serializable, Persistable<UUID> {
    
    private static final long serialVersionUID = 1L;
    
    @Transient
    private boolean isNew = true;
    
    @Id
    @NotNull
    private UUID id;
    
    @NotNull
    @Column("proxy_node_id")
    private UUID proxyNodeId;
    
    @NotNull
    @Column("total_requests")
    private Long totalRequests = 0L;
    
    @NotNull
    @Column("successful_requests")
    private Long successfulRequests = 0L;
    
    @NotNull
    @Column("failed_requests")
    private Long failedRequests = 0L;
    
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Column("success_rate")
    private Double successRate = 1.0;
    
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Column("ban_rate")
    private Double banRate = 0.0;
    
    @NotNull
    @Column("latency_p50")
    private Integer latencyP50 = 0;
    
    @NotNull
    @Column("latency_p95")
    private Integer latencyP95 = 0;
    
    @NotNull
    @Column("latency_p99")
    private Integer latencyP99 = 0;
    
    @NotNull
    @Column("active_connections")
    private Integer activeConnections = 0;
    
    @NotNull
    @Column("peak_connections")
    private Integer peakConnections = 0;
    
    @Nullable
    @Column("error_codes")
    private String errorCodes; // JSON object {"401": count, "403": count}
    
    @NotNull
    @Column("bytes_transferred")
    private Long bytesTransferred = 0L;
    
    @NotNull
    @Column("estimated_cost")
    private BigDecimal estimatedCost = BigDecimal.ZERO;
    
    @NotNull
    @Column("last_updated")
    private Instant lastUpdated = Instant.now();
    
    @NotNull
    @Column("window_start")
    private Instant windowStart = Instant.now();
    
    public ProxyMetricsEntity() {
        this.id = UUID.randomUUID();
        this.lastUpdated = Instant.now();
        this.windowStart = Instant.now();
        this.isNew = true;
    }
    
    public ProxyMetricsEntity(UUID proxyNodeId) {
        this();
        this.proxyNodeId = proxyNodeId;
    }
    
    @Override
    public boolean isNew() {
        return isNew;
    }
    
    public void markNotNew() {
        this.isNew = false;
    }
    
    // Getters
    public UUID getId() { return id; }
    public UUID getProxyNodeId() { return proxyNodeId; }
    public Long getTotalRequests() { return totalRequests; }
    public Long getSuccessfulRequests() { return successfulRequests; }
    public Long getFailedRequests() { return failedRequests; }
    public Double getSuccessRate() { return successRate; }
    public Double getBanRate() { return banRate; }
    public Integer getLatencyP50() { return latencyP50; }
    public Integer getLatencyP95() { return latencyP95; }
    public Integer getLatencyP99() { return latencyP99; }
    public Integer getActiveConnections() { return activeConnections; }
    public Integer getPeakConnections() { return peakConnections; }
    @Nullable public String getErrorCodes() { return errorCodes; }
    public Long getBytesTransferred() { return bytesTransferred; }
    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public Instant getLastUpdated() { return lastUpdated; }
    public Instant getWindowStart() { return windowStart; }
    
    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setProxyNodeId(UUID proxyNodeId) { this.proxyNodeId = proxyNodeId; }
    public void setTotalRequests(Long totalRequests) { this.totalRequests = totalRequests; }
    public void setSuccessfulRequests(Long successfulRequests) { this.successfulRequests = successfulRequests; }
    public void setFailedRequests(Long failedRequests) { this.failedRequests = failedRequests; }
    public void setSuccessRate(Double successRate) { this.successRate = successRate; }
    public void setBanRate(Double banRate) { this.banRate = banRate; }
    public void setLatencyP50(Integer latencyP50) { this.latencyP50 = latencyP50; }
    public void setLatencyP95(Integer latencyP95) { this.latencyP95 = latencyP95; }
    public void setLatencyP99(Integer latencyP99) { this.latencyP99 = latencyP99; }
    public void setActiveConnections(Integer activeConnections) { this.activeConnections = activeConnections; }
    public void setPeakConnections(Integer peakConnections) { this.peakConnections = peakConnections; }
    public void setErrorCodes(@Nullable String errorCodes) { this.errorCodes = errorCodes; }
    public void setBytesTransferred(Long bytesTransferred) { this.bytesTransferred = bytesTransferred; }
    public void setEstimatedCost(BigDecimal estimatedCost) { this.estimatedCost = estimatedCost; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }
    
    // Domain methods
    
    /**
     * Record a successful request.
     */
    public void recordSuccess(int latencyMs, long bytes) {
        totalRequests++;
        successfulRequests++;
        bytesTransferred += bytes;
        recalculateRates();
        lastUpdated = Instant.now();
    }
    
    /**
     * Record a failed request.
     */
    public void recordFailure(int latencyMs, int errorCode) {
        totalRequests++;
        failedRequests++;
        if (errorCode == 403 || errorCode == 429) {
            // Count as potential ban
            recalculateBanRate(errorCode);
        }
        recalculateRates();
        lastUpdated = Instant.now();
    }
    
    private void recalculateRates() {
        if (totalRequests > 0) {
            successRate = (double) successfulRequests / totalRequests;
        }
    }
    
    private void recalculateBanRate(int errorCode) {
        // Simplified ban rate calculation
        if (errorCode == 403 || errorCode == 429) {
            banRate = Math.min(1.0, banRate + 0.01);
        }
    }
    
    /**
     * Check if this proxy is healthy.
     */
    public boolean isHealthy() {
        return successRate >= 0.7 && banRate < 0.1 && latencyP95 < 5000;
    }
    
    /**
     * Reset metrics for new window.
     */
    public void resetWindow() {
        totalRequests = 0L;
        successfulRequests = 0L;
        failedRequests = 0L;
        successRate = 1.0;
        banRate = 0.0;
        bytesTransferred = 0L;
        windowStart = Instant.now();
        lastUpdated = Instant.now();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProxyMetricsEntity that = (ProxyMetricsEntity) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "ProxyMetricsEntity{" +
            "proxyNodeId=" + proxyNodeId +
            ", totalRequests=" + totalRequests +
            ", successRate=" + String.format("%.2f", successRate) +
            ", banRate=" + String.format("%.2f", banRate) +
            ", latencyP95=" + latencyP95 +
            '}';
    }
}
