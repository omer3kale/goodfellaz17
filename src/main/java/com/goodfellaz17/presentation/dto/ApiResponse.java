package com.goodfellaz17.presentation.dto;

import java.util.UUID;

/**
 * API Response DTO - Standard response wrapper.
 */
public record ApiResponse(
    boolean success,
    String message,
    Object data
) {
    public static ApiResponse success(Object data) {
        return new ApiResponse(true, "Success", data);
    }

    public static ApiResponse success(String message, Object data) {
        return new ApiResponse(true, message, data);
    }

    public static ApiResponse error(String message) {
        return new ApiResponse(false, message, null);
    }

    public static ApiResponse orderCreated(UUID orderId) {
        return new ApiResponse(true, "Order created successfully", orderId);
    }
}
