package com.goodfellaz17.presentation.api.pub;

import com.goodfellaz17.application.service.CapacityService;
import com.goodfellaz17.application.service.OrderExecutionService;
import com.goodfellaz17.application.service.OrderExecutionService.CreateOrderCommand;
import com.goodfellaz17.application.service.OrderExecutionService.OrderResult;
import com.goodfellaz17.domain.model.generated.OrderEntity;
import com.goodfellaz17.domain.model.generated.UserEntity;
import com.goodfellaz17.infrastructure.persistence.generated.GeneratedOrderRepository;
import com.goodfellaz17.infrastructure.persistence.generated.GeneratedUserRepository;
import com.goodfellaz17.presentation.api.pub.PublicApiDtos.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Public API Controller for External Panel Integration.
 *
 * Production-ready order API with:
 * - Atomic transactions (balance + order + transaction ledger)
 * - Idempotency support (safe retries via idempotencyKey)
 * - Strict input validation
 * - Instant execution for ≤1k orders in local/dev profiles
 * - Structured logging for debugging
 *
 * Endpoints:
 * - GET  /api/public/capacity/summary  - Check if we can accept orders
 * - POST /api/public/orders            - Create new order (idempotent if key provided)
 * - GET  /api/public/orders/{id}       - Get order status
 *
 * All endpoints return JSON with consistent error format.
 */
@RestController
@RequestMapping("/api/public")
@CrossOrigin(origins = "*")
public class PublicApiController {

    private static final Logger log = LoggerFactory.getLogger(PublicApiController.class);

    /** Standard 15k package size for capacity checks */
    private static final int STANDARD_15K_PACKAGE = 15000;

    /** Maximum hours for delivery estimation */
    private static final int MAX_HOURS = 72;

    private final CapacityService capacityService;
    private final OrderExecutionService orderExecutionService;
    private final GeneratedUserRepository userRepository;
    private final GeneratedOrderRepository orderRepository;

    public PublicApiController(
            CapacityService capacityService,
            OrderExecutionService orderExecutionService,
            GeneratedUserRepository userRepository,
            GeneratedOrderRepository orderRepository) {
        this.capacityService = capacityService;
        this.orderExecutionService = orderExecutionService;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GET /api/public/capacity/summary
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get capacity summary for panel decision-making.
     *
     * Panels can use this to:
     * - Show availability status to customers
     * - Decide whether to route a 15k order here or elsewhere
     * - Display ETA hints
     *
     * No authentication required - this is public info.
     *
     * @return CapacitySummaryResponse with current capacity metrics
     */
    @GetMapping("/capacity/summary")
    public Mono<ResponseEntity<CapacitySummaryResponse>> getCapacitySummary() {
        log.debug("GET /api/public/capacity/summary");

        return capacityService.canAccept(STANDARD_15K_PACKAGE)
            .map(result -> {
                // Use simulated capacity if set, otherwise use real
                Integer simulated = capacityService.getSimulatedCapacity();
                int playsPerHour = simulated != null
                    ? simulated
                    : result.availableCapacity72h() / MAX_HOURS;

                int max72h = playsPerHour * MAX_HOURS;
                int available72h = max72h - result.pendingPlays();

                CapacitySummaryResponse response = CapacitySummaryResponse.from(
                    playsPerHour,
                    max72h,
                    Math.max(0, available72h),
                    result.pendingPlays(),
                    result.accepted(),
                    result.estimatedHours()
                );

                log.info("Capacity summary: canAccept15k={}, available72h={}, slots={}",
                    response.canAccept15kOrder(),
                    response.available72h(),
                    response.slotsFor15k());

                return ResponseEntity.ok(response);
            })
            .onErrorResume(e -> {
                log.error("Error getting capacity summary", e);
                return Mono.just(ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null));
            });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POST /api/public/orders
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a new order via public API.
     *
     * PRODUCTION-READY with:
     * - Atomic transactions (balance + order + ledger)
     * - Idempotency via optional idempotencyKey
     * - Strict input validation
     * - Instant execution for ≤1k in local/dev
     *
     * Request:
     * {
     *   "apiKey": "test-api-key-local-dev-12345",
     *   "serviceId": "3c1cb593-85a7-4375-8092-d39c00399a7b",
     *   "quantity": 1000,
     *   "targetUrl": "https://open.spotify.com/track/...",
     *   "idempotencyKey": "my-panel-order-12345"  // optional, for safe retries
     * }
     *
     * Success Response (201):
     * {
     *   "id": "uuid",
     *   "quantity": 1000,
     *   "delivered": 1000,         // 1000 in local/dev instant mode
     *   "status": "COMPLETED",     // COMPLETED in local/dev, PENDING in prod
     *   "estimatedCompletionAt": "2026-01-13T...",
     *   "estimatedHours": 0.0,
     *   "wasIdempotentRetry": false
     * }
     */
    @PostMapping("/orders")
    public Mono<ResponseEntity<Object>> createOrder(
            @Valid @RequestBody CreatePublicOrderRequest request) {

        log.info("POST /api/public/orders | serviceId={} | quantity={} | idempotencyKey={}",
            request.serviceId(), request.quantity(),
            request.idempotencyKey() != null ? request.idempotencyKey() : "none");

        // Step 1: Authenticate by API key
        return authenticateUser(request.apiKey())
            .flatMap(user -> {
                // Step 2: Build command and delegate to OrderExecutionService
                CreateOrderCommand command = new CreateOrderCommand(
                    user.getId(),
                    request.serviceId(),
                    request.quantity(),
                    request.targetUrl(),
                    request.geoProfile(),
                    request.idempotencyKey()
                );

                return orderExecutionService.createOrder(command)
                    .map(result -> mapOrderResult(result));
            })
            .onErrorResume(this::handleErrorObject);
    }

    /**
     * Map OrderResult to HTTP response.
     */
    private ResponseEntity<Object> mapOrderResult(OrderResult result) {
        if (result.success()) {
            OrderEntity order = result.order();
            PublicOrderResponse response = PublicOrderResponse.success(
                order.getId(),
                order.getQuantity(),
                order.getDelivered(),
                order.getStatus(),
                order.getEstimatedCompletionAt(),
                result.wasIdempotent()
            );

            // Return 200 for idempotent hits, 201 for new orders
            HttpStatus status = result.wasIdempotent() ? HttpStatus.OK : HttpStatus.CREATED;
            return ResponseEntity.status(status).body(response);
        }

        // Map error codes to HTTP status
        HttpStatus status = switch (result.errorCode()) {
            case "auth_invalid" -> HttpStatus.UNAUTHORIZED;
            case "auth_suspended", "user_suspended" -> HttpStatus.FORBIDDEN;
            case "balance_insufficient" -> HttpStatus.PAYMENT_REQUIRED;
            case "service_not_found", "order_not_found" -> HttpStatus.NOT_FOUND;
            case "service_inactive", "validation_error", "quantity_too_low",
                 "quantity_too_high", "url_invalid", "url_required", "url_too_long",
                 "geo_profile_invalid", "idempotency_key_invalid" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "capacity_full", "capacity_full_15k" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "concurrent_modification" -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        return ResponseEntity.status(status)
            .body(new PublicErrorResponse(result.errorCode(), result.errorMessage(), null));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GET /api/public/orders/{id}
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get order status by ID.
     *
     * Requires API key as query param for simple panel integration.
     *
     * Success Response (200):
     * {
     *   "id": "uuid",
     *   "quantity": 15000,
     *   "delivered": 7500,
     *   "remains": 7500,
     *   "status": "RUNNING",
     *   "progressPercent": 50.0,
     *   "estimatedCompletionAt": "2026-01-15T12:00:00Z"
     * }
     */
    @GetMapping("/orders/{orderId}")
    public Mono<ResponseEntity<Object>> getOrderStatus(
            @PathVariable UUID orderId,
            @RequestParam String apiKey) {

        log.debug("GET /api/public/orders/{} - apiKey={}...", orderId,
            apiKey.length() > 10 ? apiKey.substring(0, 10) : apiKey);

        return authenticateUser(apiKey)
            .flatMap(user -> orderRepository.findById(orderId)
                .filter(order -> order.getUserId().equals(user.getId()))
                .map(this::toPublicOrderStatus)
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(PublicErrorResponse.orderNotFound(orderId))))
            .onErrorResume(e -> handleErrorObject(e));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Authenticate user by API key.
     */
    private Mono<UserEntity> authenticateUser(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.error(new PublicApiException("auth_invalid", "API key is required"));
        }

        return userRepository.findByApiKey(apiKey.trim())
            .switchIfEmpty(Mono.error(new PublicApiException("auth_invalid", "Invalid API key")))
            .flatMap(user -> {
                user.markNotNew();  // Mark as existing entity loaded from DB
                if (!user.isActive()) {
                    return Mono.error(new PublicApiException("auth_suspended", "Account is suspended"));
                }
                return Mono.just(user);
            });
    }

    /**
     * Convert order entity to public status response.
     */
    private PublicOrderStatusResponse toPublicOrderStatus(OrderEntity order) {
        return PublicOrderStatusResponse.from(
            order.getId(),
            order.getQuantity(),
            order.getDelivered(),
            order.getRemains(),
            order.getStatus(),
            order.getEstimatedCompletionAt(),
            order.getCreatedAt(),
            order.getStartedAt(),
            order.getCompletedAt()
        );
    }

    /**
     * Convert exceptions to appropriate HTTP responses (for Object return types).
     */
    private Mono<ResponseEntity<Object>> handleErrorObject(Throwable e) {
        if (e instanceof PublicApiException pae) {
            HttpStatus status = switch (pae.code) {
                case "auth_invalid" -> HttpStatus.UNAUTHORIZED;
                case "auth_suspended" -> HttpStatus.FORBIDDEN;
                case "balance_insufficient" -> HttpStatus.PAYMENT_REQUIRED;
                case "service_not_found", "order_not_found" -> HttpStatus.NOT_FOUND;
                case "service_inactive", "validation_error" -> HttpStatus.UNPROCESSABLE_ENTITY;
                case "capacity_full", "capacity_full_15k" -> HttpStatus.SERVICE_UNAVAILABLE;
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };

            return Mono.just(ResponseEntity
                .status(status)
                .body(new PublicErrorResponse(pae.code, pae.getMessage(), null)));
        }

        log.error("Unexpected error in public API", e);
        return Mono.just(ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(PublicErrorResponse.internalError("Please try again later")));
    }

    /**
     * Exception for public API errors with codes.
     */
    private static class PublicApiException extends RuntimeException {
        final String code;

        PublicApiException(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
