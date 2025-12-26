package com.goodfellaz17.presentation.api;

import com.goodfellaz17.infrastructure.persistence.entity.ApiKeyEntity;
import com.goodfellaz17.infrastructure.persistence.entity.OrderEntity;
import com.goodfellaz17.infrastructure.persistence.entity.ServiceEntity;
import com.goodfellaz17.infrastructure.persistence.repository.ApiKeyRepository;
import com.goodfellaz17.infrastructure.persistence.repository.OrderRepository;
import com.goodfellaz17.infrastructure.persistence.repository.ServiceRepository;
import com.goodfellaz17.presentation.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Customer Dashboard API Controller.
 * 
 * Endpoints for customer dashboard:
 * - GET  /api/customer/{apiKey}/summary - Dashboard header data
 * - GET  /api/customer/{apiKey}/orders - Order history
 * - GET  /api/customer/services - Available services
 * - POST /api/customer/checkout - Place new order
 */
@RestController
@RequestMapping("/api/customer")
@CrossOrigin(origins = "*")
public class CustomerDashboardController {

    private static final Logger log = LoggerFactory.getLogger(CustomerDashboardController.class);

    private final ApiKeyRepository apiKeyRepository;
    private final OrderRepository orderRepository;
    private final ServiceRepository serviceRepository;

    public CustomerDashboardController(ApiKeyRepository apiKeyRepository,
                                        OrderRepository orderRepository,
                                        ServiceRepository serviceRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.orderRepository = orderRepository;
        this.serviceRepository = serviceRepository;
    }

    // ==================== SUMMARY ====================

    /**
     * GET /api/customer/{apiKey}/summary
     * Dashboard header: balance, active orders, recent orders.
     */
    @GetMapping("/{apiKey}/summary")
    public Mono<ResponseEntity<CustomerSummary>> getSummary(@PathVariable String apiKey) {
        log.info("Getting customer summary for key: {}...", apiKey.substring(0, Math.min(8, apiKey.length())));

        return apiKeyRepository.findByApiKey(apiKey)
            .switchIfEmpty(Mono.error(new RuntimeException("Invalid API key")))
            .flatMap(apiKeyEntity -> {
                Mono<Long> activeOrdersMono = orderRepository.countActiveByApiKey(apiKey);
                Mono<List<OrderSummary>> recentOrdersMono = getRecentOrdersWithServiceNames(apiKey, 10);

                return Mono.zip(activeOrdersMono, recentOrdersMono)
                    .map(tuple -> CustomerSummary.of(
                        apiKey,
                        apiKeyEntity.getUserName(),
                        apiKeyEntity.getBalance(),
                        tuple.getT1(),
                        apiKeyEntity.getTotalSpent(),
                        tuple.getT2()
                    ));
            })
            .map(ResponseEntity::ok)
            .onErrorResume(e -> {
                log.error("Error getting summary: {}", e.getMessage());
                return Mono.just(ResponseEntity.badRequest().build());
            });
    }

    // ==================== ORDERS ====================

    /**
     * GET /api/customer/{apiKey}/orders
     * Full order history with service names.
     */
    @GetMapping("/{apiKey}/orders")
    public Flux<OrderSummary> getOrders(@PathVariable String apiKey) {
        log.info("Getting orders for key: {}...", apiKey.substring(0, Math.min(8, apiKey.length())));

        return orderRepository.findByApiKeyOrderByUpdatedAtDesc(apiKey)
            .flatMap(this::enrichOrderWithServiceName);
    }

    /**
     * GET /api/customer/{apiKey}/orders/{orderId}
     * Single order details.
     */
    @GetMapping("/{apiKey}/orders/{orderId}")
    public Mono<ResponseEntity<OrderSummary>> getOrder(@PathVariable String apiKey, 
                                                        @PathVariable UUID orderId) {
        return orderRepository.findById(orderId)
            .filter(order -> apiKey.equals(order.getApiKey()))
            .flatMap(this::enrichOrderWithServiceName)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // ==================== SERVICES ====================

    /**
     * GET /api/customer/services
     * Available services for order form.
     */
    @GetMapping("/services")
    public Flux<ServiceDto> getServices() {
        return serviceRepository.findByIsActiveTrue()
            .map(this::toServiceDto);
    }

    /**
     * GET /api/customer/services/{serviceId}
     * Single service details.
     */
    @GetMapping("/services/{serviceId}")
    public Mono<ResponseEntity<ServiceDto>> getService(@PathVariable int serviceId) {
        return serviceRepository.findById(serviceId)
            .map(this::toServiceDto)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // ==================== CHECKOUT ====================

    /**
     * POST /api/customer/checkout
     * Place new order with balance deduction.
     */
    @PostMapping("/checkout")
    public Mono<ResponseEntity<ApiResponse>> checkout(@RequestBody CheckoutRequest request) {
        log.info("Checkout: apiKey={}..., serviceId={}, quantity={}",
            request.apiKey().substring(0, Math.min(8, request.apiKey().length())),
            request.serviceId(),
            request.quantity());

        return serviceRepository.findById(request.serviceId())
            .switchIfEmpty(Mono.error(new RuntimeException("Service not found")))
            .flatMap(service -> {
                // Validate quantity
                if (request.quantity() < service.getMinQuantity()) {
                    return Mono.just(ResponseEntity.badRequest()
                        .body(ApiResponse.error("Minimum quantity is " + service.getMinQuantity())));
                }
                if (request.quantity() > service.getMaxQuantity()) {
                    return Mono.just(ResponseEntity.badRequest()
                        .body(ApiResponse.error("Maximum quantity is " + service.getMaxQuantity())));
                }

                // Calculate charge
                BigDecimal charge = service.getPricePer1000()
                    .multiply(BigDecimal.valueOf(request.quantity()))
                    .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);

                // Charge balance and create order
                return apiKeyRepository.chargeBalance(request.apiKey(), charge)
                    .flatMap(updated -> {
                        if (updated == 0) {
                            return Mono.just(ResponseEntity.badRequest()
                                .body(ApiResponse.error("Insufficient balance")));
                        }

                        // Create order
                        OrderEntity order = new OrderEntity(
                            request.apiKey(),
                            request.serviceId(),
                            request.link(),
                            request.quantity(),
                            charge,
                            "Pending"
                        );

                        return orderRepository.save(order)
                            .map(saved -> ResponseEntity.ok(ApiResponse.orderCreated(saved.getId())));
                    });
            })
            .onErrorResume(e -> {
                log.error("Checkout error: {}", e.getMessage());
                return Mono.just(ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage())));
            });
    }

    // ==================== BALANCE ====================

    /**
     * GET /api/customer/{apiKey}/balance
     * Quick balance check.
     */
    @GetMapping("/{apiKey}/balance")
    public Mono<ResponseEntity<Map<String, Object>>> getBalance(@PathVariable String apiKey) {
        return apiKeyRepository.findByApiKey(apiKey)
            .map(entity -> ResponseEntity.ok(Map.<String, Object>of(
                "balance", entity.getBalance(),
                "totalSpent", entity.getTotalSpent()
            )))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // ==================== HELPERS ====================

    private Mono<List<OrderSummary>> getRecentOrdersWithServiceNames(String apiKey, int limit) {
        return orderRepository.findRecentByApiKey(apiKey, limit)
            .flatMap(this::enrichOrderWithServiceName)
            .collectList();
    }

    private Mono<OrderSummary> enrichOrderWithServiceName(OrderEntity order) {
        return serviceRepository.findById(order.getServiceId())
            .map(service -> new OrderSummary(
                order.getId(),
                service.getName(),
                order.getLink(),
                order.getQuantity(),
                order.getCharged(),
                order.getStatus(),
                order.getProgress(),
                order.getDeliveredQuantity(),
                OrderSummary.calculateEta(order.getStartedAt(), service.getDeliveryHours(), order.getStatus()),
                order.getCreatedAt(),
                order.getUpdatedAt()
            ))
            .defaultIfEmpty(new OrderSummary(
                order.getId(),
                "Unknown Service",
                order.getLink(),
                order.getQuantity(),
                order.getCharged(),
                order.getStatus(),
                order.getProgress(),
                order.getDeliveredQuantity(),
                "N/A",
                order.getCreatedAt(),
                order.getUpdatedAt()
            ));
    }

    private ServiceDto toServiceDto(ServiceEntity entity) {
        return new ServiceDto(
            entity.getId(),
            entity.getServiceId(),
            entity.getName(),
            entity.getCategory(),
            entity.getPricePer1000(),
            entity.getDeliveryHours(),
            entity.getMinQuantity(),
            entity.getMaxQuantity(),
            entity.getNeonColor(),
            entity.getSpeedTier(),
            entity.getGeoTarget(),
            entity.getDescription()
        );
    }
}
