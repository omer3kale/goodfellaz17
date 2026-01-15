package com.goodfellaz17.presentation.api.v2;

import com.goodfellaz17.application.dto.generated.CreateOrderRequest;
import com.goodfellaz17.application.dto.generated.OrderResponse;
import com.goodfellaz17.application.service.CapacityService;
import com.goodfellaz17.domain.model.generated.*;
import com.goodfellaz17.infrastructure.persistence.generated.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Production-Ready REST Controller for Order operations (V2).
 * 
 * Uses MontiCore-generated entities and R2DBC repositories.
 * 
 * Endpoints:
 * - POST /api/v2/orders - Create order with API key auth
 * - GET /api/v2/orders/{id} - Get order by ID
 * - GET /api/v2/orders - List user orders
 * 
 * Authentication: X-Api-Key header required
 * 
 * Error Codes:
 * - 401: Missing or invalid API key
 * - 402: Insufficient balance
 * - 403: Account not active
 * - 404: Service or order not found
 * - 422: Service inactive or validation failed
 */
@RestController
@RequestMapping("/api/v2/orders")
@CrossOrigin(origins = "*")
public class OrderControllerV2 {
    
    private static final Logger log = LoggerFactory.getLogger(OrderControllerV2.class);
    
    private static final String API_KEY_HEADER = "X-Api-Key";
    
    private final GeneratedUserRepository userRepository;
    private final GeneratedServiceRepository serviceRepository;
    private final GeneratedOrderRepository orderRepository;
    private final GeneratedBalanceTransactionRepository transactionRepository;
    private final CapacityService capacityService;
    private final com.goodfellaz17.application.service.TaskGenerationService taskGenerationService;
    
    public OrderControllerV2(
            GeneratedUserRepository userRepository,
            GeneratedServiceRepository serviceRepository,
            GeneratedOrderRepository orderRepository,
            GeneratedBalanceTransactionRepository transactionRepository,
            CapacityService capacityService,
            com.goodfellaz17.application.service.TaskGenerationService taskGenerationService) {
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
        this.capacityService = capacityService;
        this.taskGenerationService = taskGenerationService;
    }
    
    /**
     * Create a new order.
     * 
     * POST /api/v2/orders
     * 
     * Headers:
     *   X-Api-Key: (required) User's API key
     * 
     * Request Body (JSON):
     * {
     *   "serviceId": "uuid",
     *   "quantity": 15000,
     *   "targetUrl": "https://open.spotify.com/track/...",
     *   "geoProfile": "WORLDWIDE" (optional),
     *   "speedMultiplier": 1.0 (optional)
     * }
     * 
     * Response:
     * - 201 Created: Order details
     * - 401 Unauthorized: Invalid API key
     * - 402 Payment Required: Insufficient balance
     * - 404 Not Found: Service not found
     * - 422 Unprocessable: Service inactive
     */
    @PostMapping
    @Transactional
    public Mono<ResponseEntity<OrderResponse>> createOrder(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @Valid @RequestBody CreateOrderRequest request) {
        
        log.info("POST /api/v2/orders - serviceId={}, quantity={}", 
            request.serviceId(), request.quantity());
        
        return authenticateUser(apiKey)
            .flatMap(user -> createOrderWithTransaction(user, request))
            .map(response -> {
                log.info("Order created successfully: id={}, cost=€{}", 
                    response.id(), response.cost());
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            })
            .onErrorResume(ResponseStatusException.class, ex -> {
                log.warn("Order creation failed: {} - {}", 
                    ex.getStatusCode(), ex.getReason());
                return Mono.just(ResponseEntity
                    .status(ex.getStatusCode())
                    .body(null));
            });
    }
    
    /**
     * Get order by ID.
     * 
     * GET /api/v2/orders/{orderId}
     */
    @GetMapping("/{orderId}")
    public Mono<ResponseEntity<OrderResponse>> getOrder(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @PathVariable UUID orderId) {
        
        return authenticateUser(apiKey)
            .flatMap(user -> orderRepository.findById(orderId)
                .filter(order -> order.getUserId().equals(user.getId()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Order not found or access denied")))
                .flatMap(this::enrichOrderResponse))
            .map(ResponseEntity::ok);
    }
    
    /**
     * List orders for authenticated user.
     * 
     * GET /api/v2/orders?page=0&size=20
     */
    @GetMapping
    public Mono<ResponseEntity<?>> listOrders(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = page * safeSize;
        
        return authenticateUser(apiKey)
            .flatMapMany(user -> orderRepository.findByUserIdWithPagination(
                    user.getId(), safeSize, offset))
            .flatMap(this::enrichOrderResponse)
            .collectList()
            .map(ResponseEntity::ok);
    }
    
    /**
     * Get user's balance (convenience endpoint).
     * 
     * GET /api/v2/orders/balance
     */
    @GetMapping("/balance")
    public Mono<ResponseEntity<BalanceResponse>> getBalance(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey) {
        
        return authenticateUser(apiKey)
            .map(user -> ResponseEntity.ok(new BalanceResponse(
                user.getBalance(),
                user.getTier(),
                user.getEmail()
            )));
    }
    
    // === Private Methods ===
    
    /**
     * Authenticate user by API key.
     * 
     * @throws ResponseStatusException 401 if invalid, 403 if inactive
     */
    private Mono<UserEntity> authenticateUser(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, 
                "API key required. Pass it in X-Api-Key header."));
        }
        
        return userRepository.findByApiKey(apiKey.trim())
            .switchIfEmpty(Mono.error(new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, 
                "Invalid API key")))
            .flatMap(user -> {
                if (!user.isActive()) {
                    return Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, 
                        "Account is suspended or pending verification"));
                }
                return Mono.just(user);
            });
    }
    
    /**
     * Create order with balance transaction in single transaction.
     */
    private Mono<OrderResponse> createOrderWithTransaction(
            UserEntity user, 
            CreateOrderRequest request) {
        
        // === First: Check Capacity ===
        return capacityService.canAccept(request.quantity())
            .flatMap(capacityResult -> {
                if (!capacityResult.accepted()) {
                    return Mono.error(new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        capacityResult.rejectionReason()));
                }
                
                // Store ETA for later use
                final Instant estimatedCompletion = capacityResult.estimatedCompletion();
                
                return serviceRepository.findById(request.serviceId())
                    .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, 
                        "Service not found: " + request.serviceId())))
                    .flatMap(service -> {
                        // === Validation ===
                        
                        if (!service.getIsActive()) {
                            return Mono.error(new ResponseStatusException(
                                HttpStatus.UNPROCESSABLE_ENTITY, 
                                "Service '" + service.getDisplayName() + "' is currently unavailable"));
                        }
                        
                        if (request.quantity() < service.getMinQuantity()) {
                            return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, 
                                String.format("Minimum quantity for %s is %,d", 
                                    service.getDisplayName(), service.getMinQuantity())));
                        }
                        
                        if (request.quantity() > service.getMaxQuantity()) {
                            return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, 
                                String.format("Maximum quantity for %s is %,d", 
                                    service.getDisplayName(), service.getMaxQuantity())));
                        }
                        
                        // === Cost Calculation ===
                        
                        UserTier tier = user.getTierEnum();
                        BigDecimal costPer1k = service.getCostForTier(tier);
                        
                        // Apply geo multiplier
                        GeoProfile geoProfile = request.getGeoProfileEnum();
                        BigDecimal geoMultiplier = BigDecimal.valueOf(geoProfile.getCostMultiplier());
                        costPer1k = costPer1k.multiply(geoMultiplier);
                        
                        // Price per unit (1 play/follower/etc)
                        BigDecimal pricePerUnit = costPer1k
                            .divide(new BigDecimal("1000"), 6, RoundingMode.HALF_UP);
                        
                        // Total cost
                        BigDecimal totalCost = pricePerUnit
                            .multiply(new BigDecimal(request.quantity()))
                            .setScale(2, RoundingMode.HALF_UP);
                        
                        // === Balance Check ===
                        
                        if (user.getBalance().compareTo(totalCost) < 0) {
                            BigDecimal deficit = totalCost.subtract(user.getBalance());
                            return Mono.error(new ResponseStatusException(
                                HttpStatus.PAYMENT_REQUIRED, 
                                String.format(
                                    "Insufficient balance. Required: €%.2f, Available: €%.2f. " +
                                    "Please add €%.2f to proceed.",
                                    totalCost, user.getBalance(), deficit)));
                        }
                        
                        // === Create Order Entity with ETA ===
                        
                        OrderEntity order = OrderEntity.builder()
                            .userId(user.getId())
                            .serviceId(service.getId())
                            .serviceName(service.getDisplayName())
                            .quantity(request.quantity())
                            .delivered(0)
                            .remains(request.quantity())
                            .targetUrl(request.targetUrl())
                            .geoProfile(geoProfile.name())
                            .speedMultiplier(request.getSpeedMultiplierOrDefault())
                            .status(OrderStatus.PENDING.name())
                            .pricePerUnit(pricePerUnit)
                            .totalCost(totalCost)
                            .estimatedCompletionAt(estimatedCompletion)
                            .build();
                
                // === Atomic Execution ===
                // Order: 1) debit balance, 2) insert order, 3) insert transaction (FK to order)
                
                return userRepository.updateBalance(user.getId(), totalCost.negate())
                    .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.PAYMENT_REQUIRED, 
                        "Balance deduction failed - concurrent modification detected")))
                    .then(orderRepository.save(order))
                    .map(savedOrder -> savedOrder.markNotNew())  // Mark as existing for future saves
                    .flatMap(savedOrder -> {
                        BalanceTransactionEntity transaction = BalanceTransactionEntity.builder()
                            .userId(user.getId())
                            .orderId(savedOrder.getId())
                            .amount(totalCost.negate())
                            .balanceBefore(user.getBalance())
                            .balanceAfter(user.getBalance().subtract(totalCost))
                            .type(TransactionType.DEBIT.name())
                            .reason(String.format("Order #%s - %s x%,d (%s)", 
                                savedOrder.getId().toString().substring(0, 8),
                                service.getDisplayName(),
                                request.quantity(),
                                geoProfile.name()))
                            .build();
                        return transactionRepository.save(transaction)
                            .thenReturn(savedOrder);
                    })
                    .flatMap(savedOrder -> {
                        // Generate tasks for orders that need task-based delivery
                        if (taskGenerationService.shouldUseTaskDelivery(savedOrder.getQuantity())) {
                            log.info("Generating tasks for order {} (quantity={})", 
                                savedOrder.getId(), savedOrder.getQuantity());
                            savedOrder.setUsesTaskDelivery(true);
                            savedOrder.markNotNew();  // Mark as existing for UPDATE instead of INSERT
                            return orderRepository.save(savedOrder)
                                .flatMap(taskOrder -> taskGenerationService.generateTasksForOrder(taskOrder)
                                    .count()
                                    .doOnSuccess(count -> log.info(
                                        "Generated {} tasks for order {}", count, taskOrder.getId()))
                                    .thenReturn(taskOrder));
                        }
                        return Mono.just(savedOrder);
                    })
                    .map(savedOrder -> toResponse(savedOrder, service.getDisplayName()));
            });
        });
    }
    
    /**
     * Enrich order with service name.
     */
    private Mono<OrderResponse> enrichOrderResponse(OrderEntity order) {
        if (order.getServiceName() != null && !order.getServiceName().isBlank()) {
            return Mono.just(toResponse(order, order.getServiceName()));
        }
        
        return serviceRepository.findById(order.getServiceId())
            .map(service -> toResponse(order, service.getDisplayName()))
            .defaultIfEmpty(toResponse(order, "Unknown Service"));
    }
    
    /**
     * Convert entity to response DTO.
     */
    private OrderResponse toResponse(OrderEntity order, String serviceName) {
        double progress = order.getQuantity() > 0 
            ? (double) order.getDelivered() / order.getQuantity() * 100.0 
            : 0.0;
        
        return new OrderResponse(
            order.getId(),
            order.getServiceId(),
            serviceName,
            order.getQuantity(),
            order.getDelivered(),
            order.getTargetUrl(),
            order.getGeoProfile(),
            order.getStatus(),
            order.getCost(),  // Uses backward-compat alias
            order.getRefundAmount(),
            order.getStartCount(),
            order.getCurrentCount(),
            order.getCreatedAt(),
            order.getStartedAt(),
            order.getCompletedAt(),
            order.getEstimatedCompletionAt(),
            order.getFailureReason(),
            Math.round(progress * 100.0) / 100.0,
            order.isTerminal()
        );
    }
    
    // === Response DTOs ===
    
    /**
     * Balance response.
     */
    public record BalanceResponse(
        BigDecimal balance,
        String tier,
        String email
    ) {}
    
    /**
     * Error response.
     */
    public record ErrorResponse(
        int status,
        String error,
        String message,
        Instant timestamp
    ) {}
    
    // === Exception Handlers ===
    
    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleResponseStatus(ResponseStatusException ex) {
        return Mono.just(ResponseEntity
            .status(ex.getStatusCode())
            .body(new ErrorResponse(
                ex.getStatusCode().value(),
                ex.getStatusCode().toString(),
                ex.getReason(),
                Instant.now()
            )));
    }
    
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericError(Exception ex) {
        log.error("Unexpected error in OrderControllerV2", ex);
        return Mono.just(ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse(
                500,
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please try again.",
                Instant.now()
            )));
    }
}
