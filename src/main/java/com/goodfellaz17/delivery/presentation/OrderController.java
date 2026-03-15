package com.goodfellaz17.delivery.presentation;

import com.goodfellaz17.delivery.application.OrderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * OrderController — REST API for order management.
 * Slice A endpoints: POST /api/orders, GET /api/orders/{id}
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Create a new order.
     *
     * @param request Order creation request
     * @return 201 Created with order details
     */
    @PostMapping
    public Mono<ResponseEntity<OrderService.OrderResponse>> createOrder(
            @Valid @RequestBody OrderService.OrderCreateRequest request) {
        log.info("Received order creation request with tenantId={}, serviceId={}, quantity={}", 
                request.getTenantId(), request.getServiceId(), request.getQuantity());
        
        return orderService.createOrder(request)
                .flatMap(order -> {
                    log.info("Order created successfully: {}", order.getId());
                    return orderService.getOrder(order.getId());
                })
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .doOnError(e -> log.error("Error creating order", e));
    }

    /**
     * Get order details with task statistics.
     *
     * @param orderId Order UUID
     * @return 200 OK with order response, or 404 if not found
     */
    @GetMapping("/{orderId}")
    public Mono<ResponseEntity<OrderService.OrderResponse>> getOrder(
            @PathVariable UUID orderId) {
        log.info("Fetching order: {}", orderId);
        return orderService.getOrder(orderId)
                .map(response -> {
                    log.info("Order found: {}", orderId);
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    log.warn("Order not found: {}", orderId);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }
}
