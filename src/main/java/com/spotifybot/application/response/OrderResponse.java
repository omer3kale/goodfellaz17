package com.spotifybot.application.response;

import com.spotifybot.domain.model.Order;
import com.spotifybot.domain.model.OrderStatus;

import java.util.UUID;

/**
 * Application Response - Order data transfer.
 */
public record OrderResponse(
        UUID id,
        String trackUrl,
        int quantity,
        int delivered,
        OrderStatus status,
        double progress
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getTrackUrl(),
                order.getQuantity(),
                order.getDelivered(),
                order.getStatus(),
                order.getProgress()
        );
    }
}
