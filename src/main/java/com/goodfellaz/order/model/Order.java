package com.goodfellaz.order.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order entity representing a Spotify play delivery order.
 * Tracks quantities, pricing, and refund state.
 */
public class Order {
    private Long id;
    private int ordered;            // Total plays ordered
    private int delivered;          // Successfully delivered plays
    private int failedPermanent;    // Permanently failed plays (eligible for refund)
    private int pending;            // Still in progress
    private BigDecimal unitPrice;   // Price per play (e.g., €0.002)
    private BigDecimal refundAmount;
    private LocalDateTime refundIssuedAt;
    private BigDecimal currentBalance;

    public Order() {}

    public Order(Long id, int ordered, int failedPermanent, BigDecimal unitPrice) {
        this.id = id;
        this.ordered = ordered;
        this.failedPermanent = failedPermanent;
        this.unitPrice = unitPrice;
        this.delivered = 0;
        this.pending = ordered - failedPermanent;
        this.currentBalance = unitPrice.multiply(BigDecimal.valueOf(ordered));
        this.refundAmount = BigDecimal.ZERO;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public int getOrdered() { return ordered; }
    public void setOrdered(int ordered) { this.ordered = ordered; }

    public int getDelivered() { return delivered; }
    public void setDelivered(int delivered) { this.delivered = delivered; }

    public int getFailedPermanent() { return failedPermanent; }
    public void setFailedPermanent(int failedPermanent) { this.failedPermanent = failedPermanent; }

    public int getPending() { return pending; }
    public void setPending(int pending) { this.pending = pending; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }

    public LocalDateTime getRefundIssuedAt() { return refundIssuedAt; }
    public void setRefundIssuedAt(LocalDateTime refundIssuedAt) { this.refundIssuedAt = refundIssuedAt; }

    public BigDecimal getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }
}
