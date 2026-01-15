package com.goodfellaz17.application.invariants;

import com.goodfellaz17.domain.model.generated.OrderEntity;
import com.goodfellaz17.domain.model.generated.OrderTaskEntity;
import com.goodfellaz17.domain.model.generated.TaskStatus;
import com.goodfellaz17.infrastructure.persistence.generated.GeneratedOrderRepository;
import com.goodfellaz17.infrastructure.persistence.generated.OrderTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * OrderInvariantValidator - Enforces hard invariants for 15k orders.
 * 
 * INVARIANTS FOR usesTaskDelivery=true:
 * 
 * INV-1: QUANTITY_ACCOUNTING
 *   sum(quantity of COMPLETED tasks) + failedPermanentPlays == order.quantity
 *   Ensures every play is accounted for - either delivered or dead-lettered.
 * 
 * INV-2: NO_STALE_EXECUTING
 *   No EXECUTING task is older than ORPHAN_THRESHOLD_SECONDS without being reclaimed.
 *   Prevents tasks from being stuck in limbo forever.
 * 
 * INV-3: COMPLETION_IMPLIES_TERMINAL
 *   Order status COMPLETED implies all tasks are COMPLETED or FAILED_PERMANENT.
 *   No active tasks can exist for a completed order.
 * 
 * INV-4: TASK_IDEMPOTENCY
 *   Each task's idempotencyToken is unique within the order.
 *   Prevents double-delivery of the same task.
 * 
 * INVARIANTS FOR usesTaskDelivery=false:
 * 
 * INV-5: NO_TASKS_FOR_INSTANT
 *   No OrderTaskEntity records exist for orders with usesTaskDelivery=false.
 * 
 * INV-6: INSTANT_DELIVERED_MATCHES
 *   For instant orders: delivered == quantity when status is COMPLETED.
 * 
 * @author goodfellaz17
 * @since 1.0.0
 */
@Service
public class OrderInvariantValidator {
    
    private static final Logger log = LoggerFactory.getLogger(OrderInvariantValidator.class);
    
    /** Seconds after which an EXECUTING task is considered orphaned */
    private static final int ORPHAN_THRESHOLD_SECONDS = 120;
    
    private final GeneratedOrderRepository orderRepository;
    private final OrderTaskRepository taskRepository;
    
    public OrderInvariantValidator(
            GeneratedOrderRepository orderRepository,
            OrderTaskRepository taskRepository) {
        this.orderRepository = orderRepository;
        this.taskRepository = taskRepository;
    }
    
    // =========================================================================
    // PUBLIC API
    // =========================================================================
    
    /**
     * Validate all invariants for an order.
     * Returns a ValidationResult with pass/fail and details.
     */
    public Mono<ValidationResult> validateOrder(UUID orderId) {
        return orderRepository.findById(orderId)
            .flatMap(this::validateOrderInvariants)
            .defaultIfEmpty(ValidationResult.failure("ORDER_NOT_FOUND", 
                "Order " + orderId + " does not exist"));
    }
    
    /**
     * Validate all invariants for all orders (use carefully - expensive).
     */
    public Mono<GlobalValidationResult> validateAllOrders() {
        return orderRepository.findAll()
            .flatMap(order -> validateOrderInvariants(order)
                .map(result -> new OrderValidationEntry(order.getId(), result)))
            .collectList()
            .map(entries -> {
                long passed = entries.stream().filter(e -> e.result().passed()).count();
                long failed = entries.stream().filter(e -> !e.result().passed()).count();
                List<OrderValidationEntry> failures = entries.stream()
                    .filter(e -> !e.result().passed())
                    .toList();
                return new GlobalValidationResult(passed, failed, failures);
            });
    }
    
    /**
     * Quick check: are there any orphaned EXECUTING tasks globally?
     */
    public Mono<OrphanCheckResult> checkForOrphanedTasks() {
        Instant orphanThreshold = Instant.now().minusSeconds(ORPHAN_THRESHOLD_SECONDS);
        
        return taskRepository.findOrphanedTasks(orphanThreshold, 100)
            .collectList()
            .map(orphans -> new OrphanCheckResult(
                orphans.isEmpty(),
                orphans.size(),
                orphans.stream().map(OrderTaskEntity::getId).toList()
            ));
    }
    
    // =========================================================================
    // INVARIANT CHECKS
    // =========================================================================
    
    private Mono<ValidationResult> validateOrderInvariants(OrderEntity order) {
        Boolean usesTaskDelivery = order.getUsesTaskDelivery();
        
        if (usesTaskDelivery != null && usesTaskDelivery) {
            return validateTaskBasedOrder(order);
        } else {
            return validateInstantOrder(order);
        }
    }
    
    /**
     * Validate invariants for task-based orders (15k+).
     */
    private Mono<ValidationResult> validateTaskBasedOrder(OrderEntity order) {
        UUID orderId = order.getId();
        
        return taskRepository.findByOrderId(orderId)
            .collectList()
            .flatMap(tasks -> {
                List<InvariantViolation> violations = new ArrayList<>();
                
                // INV-1: QUANTITY_ACCOUNTING
                int completedQuantity = tasks.stream()
                    .filter(t -> TaskStatus.COMPLETED.name().equals(t.getStatus()))
                    .mapToInt(OrderTaskEntity::getQuantity)
                    .sum();
                
                int failedPermanentQuantity = tasks.stream()
                    .filter(t -> TaskStatus.FAILED_PERMANENT.name().equals(t.getStatus()))
                    .mapToInt(OrderTaskEntity::getQuantity)
                    .sum();
                
                int orderFailedPermanent = order.getFailedPermanentPlays() != null 
                    ? order.getFailedPermanentPlays() : 0;
                
                // Only check accounting for COMPLETED orders
                if ("COMPLETED".equals(order.getStatus())) {
                    int totalAccounted = completedQuantity + failedPermanentQuantity;
                    if (totalAccounted != order.getQuantity()) {
                        violations.add(new InvariantViolation(
                            "INV-1", "QUANTITY_ACCOUNTING",
                            String.format("Expected %d, got completed=%d + failed=%d = %d",
                                order.getQuantity(), completedQuantity, failedPermanentQuantity, totalAccounted)
                        ));
                    }
                    
                    // Also verify order.delivered matches completed tasks
                    if (order.getDelivered() != completedQuantity) {
                        violations.add(new InvariantViolation(
                            "INV-1b", "DELIVERED_MATCHES_COMPLETED",
                            String.format("order.delivered=%d but sum(COMPLETED tasks)=%d",
                                order.getDelivered(), completedQuantity)
                        ));
                    }
                }
                
                // INV-2: NO_STALE_EXECUTING
                Instant orphanThreshold = Instant.now().minusSeconds(ORPHAN_THRESHOLD_SECONDS);
                List<OrderTaskEntity> staleExecuting = tasks.stream()
                    .filter(t -> TaskStatus.EXECUTING.name().equals(t.getStatus()))
                    .filter(t -> t.getExecutionStartedAt() != null && 
                                 t.getExecutionStartedAt().isBefore(orphanThreshold))
                    .toList();
                
                if (!staleExecuting.isEmpty()) {
                    violations.add(new InvariantViolation(
                        "INV-2", "NO_STALE_EXECUTING",
                        String.format("%d tasks stuck in EXECUTING beyond %ds: %s",
                            staleExecuting.size(), ORPHAN_THRESHOLD_SECONDS,
                            staleExecuting.stream().map(t -> t.getId().toString()).toList())
                    ));
                }
                
                // INV-3: COMPLETION_IMPLIES_TERMINAL
                if ("COMPLETED".equals(order.getStatus())) {
                    List<OrderTaskEntity> activeTasks = tasks.stream()
                        .filter(t -> !isTerminalStatus(t.getStatus()))
                        .toList();
                    
                    if (!activeTasks.isEmpty()) {
                        violations.add(new InvariantViolation(
                            "INV-3", "COMPLETION_IMPLIES_TERMINAL",
                            String.format("Order COMPLETED but %d tasks still active: %s",
                                activeTasks.size(),
                                activeTasks.stream()
                                    .map(t -> t.getId() + "(" + t.getStatus() + ")")
                                    .toList())
                        ));
                    }
                }
                
                // INV-4: TASK_IDEMPOTENCY (check for duplicate tokens)
                long uniqueTokens = tasks.stream()
                    .map(OrderTaskEntity::getIdempotencyToken)
                    .filter(t -> t != null)
                    .distinct()
                    .count();
                
                long totalWithTokens = tasks.stream()
                    .filter(t -> t.getIdempotencyToken() != null)
                    .count();
                
                if (uniqueTokens != totalWithTokens) {
                    violations.add(new InvariantViolation(
                        "INV-4", "TASK_IDEMPOTENCY",
                        String.format("Found duplicate idempotency tokens: %d unique out of %d",
                            uniqueTokens, totalWithTokens)
                    ));
                }
                
                if (violations.isEmpty()) {
                    return Mono.just(ValidationResult.success(orderId, "TASK_BASED"));
                } else {
                    return Mono.just(ValidationResult.violations(orderId, "TASK_BASED", violations));
                }
            });
    }
    
    /**
     * Validate invariants for instant orders (1k).
     */
    private Mono<ValidationResult> validateInstantOrder(OrderEntity order) {
        UUID orderId = order.getId();
        
        return taskRepository.findByOrderId(orderId)
            .collectList()
            .map(tasks -> {
                List<InvariantViolation> violations = new ArrayList<>();
                
                // INV-5: NO_TASKS_FOR_INSTANT
                if (!tasks.isEmpty()) {
                    violations.add(new InvariantViolation(
                        "INV-5", "NO_TASKS_FOR_INSTANT",
                        String.format("Instant order has %d tasks (should be 0)", tasks.size())
                    ));
                }
                
                // INV-6: INSTANT_DELIVERED_MATCHES
                if ("COMPLETED".equals(order.getStatus())) {
                    if (order.getDelivered() != order.getQuantity()) {
                        violations.add(new InvariantViolation(
                            "INV-6", "INSTANT_DELIVERED_MATCHES",
                            String.format("delivered=%d != quantity=%d for COMPLETED instant order",
                                order.getDelivered(), order.getQuantity())
                        ));
                    }
                }
                
                if (violations.isEmpty()) {
                    return ValidationResult.success(orderId, "INSTANT");
                } else {
                    return ValidationResult.violations(orderId, "INSTANT", violations);
                }
            });
    }
    
    private boolean isTerminalStatus(String status) {
        return TaskStatus.COMPLETED.name().equals(status) || 
               TaskStatus.FAILED_PERMANENT.name().equals(status);
    }
    
    // =========================================================================
    // RESULT TYPES
    // =========================================================================
    
    public record ValidationResult(
        boolean passed,
        UUID orderId,
        String orderType,
        List<InvariantViolation> violations,
        String errorMessage
    ) {
        public static ValidationResult success(UUID orderId, String orderType) {
            return new ValidationResult(true, orderId, orderType, List.of(), null);
        }
        
        public static ValidationResult violations(UUID orderId, String orderType, 
                                                  List<InvariantViolation> violations) {
            return new ValidationResult(false, orderId, orderType, violations, null);
        }
        
        public static ValidationResult failure(String code, String message) {
            return new ValidationResult(false, null, null, List.of(), message);
        }
    }
    
    public record InvariantViolation(
        String code,
        String name,
        String details
    ) {}
    
    public record GlobalValidationResult(
        long passedOrders,
        long failedOrders,
        List<OrderValidationEntry> failures
    ) {
        public boolean allPassed() {
            return failedOrders == 0;
        }
    }
    
    public record OrderValidationEntry(
        UUID orderId,
        ValidationResult result
    ) {}
    
    public record OrphanCheckResult(
        boolean noOrphans,
        int orphanCount,
        List<UUID> orphanTaskIds
    ) {}
}
