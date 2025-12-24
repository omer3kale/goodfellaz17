package com.goodfellaz17.presentation.api;

import com.goodfellaz17.cocos.CoCoCollector;
import com.goodfellaz17.cocos.CoCoViolationException;
import com.goodfellaz17.cocos.order.OrderCoCoChecker;
import com.goodfellaz17.cocos.order.OrderContext;
import com.goodfellaz17.prettyprinter.SmmPrettyPrinter;
import com.goodfellaz17.symboltable.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SMM Provider API v2 - Symbol Table Powered.
 * 
 * MontiCore-inspired endpoints with O(1) symbol resolution.
 * Implements standard SMM API v2 specification with SMI backend.
 * 
 * Endpoints:
 * - POST /api/v2 (action=services) → List all services
 * - POST /api/v2 (action=add) → Place order
 * - POST /api/v2 (action=status) → Get order status
 * - GET  /api/v2/symbols/* → Symbol table inspection
 */
@RestController
@RequestMapping("/api/v2")
public class SmmProviderController {
    
    private static final Logger log = LoggerFactory.getLogger(SmmProviderController.class);
    
    private final SmmSymbolTable symbolTable;
    private final OrderCoCoChecker cocoChecker;
    private final SmmPrettyPrinter prettyPrinter;
    
    public SmmProviderController(SmmSymbolTable symbolTable,
                                  OrderCoCoChecker cocoChecker,
                                  SmmPrettyPrinter prettyPrinter) {
        this.symbolTable = symbolTable;
        this.cocoChecker = cocoChecker;
        this.prettyPrinter = prettyPrinter;
    }
    
    // ==================== SMM Provider API v2 ====================
    
    /**
     * SMM Provider main endpoint (action-based).
     * POST /api/v2?key={apiKey}&action={action}
     */
    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> handleAction(
            @RequestParam String key,
            @RequestParam String action,
            @RequestBody(required = false) Map<String, Object> body) {
        
        log.info("API request: action={}, key={}...", action, key.substring(0, 8));
        
        return switch (action.toLowerCase()) {
            case "services" -> getServices();
            case "add" -> addOrder(key, body);
            case "status" -> getOrderStatus(key, body);
            case "balance" -> getBalance(key);
            default -> Mono.just(errorResponse("Invalid action: " + action));
        };
    }
    
    /**
     * GET /api/v2/services - List all available services.
     */
    @GetMapping("/services")
    public Mono<ResponseEntity<Map<String, Object>>> getServicesGet() {
        return getServices();
    }
    
    private Mono<ResponseEntity<Map<String, Object>>> getServices() {
        List<ServiceSymbol> services = symbolTable.getAllServices();
        
        List<Map<String, Object>> serviceList = services.stream()
                .filter(ServiceSymbol::isActive)
                .map(this::serviceToMap)
                .toList();
        
        return Mono.just(ResponseEntity.ok(Map.of(
                "services", serviceList,
                "count", serviceList.size()
        )));
    }
    
    /**
     * Add a new order (action=add).
     */
    private Mono<ResponseEntity<Map<String, Object>>> addOrder(String apiKey, Map<String, Object> body) {
        try {
            // Extract parameters
            int serviceId = getInt(body, "service");
            String link = getString(body, "link");
            int quantity = getInt(body, "quantity");
            
            // Resolve service from symbol table (O(1))
            ServiceSymbol service = symbolTable.resolveService(serviceId);
            
            // Get or create tenant scope
            ArtifactScope tenantScope = symbolTable.getTenantScope(apiKey);
            
            // Get or create API key symbol (would normally come from DB)
            ApiKeySymbol apiKeySymbol = tenantScope.<ApiKeySymbol>resolveLocally(apiKey, SymbolKind.API_KEY)
                    .orElseGet(() -> {
                        ApiKeySymbol newKey = new ApiKeySymbol(apiKey, UUID.randomUUID(), new BigDecimal("100.00"));
                        tenantScope.add(newKey);
                        return newKey;
                    });
            
            // Create order context and run CoCos
            OrderContext context = OrderContext.builder()
                    .apiKey(apiKeySymbol)
                    .service(service)
                    .trackUrl(link)
                    .quantity(quantity)
                    .deliveryHours(24)
                    .build();
            
            // Validate with CoCos
            cocoChecker.checkAll(context);
            
            // Create order symbol
            UUID orderId = UUID.randomUUID();
            OrderSymbol orderSymbol = new OrderSymbol(orderId, serviceId, link, quantity);
            orderSymbol.setStatus("PENDING");
            
            // Track order in tenant scope
            symbolTable.trackOrder(apiKey, orderSymbol);
            
            // Deduct balance
            BigDecimal cost = service.calculateCost(quantity);
            apiKeySymbol.deductBalance(cost);
            
            log.info("Order created: id={}, service={}, quantity={}", orderId, serviceId, quantity);
            
            return Mono.just(ResponseEntity.ok(Map.of(
                    "order", orderId.toString(),
                    "service", serviceId,
                    "link", link,
                    "quantity", quantity,
                    "charge", cost.doubleValue(),
                    "status", "Pending"
            )));
            
        } catch (GlobalScope.ServiceNotFoundException e) {
            return Mono.just(errorResponse("Service not found: " + e.getServiceId()));
        } catch (CoCoViolationException e) {
            return Mono.just(errorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating order", e);
            return Mono.just(errorResponse("Error: " + e.getMessage()));
        }
    }
    
    /**
     * Get order status (action=status).
     */
    private Mono<ResponseEntity<Map<String, Object>>> getOrderStatus(String apiKey, Map<String, Object> body) {
        String orderId = getString(body, "order");
        
        return symbolTable.resolveOrder(apiKey, orderId)
                .map(order -> ResponseEntity.ok(Map.<String, Object>of(
                        "order", order.getOrderId().toString(),
                        "status", order.getStatus(),
                        "charge", "0.00",
                        "start_count", order.getDelivered(),
                        "remains", order.getRemaining()
                )))
                .map(Mono::just)
                .orElse(Mono.just(errorResponse("Order not found: " + orderId)));
    }
    
    /**
     * Get API key balance (action=balance).
     */
    private Mono<ResponseEntity<Map<String, Object>>> getBalance(String apiKey) {
        return symbolTable.findTenantScope(apiKey)
                .flatMap(scope -> scope.<ApiKeySymbol>resolveLocally(apiKey, SymbolKind.API_KEY))
                .map(key -> ResponseEntity.ok(Map.<String, Object>of(
                        "balance", key.getBalance().toString(),
                        "currency", "USD"
                )))
                .map(Mono::just)
                .orElse(Mono.just(ResponseEntity.ok(Map.of(
                        "balance", "0.00",
                        "currency", "USD"
                ))));
    }
    
    // ==================== Symbol Table Inspection (Lehrstuhl Metrics) ====================
    
    /**
     * GET /api/v2/symbols/services - Symbol table services.
     */
    @GetMapping("/symbols/services")
    public Mono<ResponseEntity<Map<String, Object>>> getSymbolServices() {
        List<ServiceSymbol> services = symbolTable.getAllServices();
        
        return Mono.just(ResponseEntity.ok(Map.of(
                "services", services.stream().map(this::serviceToMap).toList(),
                "count", services.size(),
                "pretty", prettyPrinter.printServiceCatalog(services)
        )));
    }
    
    /**
     * GET /api/v2/symbols/stats - Symbol table statistics.
     */
    @GetMapping("/symbols/stats")
    public Mono<ResponseEntity<Map<String, Object>>> getSymbolStats() {
        SmmSymbolTable.Statistics stats = symbolTable.getStatistics();
        
        return Mono.just(ResponseEntity.ok(Map.of(
                "serviceCount", stats.serviceCount(),
                "tenantCount", stats.tenantCount(),
                "totalSymbols", stats.totalSymbols(),
                "pretty", prettyPrinter.printStatistics(symbolTable)
        )));
    }
    
    /**
     * GET /api/v2/symbols/scope/{apiKey} - Tenant scope inspection.
     */
    @GetMapping("/symbols/scope/{apiKey}")
    public Mono<ResponseEntity<Map<String, Object>>> getTenantScope(@PathVariable String apiKey) {
        return symbolTable.findTenantScope(apiKey)
                .map(scope -> {
                    List<OrderSymbol> orders = scope.resolveManyLocally(SymbolKind.ORDER);
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "apiKey", apiKey.substring(0, 8) + "...",
                            "symbolCount", scope.size(),
                            "orders", orders.stream()
                                    .map(o -> Map.of(
                                            "id", o.getOrderId().toString(),
                                            "status", o.getStatus(),
                                            "progress", o.getProgress()
                                    ))
                                    .toList()
                    ));
                })
                .map(Mono::just)
                .orElse(Mono.just(ResponseEntity.notFound().build()));
    }
    
    /**
     * GET /api/v2/cocos - CoCo checker information.
     */
    @GetMapping("/cocos")
    public Mono<ResponseEntity<Map<String, Object>>> getCoCos() {
        return Mono.just(ResponseEntity.ok(Map.of(
                "cocoCount", cocoChecker.getCoCoCount(),
                "cocos", cocoChecker.getCocos().stream()
                        .map(c -> Map.of(
                                "code", c.getErrorCode(),
                                "description", c.getDescription()
                        ))
                        .toList()
        )));
    }
    
    // ==================== Helper Methods ====================
    
    private Map<String, Object> serviceToMap(ServiceSymbol service) {
        Map<String, Object> map = new HashMap<>();
        map.put("service", service.getPublicId());
        map.put("name", service.getDisplayName());
        map.put("rate", service.getRate().doubleValue());
        map.put("min", service.getMinOrder());
        map.put("max", service.getMaxOrder());
        map.put("category", service.getCategory());
        return map;
    }
    
    private ResponseEntity<Map<String, Object>> errorResponse(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
    
    private String getString(Map<String, Object> body, String key) {
        Object value = body != null ? body.get(key) : null;
        return value != null ? value.toString() : "";
    }
    
    private int getInt(Map<String, Object> body, String key) {
        Object value = body != null ? body.get(key) : null;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return value != null ? Integer.parseInt(value.toString()) : 0;
    }
}
