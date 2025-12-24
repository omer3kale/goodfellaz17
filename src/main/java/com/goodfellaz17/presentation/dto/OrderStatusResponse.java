package com.goodfellaz17.presentation.dto;

import com.goodfellaz17.application.response.OrderResponse;

import java.util.UUID;

/**
 * DTO - Order Status Response (SMM Panel API v2).
 */
public record OrderStatusResponse(
        UUID order,
        String status,
        int quantity,
        int start_count,
        int remains,
        double progress
) {
    public static OrderStatusResponse from(OrderResponse order) {
        int remains = order.quantity() - order.delivered();
        return new OrderStatusResponse(
                order.id(),
                mapStatus(order.status().name()),
                order.quantity(),
                0,  // start_count (initial follower/play count before order)
                remains,
                order.progress()
        );
    }

    private static String mapStatus(String internalStatus) {
        return switch (internalStatus) {
            case "PENDING" -> "Pending";
            case "PROCESSING" -> "In progress";
            case "COMPLETED" -> "Completed";
            case "PARTIAL" -> "Partial";
            case "CANCELLED" -> "Canceled";
            case "REFUNDED" -> "Refunded";
            default -> internalStatus;
        };
    }
}
