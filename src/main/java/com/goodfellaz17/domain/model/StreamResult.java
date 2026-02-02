package com.goodfellaz17.domain.model;

import java.time.LocalDateTime;

/**
 * Domain record representing the result of a stream task.
 */
public record StreamResult(
    Long id,
    String proxyId,
    String trackId,
    Integer duration,
    LocalDateTime completedAt,
    String status,
    String errorMessage
) {
    public static StreamResult success(String proxyId, String trackId, Integer duration) {
        return new StreamResult(null, proxyId, trackId, duration, LocalDateTime.now(), "SUCCESS", null);
    }

    public static StreamResult failure(String proxyId, String trackId, String error) {
        return new StreamResult(null, proxyId, trackId, 0, LocalDateTime.now(), "FAILED", error);
    }
}
