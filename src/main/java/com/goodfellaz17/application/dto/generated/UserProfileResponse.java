package com.goodfellaz17.application.dto.generated;

import com.goodfellaz17.domain.model.generated.UserEntity;
import com.goodfellaz17.domain.model.generated.UserStatus;
import com.goodfellaz17.domain.model.generated.UserTier;
import jakarta.annotation.Nullable;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * DTO: UserProfileResponse
 * 
 * Response payload for user profile details.
 * Excludes sensitive fields like password hash.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
public record UserProfileResponse(
    UUID id,
    String email,
    String tier,
    BigDecimal balance,
    @Nullable String apiKey,
    @Nullable String webhookUrl,
    Instant createdAt,
    @Nullable Instant lastLogin,
    String status,
    Boolean emailVerified,
    Boolean twoFactorEnabled,
    // Extra convenience fields
    String tierDisplayName,
    Boolean hasApiAccess
) implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Create from entity.
     */
    public static UserProfileResponse fromEntity(UserEntity entity) {
        String tierDisplay = switch (UserTier.valueOf(entity.getTier())) {
            case CONSUMER -> "Consumer";
            case RESELLER -> "Reseller";
            case AGENCY -> "Agency Partner";
        };
        
        return new UserProfileResponse(
            entity.getId(),
            entity.getEmail(),
            entity.getTier(),
            entity.getBalance(),
            maskApiKey(entity.getApiKey()),
            entity.getWebhookUrl(),
            entity.getCreatedAt(),
            entity.getLastLogin(),
            entity.getStatus(),
            entity.getEmailVerified(),
            entity.getTwoFactorEnabled(),
            tierDisplay,
            entity.getApiKey() != null && !entity.getApiKey().isBlank()
        );
    }
    
    /**
     * Mask API key for display (show only first/last 4 chars).
     */
    private static String maskApiKey(@Nullable String apiKey) {
        if (apiKey == null || apiKey.length() < 12) {
            return apiKey;
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
    
    /**
     * Get tier as enum.
     */
    public UserTier getTierEnum() {
        return UserTier.valueOf(tier);
    }
    
    /**
     * Get status as enum.
     */
    public UserStatus getStatusEnum() {
        return UserStatus.valueOf(status);
    }
    
    /**
     * Check if user can place orders.
     */
    public boolean canPlaceOrders() {
        return UserStatus.ACTIVE.name().equals(status) && balance.compareTo(BigDecimal.ZERO) > 0;
    }
}
