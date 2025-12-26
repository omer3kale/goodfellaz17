package com.goodfellaz17.presentation.api;

import com.goodfellaz17.infrastructure.persistence.entity.OrderEntity;
import com.goodfellaz17.infrastructure.persistence.repository.ApiKeyRepository;
import com.goodfellaz17.infrastructure.persistence.repository.OrderRepository;
import com.goodfellaz17.infrastructure.persistence.repository.ServiceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API V2 Controller - Reseller API with status endpoint.
 * 
 * Compatible with SMM panel APIs:
 * - GET /api/v2?key=&action=services → List services
 * - GET /api/v2?key=&action=balance → Get balance
 * - GET /api/v2?key=&action=status&order= → Order status
 * - GET /api/v2/status?key= → All orders status (new)
 * - POST /api/v2?key=&action=add → Place order
 */
@RestController
@RequestMapping("/api/v2")
@CrossOrigin(origins = "*")
@Tag(name = "Reseller API", description = "SMM Panel compatible API for resellers")
public class ApiV2Controller {

    private static final Logger log = LoggerFactory.getLogger(ApiV2Controller.class);

    private final ApiKeyRepository apiKeyRepository;
    private final OrderRepository orderRepository;
    private final ServiceRepository serviceRepository;

    public ApiV2Controller(ApiKeyRepository apiKeyRepository,
                          OrderRepository orderRepository,
                          ServiceRepository serviceRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.orderRepository = orderRepository;
        this.serviceRepository = serviceRepository;
    }

    /**
     * GET /api/v2/status?key=botzzz_xxx
     * Get all orders status for an API key.
     * Used by resellers to track multiple orders.
     */
    @GetMapping("/status")
    @Operation(
        summary = "Get all orders status",
        description = "Returns status of all orders for the given API key with progress and ETA"
    )
    @ApiResponse(responseCode = "200", description = "Orders list returned")
    @ApiResponse(responseCode = "401", description = "Invalid API key")
    public Mono<ResponseEntity<Map<String, Object>>> getOrdersStatus(
            @Parameter(description = "API key (botzzz_xxx)", required = true)
            @RequestParam String key) {
        
        log.info("API v2 status request for key: {}...", key.substring(0, Math.min(10, key.length())));

        return apiKeyRepository.existsByApiKey(key)
            .flatMap(exists -> {
                if (!exists) {
                    return Mono.just(ResponseEntity.status(401).body(Map.<String, Object>of(
                        "error", "Invalid API key"
                    )));
                }

                return orderRepository.findByApiKeyOrderByUpdatedAtDesc(key)
                    .map(this::mapOrderStatus)
                    .collectList()
                    .map(orders -> ResponseEntity.ok(Map.<String, Object>of(
                        "success", true,
                        "count", orders.size(),
                        "orders", orders
                    )));
            });
    }

    /**
     * GET /api/v2?key=&action=status&order=
     * Get single order status (SMM Panel compatible).
     */
    @GetMapping
    @Operation(
        summary = "SMM Panel API",
        description = "Compatible with standard SMM panel API format"
    )
    public Mono<ResponseEntity<Object>> apiAction(
            @Parameter(description = "API key", required = true)
            @RequestParam String key,
            @Parameter(description = "Action: services, balance, status, add")
            @RequestParam(required = false) String action,
            @Parameter(description = "Order ID (for status action)")
            @RequestParam(required = false) String order,
            @Parameter(description = "Service ID (for add action)")
            @RequestParam(required = false) Integer service,
            @Parameter(description = "Target link (for add action)")
            @RequestParam(required = false) String link,
            @Parameter(description = "Quantity (for add action)")
            @RequestParam(required = false) Integer quantity) {

        log.info("API v2 request: action={}, key={}...", action, key.substring(0, Math.min(10, key.length())));

        return apiKeyRepository.existsByApiKey(key)
            .flatMap(exists -> {
                if (!exists) {
                    return Mono.just(ResponseEntity.status(401).body((Object) Map.of(
                        "error", "Invalid API key"
                    )));
                }

                if (action == null) {
                    return Mono.just(ResponseEntity.badRequest().body((Object) Map.of(
                        "error", "Action parameter required"
                    )));
                }

                return switch (action.toLowerCase()) {
                    case "services" -> getServices();
                    case "balance" -> getBalance(key);
                    case "status" -> getOrderStatus(order);
                    default -> Mono.just(ResponseEntity.badRequest().body((Object) Map.of(
                        "error", "Unknown action: " + action
                    )));
                };
            });
    }

    /**
     * Get all services list.
     */
    private Mono<ResponseEntity<Object>> getServices() {
        return serviceRepository.findAll()
            .map(service -> Map.<String, Object>of(
                "service", service.getServiceId(),
                "name", service.getName(),
                "category", service.getCategory() != null ? service.getCategory() : "General",
                "rate", service.getRate(),
                "min", service.getMinQuantity() != null ? service.getMinQuantity() : 100,
                "max", service.getMaxQuantity() != null ? service.getMaxQuantity() : 100000
            ))
            .collectList()
            .map(services -> ResponseEntity.ok((Object) services));
    }

    /**
     * Get balance for API key.
     */
    private Mono<ResponseEntity<Object>> getBalance(String key) {
        return apiKeyRepository.findBalanceByApiKey(key)
            .map(balance -> ResponseEntity.ok((Object) Map.of(
                "balance", balance,
                "currency", "USD"
            )))
            .defaultIfEmpty(ResponseEntity.ok((Object) Map.of(
                "balance", 0.0,
                "currency", "USD"
            )));
    }

    /**
     * Get single order status.
     */
    private Mono<ResponseEntity<Object>> getOrderStatus(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().body((Object) Map.of(
                "error", "Order ID required"
            )));
        }

        try {
            java.util.UUID uuid = java.util.UUID.fromString(orderId);
            return orderRepository.findById(uuid)
                .map(order -> ResponseEntity.ok((Object) mapOrderStatus(order)))
                .defaultIfEmpty(ResponseEntity.ok((Object) Map.of(
                    "error", "Order not found"
                )));
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest().body((Object) Map.of(
                "error", "Invalid order ID format"
            )));
        }
    }

    /**
     * Map OrderEntity to status response.
     */
    private Map<String, Object> mapOrderStatus(OrderEntity order) {
        Map<String, Object> status = new HashMap<>();
        status.put("order", order.getOrderId().toString());
        status.put("status", order.getStatus());
        
        // Calculate progress percentage
        int progress = 0;
        if (order.getQuantity() != null && order.getQuantity() > 0) {
            int delivered = order.getChargedCount() != null ? order.getChargedCount() : 0;
            progress = (delivered * 100) / order.getQuantity();
        }
        status.put("progress", progress);
        
        // Delivered / remaining
        status.put("start_count", 0);
        status.put("delivered", order.getChargedCount() != null ? order.getChargedCount() : 0);
        status.put("remains", order.getRemainingCount() != null ? order.getRemainingCount() : order.getQuantity());
        
        // ETA
        String eta = formatEta(order.getEtaMinutes());
        status.put("eta", eta);
        
        // Service info
        status.put("service_id", order.getServiceId());
        status.put("quantity", order.getQuantity());
        status.put("charge", order.getCharged());
        status.put("link", order.getLink());
        
        return status;
    }

    private String formatEta(Integer etaMinutes) {
        if (etaMinutes == null || etaMinutes <= 0) {
            return "N/A";
        }
        if (etaMinutes < 60) {
            return etaMinutes + "m";
        }
        int hours = etaMinutes / 60;
        int mins = etaMinutes % 60;
        if (hours < 24) {
            return hours + "h" + mins + "m";
        }
        int days = hours / 24;
        hours = hours % 24;
        return days + "d" + hours + "h";
    }
}
