package com.goodfellaz17.presentation.dto;

import java.util.UUID;

/**
 * DTO - Add Order Response (SMM Panel API v2).
 */
public record AddOrderResponse(
        boolean success,
        UUID order,
        String error
) {
    public static AddOrderResponse success(UUID orderId) {
        return new AddOrderResponse(true, orderId, null);
    }

    public static AddOrderResponse error(String message) {
        return new AddOrderResponse(false, null, message);
    }
}
