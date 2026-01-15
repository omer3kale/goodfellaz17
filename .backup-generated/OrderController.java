package com.goodfellaz17.presentation.api;

import com.goodfellaz17.application.service.HybridBotOrchestrator;
import com.goodfellaz17.application.service.RoutingEngine;
import com.goodfellaz17.domain.model.*;
import com.goodfellaz17.infrastructure.persistence.OrderRepository;
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
 * PRODUCTION: Saves orders to Neon PostgreSQL.
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
    private final OrderRepository orderRepository;
    private final RoutingEngine routingEngine;
    
    // In-memory cache (synced with DB)
    private final Map<UUID, Order> ordersCache = new ConcurrentHashMap<>();

    public OrderController(
            Optional<HybridBotOrchestrator> hybridOrchestrator,
            OrderRepository orderRepository,
            RoutingEngine routingEngine) {
        this.hybridOrchestrator = hybridOrchestrator;
        this.orderRepository = orderRepository;
        this.routingEngine = routingEngine;
    }

    /**
     * Create new order - PWA order form submission.
     * REAL: Saves to Neon PostgreSQL.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest req) {
        log.info("📦 Order received: {} x{} {}", req.serviceId(), req.quantity(), req.geoTarget());

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

        // REAL: Save to Neon PostgreSQL
        try {
            Order savedOrder = orderRepository.save(order).block();
            if (savedOrder != null) {
                log.info("✅ Order saved to DB: {}", savedOrder.getId());
            }
        } catch (Exception e) {
            log.error("❌ DB save failed, using cache: {}", e.getMessage());
        }
        
        // Cache for fast lookups
        ordersCache.put(order.getId(), order);
        
        // Queue for hybrid delivery
        if (hybridOrchestrator.isPresent()) {
            hybridOrchestrator.get().queueOrder(order);
            log.info("🚀 Order queued for hybrid delivery: {}", order.getId());
        } else {
            order.startProcessing();
            log.info("⚠️ Order accepted (orchestrator not available): {}", order.getId());
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
     * Get order by ID - checks DB first, then cache.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID orderId) {
        // Check cache first
        Order order = ordersCache.get(orderId);
        
        // Fallback to DB
        if (order == null) {
            order = orderRepository.findById(orderId).block();
        }
        
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
     * Get all pending orders from DB.
     */
    @GetMapping("/pending")
    public List<OrderResponse> getPendingOrders() {
        // Get from DB
        List<Order> pendingOrders = orderRepository.findByStatus(OrderStatus.PENDING)
                .collectList().block();
        List<Order> processingOrders = orderRepository.findByStatus(OrderStatus.PROCESSING)
                .collectList().block();
        
        // Combine
        if (pendingOrders == null) pendingOrders = List.of();
        if (processingOrders == null) processingOrders = List.of();
        
        return java.util.stream.Stream.concat(
                pendingOrders.stream(),
                processingOrders.stream()
            )
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
     * Get all orders from DB (paginated in future).
     */
    @GetMapping
    public List<OrderResponse> getAllOrders() {
        List<Order> orders = orderRepository.findAll().collectList().block();
        if (orders == null) orders = List.of();
        
        return orders.stream()
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
