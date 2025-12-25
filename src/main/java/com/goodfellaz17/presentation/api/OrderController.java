package com.goodfellaz17.presentation.api;

import com.goodfellaz17.application.service.HybridBotOrchestrator;
import com.goodfellaz17.application.service.RoutingEngine;
import com.goodfellaz17.domain.model.*;
import com.goodfellaz17.domain.port.OrderRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Order Controller - PWA order placement + status tracking.
 * 
 * Handles the complete order lifecycle:
 * 1. Create order → CoCo validation → Queue for delivery
 * 2. Track pending orders
 * 3. Get order status by ID
 */
@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final Optional<HybridBotOrchestrator> hybridOrchestrator;
    private final Optional<OrderRepositoryPort> orderRepository;
    private final RoutingEngine routingEngine;
    
    // In-memory order tracking (production: use DB)
    private final Map<UUID, Order> orders = new ConcurrentHashMap<>();

    public OrderController(
            Optional<HybridBotOrchestrator> hybridOrchestrator,
            Optional<OrderRepositoryPort> orderRepository,
            RoutingEngine routingEngine) {
        this.hybridOrchestrator = hybridOrchestrator;
        this.orderRepository = orderRepository;
        this.routingEngine = routingEngine;
    }

    /**
     * Create new order - PWA order form submission.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest req) {
        log.info("Order received: {} x{} {}", req.serviceId(), req.quantity(), req.geoTarget());

        // Parse geo target
        GeoTarget geo = parseGeoTarget(req.geoTarget());
        SpeedTier speed = parseSpeedTier(req.speedTier());

        // Create order
        Order order = Order.builder()
            .id(UUID.randomUUID())
            .serviceId(req.serviceId())
            .serviceName(req.serviceName() != null ? req.serviceName() : req.serviceId())
            .trackUrl(req.trackUrl())
            .quantity(req.quantity())
            .geoTarget(geo)
            .speedTier(speed)
            .status(OrderStatus.PENDING)
            .build();

        // Store order
        orders.put(order.getId(), order);
        
        // Queue for hybrid delivery
        if (hybridOrchestrator.isPresent()) {
            hybridOrchestrator.get().queueOrder(order);
            log.info("Order queued for hybrid delivery: {}", order.getId());
        } else {
            order.startProcessing();
            log.info("Order accepted (orchestrator not available): {}", order.getId());
        }

        // Get routing hint
        String routeHint = routingEngine.getRouteHint(req.serviceId());

        return ResponseEntity.ok(new OrderResponse(
            order.getId(),
            order.getServiceId(),
            order.getServiceName(),
            order.getQuantity(),
            order.getDelivered(),
            order.getStatus().name(),
            order.getProgress(),
            routeHint,
            order.getCreatedAt()
        ));
    }

    /**
     * Get order by ID.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID orderId) {
        Order order = orders.get(orderId);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        
        String routeHint = routingEngine.getRouteHint(order.getServiceId());
        
        return ResponseEntity.ok(new OrderResponse(
            order.getId(),
            order.getServiceId(),
            order.getServiceName(),
            order.getQuantity(),
            order.getDelivered(),
            order.getStatus().name(),
            order.getProgress(),
            routeHint,
            order.getCreatedAt()
        ));
    }

    /**
     * Get all pending orders.
     */
    @GetMapping("/pending")
    public List<OrderResponse> getPendingOrders() {
        return orders.values().stream()
            .filter(o -> o.getStatus() == OrderStatus.PENDING || o.getStatus() == OrderStatus.PROCESSING)
            .map(o -> new OrderResponse(
                o.getId(),
                o.getServiceId(),
                o.getServiceName(),
                o.getQuantity(),
                o.getDelivered(),
                o.getStatus().name(),
                o.getProgress(),
                routingEngine.getRouteHint(o.getServiceId()),
                o.getCreatedAt()
            ))
            .toList();
    }

    /**
     * Get all orders (paginated in production).
     */
    @GetMapping
    public List<OrderResponse> getAllOrders() {
        return orders.values().stream()
            .map(o -> new OrderResponse(
                o.getId(),
                o.getServiceId(),
                o.getServiceName(),
                o.getQuantity(),
                o.getDelivered(),
                o.getStatus().name(),
                o.getProgress(),
                routingEngine.getRouteHint(o.getServiceId()),
                o.getCreatedAt()
            ))
            .toList();
    }

    private GeoTarget parseGeoTarget(String geo) {
        if (geo == null) return GeoTarget.WORLDWIDE;
        return switch (geo.toUpperCase()) {
            case "US", "USA" -> GeoTarget.USA;
            case "UK", "GB", "EU" -> GeoTarget.EU;
            default -> GeoTarget.WORLDWIDE;
        };
    }

    private SpeedTier parseSpeedTier(String tier) {
        if (tier == null) return SpeedTier.NORMAL;
        return switch (tier.toUpperCase()) {
            case "VIP" -> SpeedTier.VIP;
            case "FAST" -> SpeedTier.FAST;
            default -> SpeedTier.NORMAL;
        };
    }

    // === DTOs ===

    public record CreateOrderRequest(
        String serviceId,
        String serviceName,
        String trackUrl,
        int quantity,
        String geoTarget,
        String speedTier
    ) {}

    public record OrderResponse(
        UUID orderId,
        String serviceId,
        String serviceName,
        int quantity,
        int delivered,
        String status,
        double progress,
        String routeHint,
        Instant createdAt
    ) {}
}
