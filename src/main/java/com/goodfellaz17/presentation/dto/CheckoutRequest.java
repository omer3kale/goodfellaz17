package com.goodfellaz17.presentation.dto;

/**
 * Checkout Request DTO - Place order.
 * Matches botzzz773.pro exact field names.
 */
public record CheckoutRequest(
    String apiKey,
    int serviceId,        // DB service ID (integer)
    String link,          // Spotify track/playlist URL
    int quantity          // Order quantity
) {}
