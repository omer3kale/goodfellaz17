package com.goodfellaz.order.service;

import com.goodfellaz.order.model.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Test Cluster 3: OrderInvariants
 * 
 * 7 tests covering:
 * - Test 3.1: Quantity conservation (ordered == delivered + failed + pending)
 * - Test 3.2: Refund proportionality (unit price × failed count)
 * - Test 3.3: Balance conservation (income - refund = net)
 * - Test 3.4: Idempotent refund (second call unchanged)
 * - Test 3.5: Partial refund calculation (800 of 2000)
 * - Test 3.6: Zero refund for zero failed
 * - Test 3.7: Decimal precision to cents
 */
@DisplayName("Cluster 3: OrderInvariants Tests")
public class OrderInvariantsTest {

    // ========== TEST 3.1: Quantity Conservation ==========
    @Test
    @DisplayName("Test 3.1: Quantity Conservation (ordered == delivered + failed + pending)")
    void test_quantity_conservation_ordered_equals_delivered_plus_failed_plus_pending() {
        int ordered = 2000;
        int delivered = 800;
        int failedPermanent = 400;
        int pending = 800;

        boolean conserved = OrderInvariants.quantityConserved(ordered, delivered, failedPermanent, pending);

        assertTrue(conserved);
        assertEquals(ordered, delivered + failedPermanent + pending);
    }

    // ========== TEST 3.2: Refund Proportionality ==========
    @Test
    @DisplayName("Test 3.2: Refund Proportionality (unit price × failed count)")
    void test_refund_proportionality_unit_price_times_failed() {
        int failedCount = 800;
        BigDecimal unitPrice = BigDecimal.valueOf(0.002); // €0.002 per play

        BigDecimal refundAmount = OrderInvariants.refundAmount(failedCount, unitPrice);

        BigDecimal expected = BigDecimal.valueOf(1.60); // 800 × 0.002
        assertEquals(0, expected.compareTo(refundAmount));
    }

    // ========== TEST 3.3: Balance Conservation ==========
    @Test
    @DisplayName("Test 3.3: Balance Conservation (income - refund = net)")
    void test_balance_conservation_income_minus_refund_equals_net() {
        BigDecimal totalIncome = new BigDecimal("4.00");  // 2000 plays × €0.002
        BigDecimal refund = new BigDecimal("0.80");       // 400 failed × €0.002

        BigDecimal netBalance = totalIncome.subtract(refund);

        BigDecimal expected = new BigDecimal("3.20");
        assertEquals(0, expected.compareTo(netBalance));
    }

    // ========== TEST 3.4: Idempotent Refund ==========
    @Test
    @DisplayName("Test 3.4: Idempotent Refund (second call unchanged)")
    void test_idempotent_refund_second_call_unchanged() {
        // Arrange: order already refunded
        Order order = new Order(1L, 2000, 400, new BigDecimal("0.002"));
        order.setDelivered(1600);
        order.setPending(0);
        order.setRefundAmount(new BigDecimal("0.80"));
        order.setRefundIssuedAt(LocalDateTime.of(2026, 1, 20, 10, 0));
        order.setCurrentBalance(new BigDecimal("3.20"));

        // Capture state before second call
        BigDecimal balanceBefore = order.getCurrentBalance();
        BigDecimal refundBefore = order.getRefundAmount();
        LocalDateTime issuedAtBefore = order.getRefundIssuedAt();

        // Act: call refund again (should be no-op)
        RefundService refundService = new RefundService();
        refundService.issueRefund(order);

        // Assert: nothing changed
        assertEquals(0, balanceBefore.compareTo(order.getCurrentBalance()));
        assertEquals(0, refundBefore.compareTo(order.getRefundAmount()));
        assertEquals(issuedAtBefore, order.getRefundIssuedAt());
    }

    // ========== TEST 3.5: Partial Refund Calculation ==========
    @Test
    @DisplayName("Test 3.5: Partial Refund Calculation (800 of 2000)")
    void test_partial_refund_calculation_800_of_2000() {
        int totalOrdered = 2000;
        int failedCount = 800;
        BigDecimal unitPrice = new BigDecimal("0.002");

        // Calculate refund
        BigDecimal refundAmount = OrderInvariants.refundAmount(failedCount, unitPrice);

        // Calculate percentage
        double percentage = (failedCount / (double) totalOrdered) * 100;

        // Assert percentage is 40%
        assertEquals(40.0, percentage, 0.001);

        // Assert absolute refund: 800 × €0.002 = €1.60
        BigDecimal expectedRefund = new BigDecimal("1.60");
        assertEquals(0, expectedRefund.compareTo(refundAmount));
    }

    // ========== TEST 3.6: Zero Refund for Zero Failed ==========
    @Test
    @DisplayName("Test 3.6: Zero Refund for Zero Failed")
    void test_zero_refund_for_zero_failed() {
        int failedCount = 0;
        BigDecimal unitPrice = new BigDecimal("0.002");

        BigDecimal refundAmount = OrderInvariants.refundAmount(failedCount, unitPrice);

        assertEquals(0, BigDecimal.ZERO.compareTo(refundAmount));
    }

    // ========== TEST 3.7: Decimal Precision to Cents ==========
    @Test
    @DisplayName("Test 3.7: Decimal Precision to Cents")
    void test_decimal_precision_to_cents() {
        int failedCount = 333;
        BigDecimal unitPrice = new BigDecimal("0.001");

        BigDecimal refundAmount = OrderInvariants.refundAmount(failedCount, unitPrice);

        // Assert scale is exactly 2 (cents precision)
        assertEquals(2, refundAmount.scale());

        // Assert value rounds correctly: 333 × 0.001 = 0.333 → 0.33 (HALF_UP)
        assertEquals(0, new BigDecimal("0.33").compareTo(refundAmount));
    }
}
