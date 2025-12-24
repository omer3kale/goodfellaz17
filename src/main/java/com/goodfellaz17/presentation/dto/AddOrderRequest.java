package com.goodfellaz17.presentation.dto;

import com.goodfellaz17.application.command.PlaceOrderCommand;
import com.goodfellaz17.domain.model.GeoTarget;
import com.goodfellaz17.domain.model.SpeedTier;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO - Add Order Request (SMM Panel API v2).
 * 
 * Maps Perfect Panel API format to internal command.
 */
public record AddOrderRequest(
        @NotBlank(message = "API key is required")
        String key,

        @NotNull(message = "Action is required")
        String action,  // Should be "add"

        @NotNull(message = "Service ID is required")
        Integer service,

        @NotBlank(message = "Link (track URL) is required")
        String link,

        @Min(value = 100, message = "Minimum quantity is 100")
        int quantity
) {
    /**
     * Maps SMM service IDs to internal domain model.
     * 
     * Service catalog:
     * - 1: Spotify Plays (Worldwide, Normal)
     * - 2: Spotify Plays (USA, Normal)
     * - 3: Spotify Plays (EU, Normal)
     * - 4: Spotify Premium Plays (USA, Fast)
     * - 5: Spotify VIP Plays (USA, VIP)
     */
    public PlaceOrderCommand toCommand() {
        GeoTarget geo = switch (service) {
            case 2, 4, 5 -> GeoTarget.USA;
            case 3 -> GeoTarget.EU;
            default -> GeoTarget.WORLDWIDE;
        };

        SpeedTier speed = switch (service) {
            case 4 -> SpeedTier.FAST;
            case 5 -> SpeedTier.VIP;
            default -> SpeedTier.NORMAL;
        };

        return new PlaceOrderCommand(link, quantity, geo, speed);
    }
}
