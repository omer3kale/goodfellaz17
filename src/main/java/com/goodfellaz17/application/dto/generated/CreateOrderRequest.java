package com.goodfellaz17.application.dto.generated;

import com.goodfellaz17.domain.model.generated.GeoProfile;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.*;

import java.io.Serializable;
import java.util.UUID;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * DTO: CreateOrderRequest
 * 
 * Request payload for creating a new order via REST API.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
public record CreateOrderRequest(
    
    @NotNull(message = "serviceId is required")
    UUID serviceId,
    
    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    @Max(value = 10000000, message = "quantity cannot exceed 10,000,000")
    Integer quantity,
    
    @NotNull(message = "targetUrl is required")
    @Size(max = 512, message = "targetUrl cannot exceed 512 characters")
    @Pattern(
        regexp = "^https://(open\\.)?spotify\\.com/.*$",
        message = "targetUrl must be a valid Spotify URL"
    )
    String targetUrl,
    
    @Nullable
    String geoProfile,
    
    @Nullable
    @DecimalMin(value = "0.1", message = "speedMultiplier must be at least 0.1")
    @DecimalMax(value = "5.0", message = "speedMultiplier cannot exceed 5.0")
    Double speedMultiplier

) implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Builder for CreateOrderRequest with defaults.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Get geo profile as enum, defaulting to WORLDWIDE if null.
     */
    public GeoProfile getGeoProfileEnum() {
        return geoProfile != null ? GeoProfile.fromValue(geoProfile) : GeoProfile.WORLDWIDE;
    }
    
    /**
     * Get speed multiplier with default.
     */
    public double getSpeedMultiplierOrDefault() {
        return speedMultiplier != null ? speedMultiplier : 1.0;
    }
    
    public static class Builder {
        private UUID serviceId;
        private Integer quantity;
        private String targetUrl;
        private String geoProfile;
        private Double speedMultiplier;
        
        public Builder serviceId(UUID serviceId) {
            this.serviceId = serviceId;
            return this;
        }
        
        public Builder quantity(Integer quantity) {
            this.quantity = quantity;
            return this;
        }
        
        public Builder targetUrl(String targetUrl) {
            this.targetUrl = targetUrl;
            return this;
        }
        
        public Builder geoProfile(String geoProfile) {
            this.geoProfile = geoProfile;
            return this;
        }
        
        public Builder geoProfile(GeoProfile geoProfile) {
            this.geoProfile = geoProfile != null ? geoProfile.name() : null;
            return this;
        }
        
        public Builder speedMultiplier(Double speedMultiplier) {
            this.speedMultiplier = speedMultiplier;
            return this;
        }
        
        public CreateOrderRequest build() {
            return new CreateOrderRequest(
                serviceId,
                quantity,
                targetUrl,
                geoProfile,
                speedMultiplier
            );
        }
    }
}
