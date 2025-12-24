package com.goodfellaz17.presentation.dto;

import java.util.UUID;

/**
 * DTO - Cancel Order Request.
 */
public record CancelRequest(String key, UUID order) {}
