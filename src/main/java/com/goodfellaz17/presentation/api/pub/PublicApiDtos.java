package com.goodfellaz17.presentation.api.pub;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Public API DTOs for external panel integration.
 * 
 * These DTOs are designed for:
 * - botzzz773 panel integration
 * - Third-party SMM panel resellers
 * - New frontend site
 * 
 * Goals:
 * - Simple, stable contract that hides internal complexity
 * - Clear error responses with actionable codes
 * - No exposure of admin or engine internals
 */
public final class PublicApiDtos {
    
    private PublicApiDtos() {} // Utility class
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CAPACITY SUMMARY
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * GET /api/public/capacity/summary response.
     * 
     * Provides panels with a simple view of current capacity for decision-making.
     */
    public record CapacitySummaryResponse(
        /** Can a 15k order be accepted right now? */
        boolean canAccept15kOrder,
        
        /** Maximum plays deliverable in 72 hours */
        int max72hCapacity,
        
        /** Available capacity after pending orders */
        int available72h,
        
        /** Estimated hours to complete a 15k order */
        double etaHintHours,
        
        /** How many 15k "slots" are available? (available72h / 15000) */
        int slotsFor15k,
        
        /** Current plays/hour capacity */
        int playsPerHour,
        
        /** Human-readable status message */
        String message,
        
        /** Timestamp of this snapshot */
        Instant calculatedAt
    ) implements Serializable {
        
        public static CapacitySummaryResponse from(
                int playsPerHour,
                int max72h,
                int available72h,
                int pendingPlays,
                boolean canAccept15k,
                double etaHours) {
            
            int slots = available72h / 15000;
            String message = canAccept15k 
                ? String.format("Ready to accept orders. %d slots available for 15k packages.", slots)
                : String.format("Capacity limited. Only %,d plays available in 72h. Try smaller orders or wait.", available72h);
            
            return new CapacitySummaryResponse(
                canAccept15k,
                max72h,
                available72h,
                etaHours,
                slots,
                playsPerHour,
                message,
                Instant.now()
            );
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ORDER CREATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * POST /api/public/orders request.
     * 
     * Simplified payload for panel integrations. API key is in body (not header)
     * to support simpler panel implementations.
     * 
     * Idempotency: Include an idempotencyKey to safely retry failed requests.
     * If a request with the same key has already succeeded, the existing order
     * is returned without creating a duplicate or double-charging.
     */
    public record CreatePublicOrderRequest(
        
        /** API key for authentication */
        @NotNull(message = "apiKey is required")
        @Size(min = 10, max = 100, message = "apiKey must be 10-100 characters")
        String apiKey,
        
        /** Service ID (use /api/public/services to discover) */
        @NotNull(message = "serviceId is required")
        UUID serviceId,
        
        /** Number of plays/followers/etc to deliver */
        @NotNull(message = "quantity is required")
        @Min(value = 100, message = "quantity must be at least 100")
        @Max(value = 100000, message = "quantity cannot exceed 100,000")
        Integer quantity,
        
        /** Target Spotify URL */
        @NotNull(message = "targetUrl is required")
        @Size(max = 512, message = "targetUrl cannot exceed 512 characters")
        @Pattern(
            regexp = "^https://(open\\.)?spotify\\.com/.*$",
            message = "targetUrl must be a valid Spotify URL"
        )
        String targetUrl,
        
        /** Optional geo targeting (default: WORLDWIDE) */
        @Nullable
        String geoProfile,
        
        /**
         * Optional idempotency key for safe retries.
         * If provided, duplicate requests with same key return existing order.
         * Must be 8-64 characters. Recommend UUID or panel's internal order ID.
         */
        @Nullable
        @Size(min = 8, max = 64, message = "idempotencyKey must be 8-64 characters if provided")
        String idempotencyKey
        
    ) implements Serializable {}
    
    /**
     * POST /api/public/orders success response.
     */
    public record PublicOrderResponse(
        /** Order ID for tracking */
        UUID id,
        
        /** Accepted quantity */
        int quantity,
        
        /** Number delivered (0 for new orders, may be >0 for instant execution in dev) */
        int delivered,
        
        /** Order status */
        String status,
        
        /** Estimated completion time */
        @Nullable
        Instant estimatedCompletionAt,
        
        /** Estimated hours to completion */
        double estimatedHours,
        
        /** True if this was a duplicate request (idempotency key hit) */
        boolean wasIdempotentRetry
        
    ) implements Serializable {
        
        public static PublicOrderResponse success(
                UUID id, 
                int quantity,
                int delivered,
                String status, 
                Instant eta,
                boolean wasIdempotent) {
            double hours = eta != null 
                ? (eta.toEpochMilli() - System.currentTimeMillis()) / 3600000.0
                : 48.0;
            return new PublicOrderResponse(id, quantity, delivered, status, eta, Math.max(0, hours), wasIdempotent);
        }
        
        /** Backward-compatible factory for non-idempotent calls */
        public static PublicOrderResponse success(UUID id, int quantity, String status, Instant eta) {
            return success(id, quantity, 0, status, eta, false);
        }
    }
    
    /**
     * Error response for public API.
     * 
     * Standard error codes for panels:
     * - auth_invalid: Invalid or missing API key
     * - auth_suspended: Account suspended
     * - balance_insufficient: Not enough balance
     * - capacity_full: Capacity exceeded for requested quantity
     * - capacity_full_15k: Specifically cannot accept 15k order
     * - service_not_found: Service ID doesn't exist
     * - service_inactive: Service temporarily unavailable
     * - validation_error: Request validation failed
     * - order_not_found: Order ID doesn't exist
     * - internal_error: Server error
     */
    public record PublicErrorResponse(
        /** Machine-readable error code (e.g., capacity_full_15k) */
        String code,
        
        /** Human-readable error message */
        String message,
        
        /** Additional details (optional) */
        @Nullable
        Object details
        
    ) implements Serializable {
        
        public static PublicErrorResponse authInvalid() {
            return new PublicErrorResponse("auth_invalid", 
                "Invalid or missing API key", null);
        }
        
        public static PublicErrorResponse authSuspended() {
            return new PublicErrorResponse("auth_suspended",
                "Account is suspended. Contact support.", null);
        }
        
        public static PublicErrorResponse balanceInsufficient(String detail) {
            return new PublicErrorResponse("balance_insufficient",
                "Insufficient balance to complete order", detail);
        }
        
        public static PublicErrorResponse capacityFull(int requested, int available) {
            String detail = String.format(
                "Requested %,d but only %,d available in 72h", requested, available);
            return new PublicErrorResponse("capacity_full", 
                "System at capacity. Try a smaller order or wait.", detail);
        }
        
        public static PublicErrorResponse capacityFull15k(int available) {
            return new PublicErrorResponse("capacity_full_15k",
                "Cannot accept 15k orders at this time",
                String.format("Available: %,d plays in 72h", available));
        }
        
        public static PublicErrorResponse serviceNotFound(UUID serviceId) {
            return new PublicErrorResponse("service_not_found",
                "Service not found", serviceId.toString());
        }
        
        public static PublicErrorResponse serviceInactive(String serviceName) {
            return new PublicErrorResponse("service_inactive",
                "Service temporarily unavailable", serviceName);
        }
        
        public static PublicErrorResponse validationError(String detail) {
            return new PublicErrorResponse("validation_error",
                "Request validation failed", detail);
        }
        
        public static PublicErrorResponse orderNotFound(UUID orderId) {
            return new PublicErrorResponse("order_not_found",
                "Order not found", orderId.toString());
        }
        
        public static PublicErrorResponse internalError(String detail) {
            return new PublicErrorResponse("internal_error",
                "An internal error occurred", detail);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ORDER STATUS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * GET /api/public/orders/{id} response.
     * 
     * Safe public view of order - no internal fields exposed.
     */
    public record PublicOrderStatusResponse(
        /** Order ID */
        UUID id,
        
        /** Total quantity ordered */
        int quantity,
        
        /** How many delivered so far */
        int delivered,
        
        /** How many remaining */
        int remains,
        
        /** Current status: PENDING, RUNNING, COMPLETED, FAILED, CANCELLED */
        String status,
        
        /** Progress percentage (0-100) */
        double progressPercent,
        
        /** Estimated completion time */
        @Nullable
        Instant estimatedCompletionAt,
        
        /** When order was created */
        Instant createdAt,
        
        /** When order started processing */
        @Nullable
        Instant startedAt,
        
        /** When order completed/failed */
        @Nullable
        Instant completedAt
        
    ) implements Serializable {
        
        public static PublicOrderStatusResponse from(
                UUID id,
                int quantity,
                int delivered,
                int remains,
                String status,
                Instant estimatedCompletionAt,
                Instant createdAt,
                Instant startedAt,
                Instant completedAt) {
            
            double progress = quantity > 0 
                ? (double) delivered / quantity * 100.0 
                : 0.0;
            
            return new PublicOrderStatusResponse(
                id, quantity, delivered, remains, status,
                Math.round(progress * 100.0) / 100.0,
                estimatedCompletionAt, createdAt, startedAt, completedAt);
        }
    }
}
