package com.goodfellaz17.presentation.api;

import com.goodfellaz17.infrastructure.persistence.repository.ApiKeyRepository;
import com.goodfellaz17.infrastructure.persistence.repository.OrderRepository;
import com.goodfellaz17.infrastructure.persistence.repository.ServiceRepository;
import com.goodfellaz17.presentation.dto.AdminStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin Dashboard API Controller.
 *
 * Endpoints for admin panel:
 * - GET /api/admin/stats - Full dashboard stats
 * - GET /api/admin/revenue - Revenue metrics
 * - GET /api/admin/orders/active - Active orders count
 */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminDashboardController {

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardController.class);

    private final OrderRepository orderRepository;
    private final ServiceRepository serviceRepository;
    private final ApiKeyRepository apiKeyRepository;

    public AdminDashboardController(OrderRepository orderRepository,
                                     ServiceRepository serviceRepository,
                                     ApiKeyRepository apiKeyRepository) {
        this.orderRepository = orderRepository;
        this.serviceRepository = serviceRepository;
        this.apiKeyRepository = apiKeyRepository;
    }

    // ==================== FULL STATS ====================

    /**
     * GET /api/admin/stats
     * Complete admin dashboard statistics.
     */
    @GetMapping("/stats")
    public Mono<ResponseEntity<AdminStats>> getStats() {
        log.info("Getting admin stats");

        // Split into two zip calls since Mono.zip only supports up to 8 args
        Mono<BigDecimal[]> revenuesMono = Mono.zip(
            orderRepository.totalRevenue().defaultIfEmpty(BigDecimal.ZERO),
            orderRepository.revenueLast30Days().defaultIfEmpty(BigDecimal.ZERO),
            orderRepository.revenueToday().defaultIfEmpty(BigDecimal.ZERO)
        ).map(t -> new BigDecimal[]{t.getT1(), t.getT2(), t.getT3()});

        Mono<Long[]> countsMono = Mono.zip(
            orderRepository.ordersToday().defaultIfEmpty(0L),
            orderRepository.activeOrderCount().defaultIfEmpty(0L),
            orderRepository.completedOrderCount().defaultIfEmpty(0L)
        ).map(t -> new Long[]{t.getT1(), t.getT2(), t.getT3()});

        return Mono.zip(
            revenuesMono,
            countsMono,
            calculateDeliveryRate24h(),
            getTopServices(5),
            getDailyRevenue()
        ).map(tuple -> {
            BigDecimal[] revenues = tuple.getT1();
            Long[] counts = tuple.getT2();

            return new AdminStats(
                revenues[0],           // totalRevenue
                revenues[1],           // revenue30d
                revenues[2],           // revenueToday
                counts[0],             // ordersToday
                counts[1],             // activeOrders
                counts[2],             // completedOrders
                tuple.getT3(),         // deliveryRate24h
                tuple.getT4(),         // topServices
                tuple.getT5()          // dailyRevenue
            );
        }).map(ResponseEntity::ok);
    }

    // ==================== INDIVIDUAL METRICS ====================

    /**
     * GET /api/admin/revenue
     * Revenue metrics only.
     */
    @GetMapping("/revenue")
    public Mono<ResponseEntity<Map<String, Object>>> getRevenue() {
        return Mono.zip(
            orderRepository.totalRevenue().defaultIfEmpty(BigDecimal.ZERO),
            orderRepository.revenueLast30Days().defaultIfEmpty(BigDecimal.ZERO),
            orderRepository.revenueToday().defaultIfEmpty(BigDecimal.ZERO)
        ).map(tuple -> ResponseEntity.ok(Map.<String, Object>of(
            "total", tuple.getT1(),
            "last30d", tuple.getT2(),
            "today", tuple.getT3()
        )));
    }

    /**
     * GET /api/admin/orders/active
     * Active orders count.
     */
    @GetMapping("/orders/active")
    public Mono<ResponseEntity<Map<String, Long>>> getActiveOrders() {
        return orderRepository.activeOrderCount()
            .defaultIfEmpty(0L)
            .map(count -> ResponseEntity.ok(Map.of("active", count)));
    }

    /**
     * GET /api/admin/orders/today
     * Today's order count.
     */
    @GetMapping("/orders/today")
    public Mono<ResponseEntity<Map<String, Long>>> getOrdersToday() {
        return orderRepository.ordersToday()
            .defaultIfEmpty(0L)
            .map(count -> ResponseEntity.ok(Map.of("today", count)));
    }

    /**
     * GET /api/admin/customers
     * Total customers count.
     */
    @GetMapping("/customers")
    public Mono<ResponseEntity<Map<String, Long>>> getCustomersCount() {
        return apiKeyRepository.count()
            .map(count -> ResponseEntity.ok(Map.of("total", count)));
    }

    // ==================== HELPERS ====================

    private Mono<Double> calculateDeliveryRate24h() {
        return Mono.zip(
            orderRepository.ordersCompletedWithin24h().defaultIfEmpty(0L),
            orderRepository.completedOrderCount().defaultIfEmpty(0L)
        ).map(tuple -> {
            if (tuple.getT2() == 0) return 0.0;
            return (tuple.getT1() * 100.0) / tuple.getT2();
        });
    }

    private Mono<List<AdminStats.ServiceStats>> getTopServices(int limit) {
        return orderRepository.topServicesByOrderCount(limit)
            .flatMap(count ->
                serviceRepository.findByServiceId(count.getServiceId())
                    .map(service -> new AdminStats.ServiceStats(
                        service.getId(),
                        service.getName(),
                        count.getOrderCount(),
                        calculateServiceRevenue(service, count.getOrderCount())
                    ))
            )
            .collectList()
            .defaultIfEmpty(new ArrayList<>());
    }

    /**
     * Calculate estimated revenue for a service based on order count.
     * Estimate: avgOrderQuantity = 5000, revenue = quantity * rate / 1000
     */
    private double calculateServiceRevenue(com.goodfellaz17.infrastructure.persistence.entity.ServiceEntity service, Long orderCount) {
        if (service.getRate() == null || orderCount == null) return 0.0;
        // Estimate: avgOrderQuantity = 5000, avgRevenue = quantity * rate / 1000
        double avgRevenue = 5000 * service.getRate().doubleValue() / 1000;
        return avgRevenue * orderCount;
    }

    private Mono<Map<String, BigDecimal>> getDailyRevenue() {
        // Simplified: return empty map for now, will be enhanced with actual daily aggregation
        return Mono.just(new LinkedHashMap<>());
    }
}
