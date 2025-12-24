package com.goodfellaz17.domain.model;

/**
 * Domain Value Object - Order lifecycle status.
 */
public enum OrderStatus {
    PENDING,        // Order created, awaiting processing
    PROCESSING,     // Bots actively streaming
    COMPLETED,      // All streams delivered
    PARTIAL,        // Partially delivered (capacity issue)
    CANCELLED,      // User cancelled
    REFUNDED        // Refund processed
}
