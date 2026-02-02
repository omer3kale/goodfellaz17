package com.goodfellaz17.application.service;

import com.goodfellaz17.domain.model.generated.OrderEntity;
import com.goodfellaz17.domain.model.generated.OrderTaskEntity;
import com.goodfellaz17.domain.model.generated.TaskStatus;
import com.goodfellaz17.infrastructure.persistence.generated.OrderTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * OrderInvariantValidator - Enforces hard invariants for 15k orders.
 *
 * These invariants guarantee:
 * 1. Task-based orders: sum(completed qty) + failed_permanent == order.quantity
 * 2. No orphaned EXECUTING tasks beyond threshold
 * 3. Order COMPLETED implies all tasks terminal
 * 4. Non-task orders: no tasks exist, delivered == quantity
 *
 * @author goodfellaz17
 * @since 1.0.0
 */
@Service
public class OrderInvariantValidator {

    private static final Logger log = LoggerFactory.getLogger(OrderInvariantValidator.class);

    private final OrderTaskRepository taskRepository;

    /** ORPHAN_THRESHOLD_SECONDS - matches OrderDeliveryWorker */
    private static final long ORPHAN_THRESHOLD_SECONDS = 120;

    public OrderInvariantValidator(OrderTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    // =========================================================================
    // INVARIANT 1: Task-based orders must account for all plays
    // =========================================================================

    /**
     * Invariant 1a: For usesTaskDelivery=true orders:
     *   sum(quantity of COMPLETED tasks) + failedPermanentPlays == order.quantity
     *
     * This ensures no plays are lost or double-counted.
     */
    public Mono<Boolean> validateQuantityAccounted(OrderEntity order) {
        if (order.getUsesTaskDelivery() == null || !order.getUsesTaskDelivery()) {
            // Non-task order: verify delivered == quantity
            return Mono.just(
                order.getDelivered() != null &&
                order.getDelivered() == order.getQuantity()
            );
        }

        // Task-based order: verify all plays accounted for
        return taskRepository.findByOrderId(order.getId())
            .collectList()
            .map(tasks -> {
                int completedQty = tasks.stream()
                    .filter(t -> TaskStatus.COMPLETED.name().equals(t.getStatus()))
                    .mapToInt(OrderTaskEntity::getQuantity)
                    .sum();

                int failedQty = tasks.stream()
                    .filter(t -> TaskStatus.FAILED_PERMANENT.name().equals(t.getStatus()))
                    .mapToInt(OrderTaskEntity::getQuantity)
                    .sum();

                int totalAccounted = completedQty + failedQty;
                boolean isValid = totalAccounted == order.getQuantity();

                log.debug("INVARIANT_CHECK_1a | orderId={} | completed={} | failed={} | total={} | expected={} | PASS={}",
                    order.getId(), completedQty, failedQty, totalAccounted, order.getQuantity(), isValid);

                return isValid;
            })
            .onErrorResume(e -> {
                log.error("INVARIANT_CHECK_1a_ERROR | orderId={} | error={}", order.getId(), e.getMessage());
                return Mono.just(false);
            });
    }

    // =========================================================================
    // INVARIANT 2: No orphaned EXECUTING tasks
    // =========================================================================

    /**
     * Invariant 2: No EXECUTING task should be older than ORPHAN_THRESHOLD_SECONDS.
     *
     * If found, the worker will recover it on next cycle.
     * This validates the orphan detection is working.
     */
    public Mono<Boolean> validateNoLongOrphanedTasks(OrderEntity order) {
        if (order.getUsesTaskDelivery() == null || !order.getUsesTaskDelivery()) {
            return Mono.just(true); // Non-task order
        }

        Instant orphanThreshold = Instant.now().minusSeconds(ORPHAN_THRESHOLD_SECONDS);

        return taskRepository.findByOrderIdAndStatus(order.getId(), TaskStatus.EXECUTING.name())
            .any(task -> {
                Instant executionStart = task.getExecutionStartedAt();
                boolean isOrphaned = executionStart != null && executionStart.isBefore(orphanThreshold);

                if (isOrphaned) {
                    log.warn("INVARIANT_VIOLATION_2 | orderId={} | taskId={} | executionStartedAt={} | threshold={}",
                        order.getId(), task.getId(), executionStart, orphanThreshold);
                }

                return isOrphaned;
            })
            .map(hasOrphans -> {
                boolean isValid = !hasOrphans;
                log.debug("INVARIANT_CHECK_2 | orderId={} | hasOrphans={} | PASS={}",
                    order.getId(), hasOrphans, isValid);
                return isValid;
            })
            .onErrorResume(e -> {
                log.error("INVARIANT_CHECK_2_ERROR | orderId={} | error={}", order.getId(), e.getMessage());
                return Mono.just(false);
            });
    }

    // =========================================================================
    // INVARIANT 3: COMPLETED orders have all terminal tasks
    // =========================================================================

    /**
     * Invariant 3: If order.status == COMPLETED, all tasks must be terminal
     * (COMPLETED or FAILED_PERMANENT). No PENDING, EXECUTING, or FAILED_RETRYING.
     */
    public Mono<Boolean> validateCompletedOrderTerminalTasks(OrderEntity order) {
        if (!"COMPLETED".equals(order.getStatus())) {
            return Mono.just(true); // Not completed yet
        }

        if (order.getUsesTaskDelivery() == null || !order.getUsesTaskDelivery()) {
            return Mono.just(true); // Non-task order
        }

        return taskRepository.findByOrderId(order.getId())
            .any(task -> {
                String status = task.getStatus();
                boolean isNonTerminal = TaskStatus.PENDING.name().equals(status) ||
                                       TaskStatus.EXECUTING.name().equals(status) ||
                                       TaskStatus.FAILED_RETRYING.name().equals(status);

                if (isNonTerminal) {
                    log.error("INVARIANT_VIOLATION_3 | orderId={} | taskId={} | status={} | expected=TERMINAL",
                        order.getId(), task.getId(), status);
                }

                return isNonTerminal;
            })
            .map(hasNonTerminal -> {
                boolean isValid = !hasNonTerminal;
                log.debug("INVARIANT_CHECK_3 | orderId={} | hasNonTerminal={} | PASS={}",
                    order.getId(), hasNonTerminal, isValid);
                return isValid;
            })
            .onErrorResume(e -> {
                log.error("INVARIANT_CHECK_3_ERROR | orderId={} | error={}", order.getId(), e.getMessage());
                return Mono.just(false);
            });
    }

    // =========================================================================
    // INVARIANT 4: Non-task orders have no tasks
    // =========================================================================

    /**
     * Invariant 4: If usesTaskDelivery=false, no tasks should exist for the order.
     */
    public Mono<Boolean> validateNonTaskOrderHasNoTasks(OrderEntity order) {
        if (order.getUsesTaskDelivery() != null && order.getUsesTaskDelivery()) {
            return Mono.just(true); // Task-based order
        }

        // Non-task order: verify no tasks exist
        return taskRepository.findByOrderId(order.getId())
            .count()
            .map(count -> {
                boolean isValid = count == 0;

                if (!isValid) {
                    log.error("INVARIANT_VIOLATION_4 | orderId={} | foundTasks={} | expected=0",
                        order.getId(), count);
                }

                log.debug("INVARIANT_CHECK_4 | orderId={} | taskCount={} | PASS={}",
                    order.getId(), count, isValid);

                return isValid;
            })
            .onErrorResume(e -> {
                log.error("INVARIANT_CHECK_4_ERROR | orderId={} | error={}", order.getId(), e.getMessage());
                return Mono.just(false);
            });
    }

    // =========================================================================
    // COMPREHENSIVE VALIDATION
    // =========================================================================

    /**
     * Validate ALL invariants for an order.
     * Returns list of failed invariant names (empty = all pass).
     */
    public Mono<List<String>> validateAll(OrderEntity order) {
        List<String> failures = new ArrayList<>();
        return validateQuantityAccounted(order)
            .flatMap(pass -> {
                if (!pass) failures.add("QUANTITY_NOT_ACCOUNTED");
                return validateNoLongOrphanedTasks(order);
            })
            .flatMap(pass -> {
                if (!pass) failures.add("ORPHANED_TASKS_FOUND");
                return validateCompletedOrderTerminalTasks(order);
            })
            .flatMap(pass -> {
                if (!pass) failures.add("COMPLETED_WITH_NONTERMINAL_TASKS");
                return validateNonTaskOrderHasNoTasks(order);
            })
            .map(pass -> {
                if (!pass) failures.add("NONTASK_ORDER_HAS_TASKS");
                return failures;
            });
    }

    /**
     * Fail fast if ANY invariant fails (for critical paths).
     */
    public Mono<Void> assertAllInvariantsOrFail(OrderEntity order) {
        return validateAll(order)
            .flatMap(failures -> {
                if (!failures.isEmpty()) {
                    String msg = String.format(
                        "Order invariants violated for %s: %s",
                        order.getId(), String.join(", ", failures)
                    );
                    log.error("INVARIANT_ASSERTION_FAILED | {}", msg);
                    return Mono.error(new IllegalStateException(msg));
                }
                return Mono.empty();
            });
    }
}
