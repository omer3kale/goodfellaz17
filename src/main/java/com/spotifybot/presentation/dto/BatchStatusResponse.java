package com.spotifybot.presentation.dto;

import java.util.List;

/**
 * DTO - Batch Status Response (SMM Panel API v2).
 */
public record BatchStatusResponse(List<OrderStatusResponse> orders) {}
