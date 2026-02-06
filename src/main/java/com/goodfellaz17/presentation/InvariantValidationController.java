package com.goodfellaz17.presentation;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.goodfellaz17.application.invariants.OrderInvariantValidator_Thesis;
import com.goodfellaz17.application.invariants.OrderInvariantValidator_Thesis.InvariantViolation;

import reactor.core.publisher.Mono;

/**
 * Admin endpoints for invariant validation.
 * Use these to verify system health and debug issues.
 *
 * Endpoints:
 * - GET /api/admin/invariants/orders/{orderId} - Validate single order
 * - GET /api/admin/invariants/all - Validate all orders (expensive!)
 * - GET /api/admin/invariants/orphans - Check for orphaned tasks
 *
 * @author goodfellaz17
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/admin/invariants")
public class InvariantValidationController {

    private static final Logger log = LoggerFactory.getLogger(InvariantValidationController.class);

    private final OrderInvariantValidator_Thesis invariantValidator;

    public InvariantValidationController(OrderInvariantValidator_Thesis invariantValidator) {
        this.invariantValidator = invariantValidator;
    }

    /**
     * Validate all invariants for a specific order.
     */
    @GetMapping("/orders/{orderId}")
    public Mono<ResponseEntity<ValidationResponse>> validateOrder(@PathVariable UUID orderId) {
        log.info("Validating invariants for order: {}", orderId);

        return invariantValidator.validateOrder(orderId)
            .map(result -> {
                ValidationResponse response = new ValidationResponse(
                    result.passed(),
                    result.orderId() != null ? result.orderId().toString() : null,
                    result.orderType(),
                    result.violations(),
                    result.errorMessage()
                );

                if (result.passed()) {
                    return ResponseEntity.ok(response);
                } else {
                    return ResponseEntity.status(422).body(response);
                }
            });
    }

    /**
     * Validate all orders in the system.
     * WARNING: This is expensive and should only be used for debugging.
     */
    @GetMapping("/all")
    public Mono<ResponseEntity<GlobalValidationResponse>> validateAllOrders() {
        log.warn("Running global invariant validation - this may be slow!");

        return invariantValidator.validateAllOrders()
            .map(result -> {
                GlobalValidationResponse response = new GlobalValidationResponse(
                    result.allPassed(),
                    result.passedOrders(),
                    result.failedOrders(),
                    result.failures().stream()
                        .map(f -> new FailedOrderSummary(
                            f.orderId().toString(),
                            f.result().orderType(),
                            f.result().violations().stream()
                                .map(v -> v.code() + ": " + v.details())
                                .toList()
                        ))
                        .toList()
                );

                if (result.allPassed()) {
                    return ResponseEntity.ok(response);
                } else {
                    return ResponseEntity.status(422).body(response);
                }
            });
    }

    /**
     * Quick health check: are there any orphaned EXECUTING tasks?
     */
    @GetMapping("/orphans")
    public Mono<ResponseEntity<OrphanCheckResponse>> checkOrphans() {
        return invariantValidator.checkForOrphanedTasks()
            .map(result -> {
                OrphanCheckResponse response = new OrphanCheckResponse(
                    result.noOrphans(),
                    result.orphanCount(),
                    result.orphanTaskIds().stream().map(UUID::toString).toList()
                );

                if (result.noOrphans()) {
                    return ResponseEntity.ok(response);
                } else {
                    return ResponseEntity.status(422).body(response);
                }
            });
    }

    // =========================================================================
    // RESPONSE TYPES
    // =========================================================================

    public record ValidationResponse(
        boolean passed,
        String orderId,
        String orderType,
        java.util.List<InvariantViolation> violations,
        String errorMessage
    ) {}

    public record GlobalValidationResponse(
        boolean allPassed,
        long passedOrders,
        long failedOrders,
        java.util.List<FailedOrderSummary> failures
    ) {}

    public record FailedOrderSummary(
        String orderId,
        String orderType,
        java.util.List<String> violations
    ) {}

    public record OrphanCheckResponse(
        boolean healthy,
        int orphanCount,
        java.util.List<String> orphanTaskIds
    ) {}
}
