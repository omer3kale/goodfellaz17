package com.goodfellaz17.application.response;

import com.goodfellaz17.domain.model.Order;
import com.goodfellaz17.domain.model.OrderStatus;

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
