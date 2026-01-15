package com.goodfellaz17.application.service;

import com.goodfellaz17.domain.model.generated.*;
import com.goodfellaz17.infrastructure.persistence.generated.*;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Production-Ready Order Execution Service
 * =========================================
 * 
 * Handles the complete 1k-play order flow with production-grade guarantees:
 * 
 * 1. ATOMIC TRANSACTIONS
 *    - User balance deduction
 *    - Order row creation
 *    - Balance transaction logging
 *    All commit or rollback together.
 * 
 * 2. IDEMPOTENCY
 *    - Client-supplied idempotency key prevents duplicate orders
 *    - Safe retries return same order ID without double-charging
 * 
 * 3. INPUT VALIDATION
 *    - Quantity within service min/max
 *    - URL format validation
 *    - Service active check
 *    - User tier supported
 * 
 * 4. INSTANT EXECUTION (local/dev only)
 *    - Orders ≤1000 auto-complete immediately in non-prod profiles
 *    - Production uses normal ETA-based pending flow
 * 
 * 5. STRUCTURED LOGGING
 *    - Order created (userId, quantity, cost, balanceBefore/After)
 *    - Order auto-completed (for instant path)
 *    - Idempotency key hits logged
 * 
 * @author goodfellaz17
 * @since 1.0.0
 */
@Service
public class OrderExecutionService {
    
    private static final Logger log = LoggerFactory.getLogger(OrderExecutionService.class);
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Threshold for instant execution in dev (≤1000 plays) */
    private static final int INSTANT_EXECUTION_THRESHOLD = 1000;
    
    /** Spotify URL validation pattern */
    private static final Pattern SPOTIFY_URL_PATTERN = Pattern.compile(
        "^https://(open\\.)?spotify\\.com/(track|album|playlist|artist)/[a-zA-Z0-9]+.*$"
    );
    
    /** Max URL length */
    private static final int MAX_URL_LENGTH = 512;
    
    /** Idempotency key length bounds */
    private static final int MIN_IDEMPOTENCY_KEY_LENGTH = 8;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // DEPENDENCIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    private final GeneratedUserRepository userRepository;
    private final GeneratedServiceRepository serviceRepository;
    private final GeneratedOrderRepository orderRepository;
    private final GeneratedBalanceTransactionRepository transactionRepository;
    private final CapacityService capacityService;
    private final TransactionalOperator transactionalOperator;
    private final TaskGenerationService taskGenerationService;
    
    /** Active Spring profile - used to detect dev/local mode */
    @Value("${spring.profiles.active:prod}")
    private String activeProfile;
    
    /** 
     * When true, disables the instant execution shortcut for small orders.
     * This forces ALL orders (including ≤1000) through the task-based delivery path.
     * Used for freeze testing to ensure the full control flow is exercised.
     */
    @Value("${goodfellaz17.delivery.force-task-delivery:false}")
    private boolean forceTaskDelivery;
    
    public OrderExecutionService(
            GeneratedUserRepository userRepository,
            GeneratedServiceRepository serviceRepository,
            GeneratedOrderRepository orderRepository,
            GeneratedBalanceTransactionRepository transactionRepository,
            CapacityService capacityService,
            TransactionalOperator transactionalOperator,
            TaskGenerationService taskGenerationService) {
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
        this.capacityService = capacityService;
        this.transactionalOperator = transactionalOperator;
        this.taskGenerationService = taskGenerationService;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Create order with full production guarantees.
     * 
     * @param request Validated order request
     * @return OrderResult with created order or error
     */
    public Mono<OrderResult> createOrder(CreateOrderCommand request) {
        log.info("ORDER_CREATE_START | userId={} | quantity={} | serviceId={} | idempotencyKey={}", 
            request.userId(), request.quantity(), request.serviceId(), 
            request.idempotencyKey() != null ? request.idempotencyKey() : "none");
        
        // Step 0: Check idempotency key for duplicate prevention
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            return checkIdempotencyKey(request.idempotencyKey(), request.userId())
                .flatMap(existingOrder -> {
                    if (existingOrder != null) {
                        log.info("ORDER_IDEMPOTENT_HIT | orderId={} | idempotencyKey={}", 
                            existingOrder.getId(), request.idempotencyKey());
                        return Mono.just(OrderResult.success(existingOrder, true));
                    }
                    return executeOrderCreation(request);
                })
                .switchIfEmpty(executeOrderCreation(request));
        }
        
        return executeOrderCreation(request);
    }
    
    /**
     * Validate order request without creating it.
     * Useful for pre-flight checks.
     */
    public Mono<ValidationResult> validateOrder(CreateOrderCommand request) {
        return loadUser(request.userId())
            .flatMap(user -> loadService(request.serviceId())
                .flatMap(service -> validateInputs(user, service, request)));
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CORE EXECUTION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Execute the full order creation flow with atomic transaction.
     */
    private Mono<OrderResult> executeOrderCreation(CreateOrderCommand request) {
        return loadUser(request.userId())
            .flatMap(user -> loadService(request.serviceId())
                .flatMap(service -> validateInputs(user, service, request)
                    .flatMap(validation -> {
                        if (!validation.isValid()) {
                            return Mono.just(OrderResult.validationFailed(validation.errorCode(), validation.message()));
                        }
                        
                        // Check capacity
                        return capacityService.canAccept(request.quantity())
                            .flatMap(capacityResult -> {
                                if (!capacityResult.accepted()) {
                                    log.warn("ORDER_CAPACITY_REJECTED | quantity={} | available={}", 
                                        request.quantity(), capacityResult.availableCapacity72h());
                                    return Mono.just(OrderResult.capacityFull(
                                        request.quantity(), capacityResult.availableCapacity72h()));
                                }
                                
                                // Execute atomic order creation
                                return createOrderAtomically(
                                    user, service, request, capacityResult.estimatedCompletion());
                            });
                    })));
    }
    
    /**
     * Create order with atomic DB operations.
     * All three writes (balance, order, transaction) succeed or fail together.
     * 
     * For orders >1000 plays: generates delivery tasks for the worker to process.
     */
    private Mono<OrderResult> createOrderAtomically(
            UserEntity user,
            ServiceEntity service,
            CreateOrderCommand request,
            Instant estimatedCompletion) {
        
        // Calculate cost
        BigDecimal costPer1k = service.getCostForTier(user.getTierEnum());
        BigDecimal geoMultiplier = BigDecimal.valueOf(
            request.geoProfile() != null 
                ? GeoProfile.valueOf(request.geoProfile()).getCostMultiplier() 
                : 1.0);
        costPer1k = costPer1k.multiply(geoMultiplier);
        
        BigDecimal pricePerUnit = costPer1k.divide(new BigDecimal("1000"), 6, RoundingMode.HALF_UP);
        BigDecimal totalCost = pricePerUnit
            .multiply(new BigDecimal(request.quantity()))
            .setScale(2, RoundingMode.HALF_UP);
        
        // Check balance
        if (user.getBalance().compareTo(totalCost) < 0) {
            BigDecimal deficit = totalCost.subtract(user.getBalance());
            log.warn("ORDER_INSUFFICIENT_BALANCE | userId={} | required={} | available={}", 
                user.getId(), totalCost, user.getBalance());
            return Mono.just(OrderResult.insufficientBalance(totalCost, user.getBalance(), deficit));
        }
        
        BigDecimal balanceBefore = user.getBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(totalCost);
        
        // Determine execution mode
        boolean instantExecution = shouldInstantExecute(request.quantity());
        boolean usesTaskDelivery = taskGenerationService.shouldUseTaskDelivery(request.quantity());
        
        // For task-based delivery, start in RUNNING state
        String initialStatus;
        if (instantExecution) {
            initialStatus = OrderStatus.COMPLETED.name();
        } else if (usesTaskDelivery) {
            initialStatus = OrderStatus.RUNNING.name();  // 15k orders start running immediately
        } else {
            initialStatus = OrderStatus.PENDING.name();
        }
        
        // Build order entity
        OrderEntity order = OrderEntity.builder()
            .userId(user.getId())
            .serviceId(service.getId())
            .serviceName(service.getDisplayName())
            .quantity(request.quantity())
            .delivered(instantExecution ? request.quantity() : 0)
            .remains(instantExecution ? 0 : request.quantity())
            .targetUrl(request.targetUrl())
            .geoProfile(request.geoProfile() != null ? request.geoProfile() : GeoProfile.WORLDWIDE.name())
            .speedMultiplier(1.0)
            .status(initialStatus)
            .pricePerUnit(pricePerUnit)
            .totalCost(totalCost)
            .estimatedCompletionAt(instantExecution ? Instant.now() : estimatedCompletion)
            .externalOrderId(request.idempotencyKey())  // Store idempotency key
            .startedAt((instantExecution || usesTaskDelivery) ? Instant.now() : null)
            .completedAt(instantExecution ? Instant.now() : null)
            .build();
        
        // Set task delivery flag
        order.setUsesTaskDelivery(usesTaskDelivery);
        order.setFailedPermanentPlays(0);
        
        // Build transaction entity
        String reason = String.format("Order #%s - %s x%,d (%s)", 
            order.getId().toString().substring(0, 8),
            service.getDisplayName(),
            request.quantity(),
            order.getGeoProfile());
        
        BalanceTransactionEntity transaction = BalanceTransactionEntity.builder()
            .userId(user.getId())
            .orderId(order.getId())
            .amount(totalCost.negate())
            .balanceBefore(balanceBefore)
            .balanceAfter(balanceAfter)
            .type(TransactionType.DEBIT.name())
            .reason(reason)
            .build();
        
        // Execute atomically with TransactionalOperator
        return transactionalOperator.transactional(
            // 1. Deduct balance using optimistic lock
            userRepository.updateBalance(user.getId(), totalCost.negate())
                .switchIfEmpty(Mono.error(new ConcurrentModificationException(
                    "Balance deduction failed - concurrent modification")))
                // 2. Save order
                .then(orderRepository.save(order))
                // 3. Save transaction
                .flatMap(savedOrder -> transactionRepository.save(transaction)
                    .thenReturn(savedOrder))
        )
        .flatMap(savedOrder -> {
            // Log success
            if (instantExecution) {
                log.info("ORDER_INSTANT_COMPLETED | orderId={} | userId={} | quantity={} | cost={} | " +
                         "balanceBefore={} | balanceAfter={}", 
                    savedOrder.getId(), user.getId(), savedOrder.getQuantity(), 
                    totalCost, balanceBefore, balanceAfter);
                return Mono.just(OrderResult.success(savedOrder, false));
            } else if (usesTaskDelivery) {
                // Generate tasks for 15k+ orders
                log.info("ORDER_15K_ACCEPTED | orderId={} | userId={} | quantity={} | cost={} | " +
                         "eta={} | generatingTasks=true", 
                    savedOrder.getId(), user.getId(), savedOrder.getQuantity(), 
                    totalCost, savedOrder.getEstimatedCompletionAt());
                
                return taskGenerationService.generateTasksForOrder(savedOrder)
                    .count()
                    .doOnSuccess(taskCount -> 
                        log.info("ORDER_TASKS_GENERATED | orderId={} | taskCount={}", 
                            savedOrder.getId(), taskCount))
                    .thenReturn(OrderResult.success(savedOrder, false));
            } else {
                log.info("ORDER_CREATED | orderId={} | userId={} | quantity={} | cost={} | " +
                         "balanceBefore={} | balanceAfter={} | eta={}", 
                    savedOrder.getId(), user.getId(), savedOrder.getQuantity(), 
                    totalCost, balanceBefore, balanceAfter, savedOrder.getEstimatedCompletionAt());
                return Mono.just(OrderResult.success(savedOrder, false));
            }
        })
        .onErrorResume(DuplicateKeyException.class, e -> {
            // Idempotency key collision - fetch existing order
            log.info("ORDER_IDEMPOTENT_COLLISION | idempotencyKey={}", request.idempotencyKey());
            if (request.idempotencyKey() != null) {
                return orderRepository.findByExternalOrderId(request.idempotencyKey())
                    .map(existing -> OrderResult.success(existing, true))
                    .defaultIfEmpty(OrderResult.error("duplicate_key", "Order creation conflict"));
            }
            return Mono.just(OrderResult.error("duplicate_key", "Order creation conflict"));
        })
        .onErrorResume(ConcurrentModificationException.class, e -> {
            log.error("ORDER_CONCURRENT_MODIFICATION | userId={}", user.getId());
            return Mono.just(OrderResult.error("concurrent_modification", 
                "Balance was modified concurrently. Please retry."));
        });
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    private Mono<ValidationResult> validateInputs(
            UserEntity user, 
            ServiceEntity service, 
            CreateOrderCommand request) {
        
        // Check service is active
        if (!service.getIsActive()) {
            return Mono.just(ValidationResult.invalid("service_inactive", 
                String.format("Service '%s' is currently unavailable", service.getDisplayName())));
        }
        
        // Validate quantity bounds
        if (request.quantity() < service.getMinQuantity()) {
            return Mono.just(ValidationResult.invalid("quantity_too_low", 
                String.format("Minimum quantity is %,d for %s", 
                    service.getMinQuantity(), service.getDisplayName())));
        }
        
        if (request.quantity() > service.getMaxQuantity()) {
            return Mono.just(ValidationResult.invalid("quantity_too_high", 
                String.format("Maximum quantity is %,d for %s", 
                    service.getMaxQuantity(), service.getDisplayName())));
        }
        
        // Validate URL format
        String url = request.targetUrl();
        if (url == null || url.isBlank()) {
            return Mono.just(ValidationResult.invalid("url_required", "Target URL is required"));
        }
        
        if (url.length() > MAX_URL_LENGTH) {
            return Mono.just(ValidationResult.invalid("url_too_long", 
                String.format("URL exceeds maximum length of %d characters", MAX_URL_LENGTH)));
        }
        
        if (!SPOTIFY_URL_PATTERN.matcher(url).matches()) {
            return Mono.just(ValidationResult.invalid("url_invalid", 
                "URL must be a valid Spotify track, album, playlist, or artist URL"));
        }
        
        // Validate idempotency key if provided
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            if (request.idempotencyKey().length() < MIN_IDEMPOTENCY_KEY_LENGTH || 
                request.idempotencyKey().length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
                return Mono.just(ValidationResult.invalid("idempotency_key_invalid", 
                    String.format("Idempotency key must be %d-%d characters", 
                        MIN_IDEMPOTENCY_KEY_LENGTH, MAX_IDEMPOTENCY_KEY_LENGTH)));
            }
        }
        
        // Validate geo profile if provided
        if (request.geoProfile() != null && !request.geoProfile().isBlank()) {
            try {
                GeoProfile.valueOf(request.geoProfile());
            } catch (IllegalArgumentException e) {
                return Mono.just(ValidationResult.invalid("geo_profile_invalid", 
                    "Invalid geo profile. Valid options: WORLDWIDE, USA, UK, DE, FR, ES, IT, BR, MX, AU"));
            }
        }
        
        // Check user is active
        if (!user.isActive()) {
            return Mono.just(ValidationResult.invalid("user_suspended", "Account is suspended"));
        }
        
        return Mono.just(ValidationResult.ok());
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private Mono<UserEntity> loadUser(UUID userId) {
        return userRepository.findById(userId)
            .doOnNext(UserEntity::markNotNew)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)));
    }
    
    private Mono<ServiceEntity> loadService(UUID serviceId) {
        return serviceRepository.findById(serviceId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Service not found: " + serviceId)));
    }
    
    /**
     * Check if an order with this idempotency key already exists for this user.
     */
    private Mono<OrderEntity> checkIdempotencyKey(String idempotencyKey, UUID userId) {
        return orderRepository.findByExternalOrderId(idempotencyKey)
            .filter(order -> order.getUserId().equals(userId))
            .doOnNext(OrderEntity::markNotNew);
    }
    
    /**
     * Determine if instant execution applies.
     * Only for local/dev profiles and orders ≤1000 plays.
     * 
     * FREEZE MODE: When forceTaskDelivery=true, this always returns false,
     * ensuring that all orders go through the full task-based delivery path.
     * This is critical for freeze testing - it ensures the control flow is
     * identical to production, only the external network calls are stubbed.
     */
    private boolean shouldInstantExecute(int quantity) {
        // FREEZE MODE: Force all orders through task-based delivery
        if (forceTaskDelivery) {
            log.debug("INSTANT_EXECUTION_DISABLED | forceTaskDelivery=true | quantity={}", quantity);
            return false;
        }
        
        if (quantity > INSTANT_EXECUTION_THRESHOLD) {
            return false;
        }
        
        // Only instant-execute in non-prod profiles
        String profile = activeProfile.toLowerCase();
        boolean isNonProd = profile.contains("local") || 
                           profile.contains("dev") || 
                           profile.contains("test");
        
        if (isNonProd) {
            log.debug("INSTANT_EXECUTION_ENABLED | profile={} | quantity={}", activeProfile, quantity);
        }
        
        return isNonProd;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // RESULT TYPES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Order creation command (input).
     */
    public record CreateOrderCommand(
        UUID userId,
        UUID serviceId,
        int quantity,
        String targetUrl,
        @Nullable String geoProfile,
        @Nullable String idempotencyKey
    ) {
        public static CreateOrderCommand of(
                UUID userId, UUID serviceId, int quantity, String targetUrl) {
            return new CreateOrderCommand(userId, serviceId, quantity, targetUrl, null, null);
        }
        
        public CreateOrderCommand withIdempotencyKey(String key) {
            return new CreateOrderCommand(userId, serviceId, quantity, targetUrl, geoProfile, key);
        }
        
        public CreateOrderCommand withGeoProfile(String geo) {
            return new CreateOrderCommand(userId, serviceId, quantity, targetUrl, geo, idempotencyKey);
        }
    }
    
    /**
     * Order creation result (output).
     */
    public record OrderResult(
        boolean success,
        @Nullable OrderEntity order,
        boolean wasIdempotent,
        @Nullable String errorCode,
        @Nullable String errorMessage,
        @Nullable BigDecimal requiredAmount,
        @Nullable BigDecimal availableBalance,
        @Nullable BigDecimal deficit
    ) {
        public static OrderResult success(OrderEntity order, boolean wasIdempotent) {
            return new OrderResult(true, order, wasIdempotent, null, null, null, null, null);
        }
        
        public static OrderResult validationFailed(String code, String message) {
            return new OrderResult(false, null, false, code, message, null, null, null);
        }
        
        public static OrderResult insufficientBalance(BigDecimal required, BigDecimal available, BigDecimal deficit) {
            return new OrderResult(false, null, false, "balance_insufficient", 
                String.format("Need €%.2f more. Required: €%.2f, Available: €%.2f", 
                    deficit, required, available),
                required, available, deficit);
        }
        
        public static OrderResult capacityFull(int requested, int available) {
            String code = requested >= 15000 ? "capacity_full_15k" : "capacity_full";
            return new OrderResult(false, null, false, code, 
                String.format("Cannot accept %,d plays. Available capacity: %,d in 72h", 
                    requested, available),
                null, null, null);
        }
        
        public static OrderResult error(String code, String message) {
            return new OrderResult(false, null, false, code, message, null, null, null);
        }
    }
    
    /**
     * Validation result for pre-flight checks.
     */
    public record ValidationResult(
        boolean isValid,
        @Nullable String errorCode,
        @Nullable String message
    ) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null, null);
        }
        
        public static ValidationResult invalid(String code, String message) {
            return new ValidationResult(false, code, message);
        }
    }
    
    /**
     * Exception for concurrent modification detection.
     */
    private static class ConcurrentModificationException extends RuntimeException {
        ConcurrentModificationException(String message) {
            super(message);
        }
    }
}
