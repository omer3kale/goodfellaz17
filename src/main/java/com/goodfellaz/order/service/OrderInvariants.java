package com.goodfellaz.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Order invariant validation utilities.
 * Ensures financial correctness and quantity conservation.
 */
public class OrderInvariants {

    /**
     * Validates quantity conservation: all plays must be accounted for.
     * ordered == delivered + failedPermanent + pending
     *
     * @param ordered Total plays ordered
     * @param delivered Successfully delivered plays
     * @param failedPermanent Permanently failed plays
     * @param pending Plays still in progress
     * @return true if quantities are conserved
     */
    public static boolean quantityConserved(int ordered, int delivered, int failedPermanent, int pending) {
        return ordered == delivered + failedPermanent + pending;
    }

    /**
     * Calculates refund amount for failed plays.
     * refund = unitPrice × failedCount, rounded to 2 decimal places.
     *
     * @param failedCount Number of failed plays
     * @param unitPrice Price per play
     * @return Refund amount to 2 decimal places (cents)
     */
    public static BigDecimal refundAmount(int failedCount, BigDecimal unitPrice) {
        if (failedCount == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return unitPrice
                .multiply(BigDecimal.valueOf(failedCount))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
