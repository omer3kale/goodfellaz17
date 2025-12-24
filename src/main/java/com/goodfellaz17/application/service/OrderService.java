package com.goodfellaz17.application.service;

import com.goodfellaz17.application.command.PlaceOrderCommand;
import com.goodfellaz17.application.response.OrderResponse;
import com.goodfellaz17.domain.model.Order;
import com.goodfellaz17.domain.port.OrderRepositoryPort;
import com.goodfellaz17.domain.service.SpotifyComplianceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Application Service - Order management use cases.
 * 
 * Orchestrates order lifecycle: create → validate → process → complete.
 * Uses domain ports for persistence (Hexagonal Architecture).
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepositoryPort orderRepository;
    private final BotOrchestratorService orchestratorService;
    private final SpotifyComplianceService complianceService;

    public OrderService(OrderRepositoryPort orderRepository,
                        BotOrchestratorService orchestratorService,
                        SpotifyComplianceService complianceService) {
        this.orderRepository = orderRepository;
        this.orchestratorService = orchestratorService;
        this.complianceService = complianceService;
    }

    /**
     * Place a new order and queue for bot execution.
     */
    public OrderResponse placeOrder(PlaceOrderCommand command) {
        Order order = Order.create(
                command.trackUrl(),
                command.quantity(),
                command.geoTarget(),
                command.speedTier()
        );

        // Validate compliance before accepting
        complianceService.validateOrder(order);
        
        orderRepository.save(order);
        log.info("Order created: id={}, quantity={}, geo={}", 
                order.getId(), order.getQuantity(), order.getGeoTarget());

        // Queue for async bot execution
        orchestratorService.queueOrder(order);

        return OrderResponse.from(order);
    }

    /**
     * Get order status by ID.
     */
    public OrderResponse getOrderStatus(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        return OrderResponse.from(order);
    }

    /**
     * Cancel an order.
     */
    public void cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        order.cancel();
        orderRepository.save(order);
        log.info("Order cancelled: id={}", orderId);
    }
}
