package com.goodfellaz17.application.service.generated;

import com.goodfellaz17.application.dto.generated.CreateOrderRequest;
import com.goodfellaz17.application.dto.generated.OrderResponse;
import com.goodfellaz17.domain.model.generated.*;
import com.goodfellaz17.infrastructure.persistence.generated.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * Service: OrderService
 * Aggregate Root: Order
 * 
 * Handles order lifecycle management including creation, status transitions,
 * progress tracking, and cancellation. Integrates with ServiceRepository for
 * pricing and UserRepository for balance management.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
@Service
public class GeneratedOrderService {
    
    private static final Logger log = LoggerFactory.getLogger(GeneratedOrderService.class);
    
    private final GeneratedOrderRepository orderRepository;
    private final GeneratedUserRepository userRepository;
    private final GeneratedServiceRepository serviceRepository;
    private final GeneratedBalanceTransactionRepository transactionRepository;
    
    public GeneratedOrderService(
            GeneratedOrderRepository orderRepository,
            GeneratedUserRepository userRepository,
            GeneratedServiceRepository serviceRepository,
            GeneratedBalanceTransactionRepository transactionRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.transactionRepository = transactionRepository;
    }
    
    /**
     * Create a new order with balance deduction.
     * 
     * @param request Order creation request
     * @return Created order as response DTO
     */
    @Transactional
    public Mono<OrderResponse> createOrder(@Valid CreateOrderRequest request) {
        return userRepository.findById(request.userId())
            .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found")))
            .flatMap(user -> {
                // Verify user is active
                if (!UserStatus.ACTIVE.name().equals(user.getStatus())) {
                    return Mono.error(new IllegalStateException("User account is not active"));
                }
                
                return serviceRepository.findById(request.serviceId())
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Service not found")))
                    .flatMap(service -> {
                        // Validate quantity
                        if (!service.isValidQuantity(request.quantity())) {
                            return Mono.error(new IllegalArgumentException(
                                String.format("Quantity must be between %d and %d",
                                    service.getMinQuantity(), service.getMaxQuantity())));
                        }
                        
                        // Calculate cost based on user tier
                        UserTier userTier = UserTier.valueOf(user.getTier());
                        BigDecimal totalCost = service.calculateCost(request.quantity(), userTier);
                        
                        // Verify sufficient balance
                        if (user.getBalance().compareTo(totalCost) < 0) {
                            return Mono.error(new IllegalStateException(
                                String.format("Insufficient balance. Required: $%.2f, Available: $%.2f",
                                    totalCost, user.getBalance())));
                        }
                        
                        // Create order entity
                        OrderEntity order = OrderEntity.builder()
                            .userId(user.getId())
                            .serviceId(service.getId())
                            .serviceName(service.getName())
                            .targetUrl(request.targetUrl())
                            .quantity(request.quantity())
                            .pricePerUnit(service.getCostForTier(userTier).divide(new BigDecimal(1000)))
                            .totalCost(totalCost)
                            .status(OrderStatus.PENDING)
                            .geoProfile(request.geoProfile() != null ? request.geoProfile() : GeoProfile.WORLDWIDE.name())
                            .startCount(request.startCount() != null ? request.startCount() : 0)
                            .build();
                        
                        // Deduct balance
                        BigDecimal newBalance = user.getBalance().subtract(totalCost);
                        
                        // Create transaction record
                        BalanceTransactionEntity transaction = BalanceTransactionEntity.createDebit(
                            user.getId(),
                            order.getId(),
                            totalCost,
                            user.getBalance(),
                            String.format("Order %s - %s x%d", 
                                order.getId().toString().substring(0, 8),
                                service.getDisplayName(),
                                request.quantity())
                        );
                        
                        return userRepository.updateBalance(user.getId(), newBalance)
                            .then(transactionRepository.save(transaction))
                            .then(orderRepository.save(order))
                            .map(this::toResponse);
                    });
            })
            .doOnSuccess(order -> log.info("Created order {} for user {}", 
                order.id(), order.userId()))
            .doOnError(e -> log.error("Failed to create order: {}", e.getMessage()));
    }
    
    /**
     * Get order by ID.
     */
    public Mono<OrderResponse> getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
            .map(this::toResponse);
    }
    
    /**
     * Get all orders for a user.
     */
    public Flux<OrderResponse> getUserOrders(UUID userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .map(this::toResponse);
    }
    
    /**
     * Get paginated orders for a user.
     */
    public Flux<OrderResponse> getUserOrdersPaginated(UUID userId, int page, int size) {
        int offset = page * size;
        return orderRepository.findByUserIdPaginated(userId, size, offset)
            .map(this::toResponse);
    }
    
    /**
     * Start order processing.
     */
    @Transactional
    public Mono<OrderResponse> startOrder(UUID orderId) {
        return orderRepository.findById(orderId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Order not found")))
            .flatMap(order -> {
                if (!order.canTransitionTo(OrderStatus.IN_PROGRESS)) {
                    return Mono.error(new IllegalStateException(
                        "Cannot start order in status: " + order.getStatus()));
                }
                
                return orderRepository.updateStatus(orderId, 
                        OrderStatus.IN_PROGRESS.name(), Instant.now())
                    .then(orderRepository.findById(orderId))
                    .map(this::toResponse);
            });
    }
    
    /**
     * Update order progress.
     */
    @Transactional
    public Mono<OrderResponse> updateProgress(UUID orderId, int currentCount) {
        return orderRepository.findById(orderId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Order not found")))
            .flatMap(order -> {
                if (!OrderStatus.IN_PROGRESS.name().equals(order.getStatus())) {
                    return Mono.error(new IllegalStateException(
                        "Can only update progress for IN_PROGRESS orders"));
                }
                
                int delivered = currentCount - order.getStartCount();
                int remains = order.getQuantity() - delivered;
                
                return orderRepository.updateProgress(orderId, currentCount, delivered, remains)
                    .then(orderRepository.findById(orderId))
                    .map(this::toResponse);
            });
    }
    
    /**
     * Complete order successfully.
     */
    @Transactional
    public Mono<OrderResponse> completeOrder(UUID orderId) {
        return orderRepository.findById(orderId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Order not found")))
            .flatMap(order -> {
                if (!order.canTransitionTo(OrderStatus.COMPLETED)) {
                    return Mono.error(new IllegalStateException(
                        "Cannot complete order in status: " + order.getStatus()));
                }
                
                return orderRepository.updateStatus(orderId, 
                        OrderStatus.COMPLETED.name(), null)
                    .then(orderRepository.findById(orderId))
                    .map(this::toResponse);
            });
    }
    
    /**
     * Cancel order with refund.
     */
    @Transactional
    public Mono<OrderResponse> cancelOrder(UUID orderId, String reason) {
        return orderRepository.findById(orderId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Order not found")))
            .flatMap(order -> {
                if (!order.canTransitionTo(OrderStatus.CANCELLED)) {
                    return Mono.error(new IllegalStateException(
                        "Cannot cancel order in status: " + order.getStatus()));
                }
                
                return userRepository.findById(order.getUserId())
                    .flatMap(user -> {
                        // Calculate refund amount
                        BigDecimal refundAmount = order.calculateRefundAmount();
                        BigDecimal newBalance = user.getBalance().add(refundAmount);
                        
                        // Create refund transaction
                        BalanceTransactionEntity transaction = BalanceTransactionEntity.createRefund(
                            user.getId(),
                            order.getId(),
                            refundAmount,
                            user.getBalance(),
                            "Order cancelled: " + reason
                        );
                        
                        return userRepository.updateBalance(user.getId(), newBalance)
                            .then(transactionRepository.save(transaction))
                            .then(orderRepository.updateStatus(orderId, 
                                OrderStatus.CANCELLED.name(), null))
                            .then(orderRepository.findById(orderId))
                            .map(this::toResponse);
                    });
            });
    }
    
    /**
     * Refill partial order.
     */
    @Transactional
    public Mono<OrderResponse> refillOrder(UUID orderId, int additionalQuantity) {
        return orderRepository.findById(orderId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Order not found")))
            .flatMap(order -> {
                if (!OrderStatus.PARTIAL.name().equals(order.getStatus())) {
                    return Mono.error(new IllegalStateException(
                        "Can only refill PARTIAL orders"));
                }
                
                int newRemains = order.getRemains() + additionalQuantity;
                int newQuantity = order.getQuantity() + additionalQuantity;
                
                order.setRemains(newRemains);
                order.setQuantity(newQuantity);
                order.setStatus(OrderStatus.IN_PROGRESS.name());
                
                return orderRepository.save(order)
                    .map(this::toResponse);
            });
    }
    
    /**
     * Get order statistics for a user.
     */
    public Mono<UserOrderStats> getUserOrderStats(UUID userId) {
        return Mono.zip(
            orderRepository.countByUserId(userId),
            orderRepository.countByUserIdAndStatus(userId, OrderStatus.COMPLETED.name()),
            orderRepository.sumTotalCostByUserId(userId)
        ).map(tuple -> new UserOrderStats(
            tuple.getT1(),
            tuple.getT2(),
            tuple.getT3() != null ? tuple.getT3() : BigDecimal.ZERO
        ));
    }
    
    private OrderResponse toResponse(OrderEntity order) {
        return new OrderResponse(
            order.getId(),
            order.getUserId(),
            order.getServiceId(),
            order.getServiceName(),
            order.getTargetUrl(),
            order.getQuantity(),
            order.getStartCount(),
            order.getCurrentCount(),
            order.getDelivered(),
            order.getRemains(),
            order.getTotalCost(),
            order.getStatusEnum(),
            order.getGeoProfileEnum(),
            order.getProgressPercent(),
            order.isTerminal(),
            order.getCreatedAt(),
            order.getStartedAt(),
            order.getUpdatedAt()
        );
    }
    
    /**
     * User order statistics record.
     */
    public record UserOrderStats(
        long totalOrders,
        long completedOrders,
        BigDecimal totalSpent
    ) {
        public double completionRate() {
            if (totalOrders == 0) return 0.0;
            return (double) completedOrders / totalOrders;
        }
    }
}
