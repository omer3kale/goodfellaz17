package com.goodfellaz.order.service;

import com.goodfellaz.order.model.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Service for processing refunds with idempotence guarantees.
 * A refund can only be issued once per order.
 */
public class RefundService {

    /**
     * Issues a refund for failed plays in an order.
     * Idempotent: if refund already issued, does nothing.
     *
     * @param order The order to refund
     */
    public void issueRefund(Order order) {
        // Idempotence check: if already refunded, skip
        if (order.getRefundIssuedAt() != null) {
            return;
        }

        // Calculate refund
        BigDecimal refund = OrderInvariants.refundAmount(
                order.getFailedPermanent(),
                order.getUnitPrice()
        );

        // Apply refund
        order.setRefundAmount(refund);
        order.setRefundIssuedAt(LocalDateTime.now());
        order.setCurrentBalance(order.getCurrentBalance().subtract(refund));
    }
}
