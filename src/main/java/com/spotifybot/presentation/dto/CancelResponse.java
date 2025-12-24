package com.spotifybot.presentation.dto;

/**
 * DTO - Cancel Order Response.
 */
public record CancelResponse(boolean isSuccess, String errorMessage) {
    public static CancelResponse success() {
        return new CancelResponse(true, null);
    }

    public static CancelResponse error(String message) {
        return new CancelResponse(false, message);
    }
}
