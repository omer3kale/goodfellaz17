package com.goodfellaz17.application.dto.generated;

import com.goodfellaz17.domain.model.generated.GeoProfile;
import com.goodfellaz17.domain.model.generated.OrderEntity;
import com.goodfellaz17.domain.model.generated.OrderStatus;
import jakarta.annotation.Nullable;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * DTO: OrderResponse
 * 
 * Response payload for order details via REST API.
 * Includes computed fields like progress percentage.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
public record OrderResponse(
    UUID id,
    UUID serviceId,
    String serviceName,
    Integer quantity,
    Integer delivered,
    String targetUrl,
    String geoProfile,
    String status,
    BigDecimal cost,
    BigDecimal refundAmount,
    @Nullable Integer startCount,
    @Nullable Integer currentCount,
    Instant createdAt,
    @Nullable Instant startedAt,
    @Nullable Instant completedAt,
    @Nullable Instant estimatedCompletionAt,
    @Nullable String failureReason,
    // Computed fields
    Double progressPercent,
    Boolean isTerminal
) implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Create from entity with service name lookup.
     */
    public static OrderResponse fromEntity(OrderEntity entity, String serviceName) {
        double progress = entity.getQuantity() > 0 
            ? (double) entity.getDelivered() / entity.getQuantity() * 100.0 
            : 0.0;
        
        boolean terminal = OrderStatus.valueOf(entity.getStatus()).isTerminal();
        
        return new OrderResponse(
            entity.getId(),
            entity.getServiceId(),
            serviceName,
            entity.getQuantity(),
            entity.getDelivered(),
            entity.getTargetUrl(),
            entity.getGeoProfile(),
            entity.getStatus(),
            entity.getCost(),
            entity.getRefundAmount(),
            entity.getStartCount(),
            entity.getCurrentCount(),
            entity.getCreatedAt(),
            entity.getStartedAt(),
            entity.getCompletedAt(),
            entity.getEstimatedCompletionAt(),
            entity.getFailureReason(),
            Math.round(progress * 100.0) / 100.0,
            terminal
        );
    }
    
    /**
     * Get status as enum.
     */
    public OrderStatus getStatusEnum() {
        return OrderStatus.valueOf(status);
    }
    
    /**
     * Get geo profile as enum.
     */
    public GeoProfile getGeoProfileEnum() {
        return GeoProfile.valueOf(geoProfile);
    }
    
    /**
     * Check if order can be cancelled.
     */
    public boolean isCancellable() {
        OrderStatus s = getStatusEnum();
        return s == OrderStatus.PENDING || s == OrderStatus.VALIDATING;
    }
}
