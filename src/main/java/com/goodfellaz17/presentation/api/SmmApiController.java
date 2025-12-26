package com.goodfellaz17.presentation.api;

import com.goodfellaz17.application.command.PlaceOrderCommand;
import com.goodfellaz17.application.response.OrderResponse;
import com.goodfellaz17.application.service.BotOrchestratorService;
import com.goodfellaz17.application.service.OrderService;
import com.goodfellaz17.presentation.dto.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Presentation Layer - SMM Panel API v2 Controller.
 * 
 * Implements standard SMM API v2 specification for panel integration.
 * Endpoints: /api/v2/add, /api/v2/status, /api/v2/services, etc.
 */
@RestController
@RequestMapping("/api/v2")
public class SmmApiController {

    private static final Logger log = LoggerFactory.getLogger(SmmApiController.class);

    private final OrderService orderService;
    private final BotOrchestratorService orchestratorService;

    public SmmApiController(OrderService orderService, BotOrchestratorService orchestratorService) {
        this.orderService = orderService;
        this.orchestratorService = orchestratorService;
    }

    /**
     * POST /api/v2/add - Place a new order.
     * 
     * SMM Panel spec endpoint for creating stream orders.
     */
    @PostMapping("/add")
    public ResponseEntity<AddOrderResponse> addOrder(@Valid @RequestBody AddOrderRequest request) {
        log.info("API: Add order - service={}, link={}, quantity={}", 
                request.service(), request.link(), request.quantity());

        // Map API request to application command
        PlaceOrderCommand command = request.toCommand();
        OrderResponse order = orderService.placeOrder(command);

        return ResponseEntity.ok(AddOrderResponse.success(order.id()));
    }

    /**
     * GET /api/v2/status - Get order status.
     * 
     * Returns current order progress and delivery status.
     */
    @GetMapping("/status")
    public ResponseEntity<OrderStatusResponse> getStatus(@RequestParam UUID order) {
        log.debug("API: Get status - orderId={}", order);

        OrderResponse orderResponse = orderService.getOrderStatus(order);
        return ResponseEntity.ok(OrderStatusResponse.from(orderResponse));
    }

    /**
     * POST /api/v2/status - Batch status check (SMM Panel spec).
     */
    @PostMapping("/status")
    public ResponseEntity<BatchStatusResponse> getBatchStatus(@RequestBody BatchStatusRequest request) {
        log.debug("API: Batch status - orders={}", request.orders().size());

        List<OrderStatusResponse> statuses = request.orders().stream()
                .map(orderService::getOrderStatus)
                .map(OrderStatusResponse::from)
                .toList();

        return ResponseEntity.ok(new BatchStatusResponse(statuses));
    }

    /**
     * GET /api/v2/services - List available services.
     * 
     * Returns service catalog with pricing.
     */
    @GetMapping("/services")
    public ResponseEntity<ServicesResponse> getServices() {
        log.debug("API: Get services");
        return ResponseEntity.ok(ServicesResponse.defaultCatalog());
    }

    /**
     * POST /api/v2/cancel - Cancel an order.
     */
    @PostMapping("/cancel")
    public ResponseEntity<CancelResponse> cancelOrder(@RequestBody CancelRequest request) {
        log.info("API: Cancel order - orderId={}", request.order());

        try {
            orderService.cancelOrder(request.order());
            return ResponseEntity.ok(CancelResponse.success());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(CancelResponse.error(e.getMessage()));
        }
    }

    /**
     * GET /api/v2/balance - Get API user balance (placeholder).
     */
    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> getBalance(@RequestParam String key) {
        // In production, validate API key and return user balance
        return ResponseEntity.ok(new BalanceResponse("1000.00", "USD"));
    }

    /**
     * GET /api/v2/stats - Get execution statistics (internal).
     */
    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> getStats() {
        var stats = orchestratorService.getStats();
        return ResponseEntity.ok(StatsResponse.from(stats));
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
