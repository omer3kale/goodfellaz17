package com.goodfellaz17.application.dto.generated;

import com.goodfellaz17.domain.model.generated.ProxyNodeEntity;
import com.goodfellaz17.domain.model.generated.ProxyStatus;
import com.goodfellaz17.domain.model.generated.ProxyTier;
import jakarta.annotation.Nullable;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * DTO: ProxyNodeStatus
 * 
 * Response payload for proxy node status in admin dashboards.
 * Includes real-time metrics for monitoring.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
public record ProxyNodeStatus(
    UUID id,
    String provider,
    String region,
    String country,
    String tier,
    String status,
    Integer currentLoad,
    Integer capacity,
    Double successRate,
    Integer latencyP95,
    @Nullable Instant lastHealthcheck,
    // Computed fields
    Double loadPercent,
    Boolean isHealthy,
    Integer availableCapacity
) implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Create from entity with metrics.
     */
    public static ProxyNodeStatus fromEntity(
        ProxyNodeEntity entity, 
        double successRate, 
        int latencyP95
    ) {
        double loadPct = entity.getCapacity() > 0 
            ? (double) entity.getCurrentLoad() / entity.getCapacity() * 100.0 
            : 0.0;
        
        boolean healthy = ProxyStatus.ONLINE.name().equals(entity.getStatus()) 
            && successRate >= 0.8 
            && latencyP95 < 5000;
        
        return new ProxyNodeStatus(
            entity.getId(),
            entity.getProvider(),
            entity.getRegion(),
            entity.getCountry(),
            entity.getTier(),
            entity.getStatus(),
            entity.getCurrentLoad(),
            entity.getCapacity(),
            Math.round(successRate * 1000.0) / 1000.0,
            latencyP95,
            entity.getLastHealthcheck(),
            Math.round(loadPct * 10.0) / 10.0,
            healthy,
            Math.max(0, entity.getCapacity() - entity.getCurrentLoad())
        );
    }
    
    /**
     * Create from entity with default metrics.
     */
    public static ProxyNodeStatus fromEntity(ProxyNodeEntity entity) {
        return fromEntity(entity, 1.0, 0);
    }
    
    /**
     * Get tier as enum.
     */
    public ProxyTier getTierEnum() {
        return ProxyTier.valueOf(tier);
    }
    
    /**
     * Get status as enum.
     */
    public ProxyStatus getStatusEnum() {
        return ProxyStatus.valueOf(status);
    }
    
    /**
     * Check if proxy can accept new connections.
     */
    public boolean canAcceptConnections() {
        return isHealthy && availableCapacity > 0;
    }
}
