package com.goodfellaz17.presentation.dto;

import java.math.BigDecimal;

/**
 * Service DTO - Service card data.
 * Matches botzzz773.pro exact field names.
 */
public record ServiceDto(
    int id,
    String serviceId,
    String name,
    String category,
    BigDecimal pricePer1000,
    int deliveryHours,
    int minQuantity,
    int maxQuantity,
    String neonColor,
    String speedTier,
    String geoTarget,
    String description
) {
    /**
     * Get tier badge text.
     */
    public String tierBadge() {
        return switch (speedTier) {
            case "LIGHTNING" -> "⚡ Lightning";
            case "STEALTH" -> "🥷 Stealth";
            case "ULTRA" -> "💎 Ultra";
            default -> "📦 Standard";
        };
    }

    /**
     * Get delivery time display.
     */
    public String deliveryDisplay() {
        if (deliveryHours <= 12) return "⚡ " + deliveryHours + "h";
        if (deliveryHours <= 48) return "🕐 " + deliveryHours + "h";
        return "🌙 " + deliveryHours + "h";
    }
}
