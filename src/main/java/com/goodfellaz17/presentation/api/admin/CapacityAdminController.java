package com.goodfellaz17.presentation.api.admin;

import com.goodfellaz17.application.service.CapacityService;
import com.goodfellaz17.application.service.CapacityService.CapacitySnapshot;
import com.goodfellaz17.application.service.CapacityService.CanAcceptResult;
import com.goodfellaz17.application.service.CapacityService.TierCapacity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Admin API for Capacity Planning.
 * 
 * Endpoints:
 *   GET  /api/admin/capacity           - Get current capacity snapshot
 *   GET  /api/admin/capacity/check     - Check if a specific quantity can be accepted
 *   POST /api/admin/capacity/simulate  - Simulate low capacity (for testing rejection)
 * 
 * Used by ops team to monitor system capacity and make decisions
 * about accepting new 15k packages.
 */
@RestController
@RequestMapping("/api/admin/capacity")
public class CapacityAdminController {
    
    private static final Logger log = LoggerFactory.getLogger(CapacityAdminController.class);
    
    private final CapacityService capacityService;
    
    public CapacityAdminController(CapacityService capacityService) {
        this.capacityService = capacityService;
    }
    
    // =========================================================================
    // CAPACITY ENDPOINTS
    // =========================================================================
    
    /**
     * Get current capacity snapshot.
     * 
     * GET /api/admin/capacity
     * 
     * Returns:
     * - playsPerHour: Estimated safe plays/hour
     * - max48hCapacity: Max plays in 48 hours
     * - max72hCapacity: Max plays in 72 hours
     * - canAccept15kOrder: Whether a 15k order can be accepted now
     * - tierBreakdown: Capacity per proxy tier
     * 
     * Example:
     * curl http://localhost:8080/api/admin/capacity | jq .
     */
    @GetMapping
    public Mono<ResponseEntity<CapacityResponse>> getCapacity() {
        log.info("GET /api/admin/capacity - Calculating current capacity");
        
        return capacityService.calculateCurrentCapacity()
            .flatMap(snapshot -> capacityService.calculatePendingLoad()
                .map(pendingPlays -> {
                    // Apply simulation override if set
                    Integer simulated = capacityService.getSimulatedCapacity();
                    int effectivePlaysPerHour = simulated != null 
                        ? simulated 
                        : snapshot.totalPlaysPerHour();
                    
                    int effectiveMax48h = effectivePlaysPerHour * 48;
                    int effectiveMax72h = effectivePlaysPerHour * 72;
                    int available72h = effectiveMax72h - pendingPlays;
                    int available48h = effectiveMax48h - pendingPlays;
                    
                    return new CapacityResponse(
                        effectivePlaysPerHour,
                        effectiveMax48h,
                        effectiveMax72h,
                        pendingPlays,
                        available72h,
                        available48h,
                        available72h >= 15000,
                        snapshot.healthyProxyCount(),
                        snapshot.totalProxyCount(),
                        snapshot.tierCapacities(),
                        Instant.now(),
                        simulated != null
                    );
                }))
            .map(ResponseEntity::ok);
    }
    
    /**
     * Check if a specific quantity can be accepted.
     * 
     * GET /api/admin/capacity/check?quantity=15000
     * 
     * Example:
     * curl "http://localhost:8080/api/admin/capacity/check?quantity=15000" | jq .
     */
    @GetMapping("/check")
    public Mono<ResponseEntity<CapacityCheckResponse>> checkCapacity(
            @RequestParam(defaultValue = "15000") int quantity) {
        
        log.info("GET /api/admin/capacity/check - Checking capacity for {} plays", quantity);
        
        // If simulated low capacity is set, apply it
        if (capacityService.getSimulatedCapacity() != null) {
            int simPlaysPerHour = capacityService.getSimulatedCapacity();
            int simMax72h = simPlaysPerHour * 72;
            int simMax48h = simPlaysPerHour * 48;
            
            return capacityService.calculatePendingLoad()
                .map(pendingPlays -> {
                    int available72h = simMax72h - pendingPlays;
                    int available48h = simMax48h - pendingPlays;
                    boolean accepted = quantity <= available72h;
                    boolean withinTarget = quantity <= available48h;
                    
                    double hoursNeeded = simPlaysPerHour > 0 
                        ? (double) quantity / simPlaysPerHour 
                        : Double.MAX_VALUE;
                    Instant eta = Instant.now().plusSeconds((long)(hoursNeeded * 3600));
                    
                    return ResponseEntity.ok(new CapacityCheckResponse(
                        accepted,
                        withinTarget,
                        quantity,
                        available72h,
                        available48h,
                        pendingPlays,
                        hoursNeeded,
                        eta,
                        accepted ? null : buildRejectionReason(quantity, available72h),
                        true
                    ));
                });
        }
        
        return capacityService.canAccept(quantity)
            .map(result -> ResponseEntity.ok(new CapacityCheckResponse(
                result.accepted(),
                result.withinTarget(),
                result.requestedQuantity(),
                result.availableCapacity72h(),
                result.availableCapacity48h(),
                result.pendingPlays(),
                result.estimatedHours(),
                result.estimatedCompletion(),
                result.rejectionReason(),
                false
            )));
    }
    
    /**
     * Simulate low capacity for testing rejection behavior.
     * 
     * POST /api/admin/capacity/simulate
     * 
     * Body: { "playsPerHour": 50 }  // Set to simulate ~3600 max in 72h
     * Body: { "playsPerHour": null } // Clear simulation
     * 
     * Example (enable simulation):
     * curl -X POST http://localhost:8080/api/admin/capacity/simulate \
     *   -H "Content-Type: application/json" \
     *   -d '{"playsPerHour": 50}'
     * 
     * Example (disable simulation):
     * curl -X POST http://localhost:8080/api/admin/capacity/simulate \
     *   -H "Content-Type: application/json" \
     *   -d '{"playsPerHour": null}'
     */
    @PostMapping("/simulate")
    public ResponseEntity<SimulationResponse> simulateCapacity(
            @RequestBody SimulateRequest request) {
        
        Integer oldValue = capacityService.getSimulatedCapacity();
        capacityService.setSimulatedCapacity(request.playsPerHour);
        
        if (request.playsPerHour != null) {
            log.warn("⚠️  CAPACITY SIMULATION ENABLED: {} plays/hr (max {}k in 72h)",
                request.playsPerHour, request.playsPerHour * 72 / 1000);
        } else {
            log.info("Capacity simulation DISABLED - using real capacity");
        }
        
        return ResponseEntity.ok(new SimulationResponse(
            request.playsPerHour != null,
            request.playsPerHour,
            oldValue,
            request.playsPerHour != null 
                ? "Capacity simulation ENABLED. Orders will be rejected if exceeding " + 
                  (request.playsPerHour * 72) + " plays (72h max)."
                : "Capacity simulation DISABLED. Real capacity will be used."
        ));
    }
    
    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    private String buildRejectionReason(int requested, int available) {
        int deficit = requested - available;
        return String.format(
            "Package capacity full. Requested: %,d plays, Available (72h): %,d. " +
            "Need %,d more capacity. Try again later or reduce quantity.",
            requested, Math.max(0, available), deficit);
    }
    
    // =========================================================================
    // RESPONSE DTOS
    // =========================================================================
    
    /**
     * Full capacity snapshot response.
     */
    public record CapacityResponse(
        int playsPerHour,
        int max48hCapacity,
        int max72hCapacity,
        int pendingPlays,
        int available72h,
        int available48h,
        boolean canAccept15kOrder,
        int healthyProxyCount,
        int totalProxyCount,
        Map<String, TierCapacity> tierBreakdown,
        Instant calculatedAt,
        boolean isSimulated
    ) {}
    
    /**
     * Capacity check result for specific quantity.
     */
    public record CapacityCheckResponse(
        boolean accepted,
        boolean withinTarget,
        int requestedQuantity,
        int availableCapacity72h,
        int availableCapacity48h,
        int pendingPlays,
        double estimatedHours,
        Instant estimatedCompletion,
        String rejectionReason,
        boolean isSimulated
    ) {}
    
    /**
     * Request to simulate capacity constraints.
     */
    public record SimulateRequest(
        Integer playsPerHour
    ) {}
    
    /**
     * Response from simulation endpoint.
     */
    public record SimulationResponse(
        boolean simulationActive,
        Integer currentSimulatedPlaysPerHour,
        Integer previousValue,
        String message
    ) {}
}
