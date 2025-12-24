package com.spotifybot.application.command;

import com.spotifybot.domain.model.GeoTarget;
import com.spotifybot.domain.model.SpeedTier;

/**
 * Application Command - Place new order.
 * 
 * Immutable command object for order creation use case.
 */
public record PlaceOrderCommand(
        String trackUrl,
        int quantity,
        GeoTarget geoTarget,
        SpeedTier speedTier
) {
}
