package com.goodfellaz17.application.command;

import com.goodfellaz17.domain.model.GeoTarget;
import com.goodfellaz17.domain.model.SpeedTier;

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
