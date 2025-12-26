package com.goodfellaz17.presentation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Order Summary DTO - Customer order table row.
 * Matches botzzz773.pro exact field names.
 */
public record OrderSummary(
    UUID id,
    String serviceName,
    String link,
    int quantity,
    BigDecimal charged,
    String status,
    int progress,
    int deliveredQuantity,
    String eta,          // "8h23m" or "✅"
    Instant createdAt,
    Instant updatedAt
) {
    /**
     * Calculate ETA string from timestamps and delivery hours.
     */
    public static String calculateEta(Instant startedAt, int deliveryHours, String status) {
        if ("Completed".equals(status)) {
            return "✅";
        }
        if ("Failed".equals(status)) {
            return "❌";
        }
        if ("Refunded".equals(status)) {
            return "💸";
        }
        if (startedAt == null) {
            return deliveryHours + "h";
        }
        
        Instant eta = startedAt.plusSeconds(deliveryHours * 3600L);
        long remainingSeconds = eta.getEpochSecond() - Instant.now().getEpochSecond();
        
        if (remainingSeconds <= 0) {
            return "Soon™";
        }
        
        long hours = remainingSeconds / 3600;
        long minutes = (remainingSeconds % 3600) / 60;
        
        if (hours > 0) {
            return hours + "h" + minutes + "m";
        }
        return minutes + "m";
    }

    /**
     * Get progress bar string (for HTML rendering).
     */
    public String progressBar() {
        int filled = progress / 10;
        int empty = 10 - filled;
        return "█".repeat(filled) + "▁".repeat(empty);
    }

    /**
     * Get short order ID for display.
     */
    public String shortId() {
        return id.toString().substring(0, 8) + "...";
    }
}
