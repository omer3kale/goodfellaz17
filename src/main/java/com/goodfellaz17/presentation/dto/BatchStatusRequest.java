package com.goodfellaz17.presentation.dto;

import java.util.List;
import java.util.UUID;

/**
 * DTO - Batch Status Request (SMM Panel API v2).
 */
public record BatchStatusRequest(
        String key,
        String action,  // Should be "status"
        List<UUID> orders
) {}
