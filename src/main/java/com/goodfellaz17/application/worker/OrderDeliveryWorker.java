package com.goodfellaz17.application.worker;

import com.goodfellaz17.application.metrics.DeliveryMetrics;
import com.goodfellaz17.application.testing.FailureInjectionService;
import com.goodfellaz17.domain.model.generated.*;
import com.goodfellaz17.infrastructure.proxy.ProxyExecutorClient;
import com.goodfellaz17.infrastructure.persistence.OrderProgressUpdater;
import com.goodfellaz17.infrastructure.persistence.generated.GeneratedOrderRepository;
import com.goodfellaz17.infrastructure.persistence.generated.OrderTaskRepository;
import com.goodfellaz17.infrastructure.proxy.generated.HybridProxyRouterV2;
import com.goodfellaz17.infrastructure.proxy.generated.HybridProxyRouterV2.ProxySelection;
import com.goodfellaz17.infrastructure.proxy.generated.HybridProxyRouterV2.RoutingRequest;
import com.goodfellaz17.infrastructure.proxy.generated.HybridProxyRouterV2.ProxyResult;
import com.goodfellaz17.infrastructure.proxy.generated.HybridProxyRouterV2.OperationType;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OrderDeliveryWorker - The heart of the 15k delivery guarantee.
 * 
 * Runs on a schedule and:
 * 1. Picks up PENDING tasks that are ready (past scheduled time)
 * 2. Picks up FAILED_RETRYING tasks past their retry delay
 * 3. Recovers orphaned EXECUTING tasks (worker crashed mid-execution)
 * 4. For each task:
 *    - Claims it atomically
 *    - Selects a proxy via HybridProxyRouterV2
 *    - Executes the play delivery (simulated for now)
 *    - On success: marks COMPLETED, increments order.delivered
 *    - On failure: marks FAILED_RETRYING with backoff, or FAILED_PERMANENT after max retries
 * 5. When all tasks complete: marks order COMPLETED
 * 
 * Recovery guarantees:
 * - Orphaned tasks are detected after ORPHAN_THRESHOLD_SECONDS
 * - Failed tasks use exponential backoff (30s, 60s, 120s)
 * - After 3 failures: task goes to dead-letter queue
 * - Order still completes even with dead-letter tasks (partial success logged)
 * 
 * @author goodfellaz17
 * @since 1.0.0
 */
@Component
public class OrderDeliveryWorker {
    
    private static final Logger log = LoggerFactory.getLogger(OrderDeliveryWorker.class);
    
    // === Configuration ===
    
    /** How many tasks to pick up per worker cycle */
    private static final int BATCH_SIZE = 10;
    
    /** Seconds after which an EXECUTING task is considered orphaned */
    private static final int ORPHAN_THRESHOLD_SECONDS = 30;
    
    /** Maximum concurrent task executions */
    private static final int MAX_CONCURRENT_TASKS = 5;
    
    /** Simulated execution time per task (ms) */
    private static final int SIMULATED_EXECUTION_MS = 500;
    
    private final OrderTaskRepository taskRepository;
    private final GeneratedOrderRepository orderRepository;
    private final OrderProgressUpdater progressUpdater;
    private final HybridProxyRouterV2 proxyRouter;
    private final TransactionalOperator transactionalOperator;
    
    /** Unique worker ID for this instance (hostname + random suffix) */
    private final String workerId;
    
    /** Flag to prevent concurrent worker cycles */
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    /** Active profile */
    @Value("${spring.profiles.active:prod}")
    private String activeProfile;
    
    /** Feature flag to enable/disable worker */
    @Value("${goodfellaz17.worker.enabled:true}")
    private boolean workerEnabled;
    
    /** 
     * Feature flag to enable refunds on permanent failures.
     * Default: true for real runs. Set to false for freeze tests.
     */
    @Value("${goodfellaz17.refund.enabled:true}")
    private boolean refundEnabled;
    
    // === Metrics ===
    private final AtomicLong totalTasksProcessed = new AtomicLong(0);
    private final AtomicLong totalTasksCompleted = new AtomicLong(0);
    private final AtomicLong totalTasksFailed = new AtomicLong(0);
    private final AtomicLong totalTransientFailures = new AtomicLong(0);
    private final AtomicLong totalPermanentFailures = new AtomicLong(0);
    private final AtomicLong totalRetries = new AtomicLong(0);
    private final AtomicLong recoveredOrphans = new AtomicLong(0);
    private final AtomicInteger activeTaskCount = new AtomicInteger(0);
    
    /** Timestamp when worker started (for tracking restart recovery) */
    private final Instant workerStartTime = Instant.now();
    
    /** Count of tasks recovered after this worker started (orphan recovery) */
    private final AtomicLong tasksRecoveredAfterStart = new AtomicLong(0);
    
    /** Failure injection service (only active in dev/local) */
    @Autowired(required = false)
    private FailureInjectionService failureInjectionService;
    
    /** Proxy executor client for real proxy calls (Phase 3 infra) */
    @Autowired(required = false)
    private ProxyExecutorClient proxyExecutorClient;
    
    public OrderDeliveryWorker(
            OrderTaskRepository taskRepository,
            GeneratedOrderRepository orderRepository,
            OrderProgressUpdater progressUpdater,
            HybridProxyRouterV2 proxyRouter,
            TransactionalOperator transactionalOperator) {
        this.taskRepository = taskRepository;
        this.orderRepository = orderRepository;
        this.progressUpdater = progressUpdater;
        this.proxyRouter = proxyRouter;
        this.transactionalOperator = transactionalOperator;
        this.workerId = generateWorkerId();
        
        log.info("OrderDeliveryWorker initialized | workerId={}", workerId);
    }
    
    // =========================================================================
    // SCHEDULED WORKER
    // =========================================================================
    
    /**
     * Main worker loop - runs every 10 seconds.
     * Uses fixedDelay to prevent overlapping executions.
     */
    @Scheduled(fixedDelayString = "${goodfellaz17.worker.interval-ms:10000}")
    public void processTaskBatch() {
        if (!workerEnabled) {
            return;
        }
        
        // Prevent concurrent executions
        if (!isRunning.compareAndSet(false, true)) {
            log.debug("Worker cycle skipped - previous cycle still running");
            return;
        }
        
        // Track per-cycle stats for summary logging
        final long cycleStartProcessed = totalTasksProcessed.get();
        final long cycleStartCompleted = totalTasksCompleted.get();
        final long cycleStartFailed = totalTasksFailed.get();
        
        try {
            log.debug("WORKER_CYCLE_START | workerId={} | activeTasks={}", workerId, activeTaskCount.get());
            
            Instant now = Instant.now();
            Instant orphanThreshold = now.minusSeconds(ORPHAN_THRESHOLD_SECONDS);
            
            // Find tasks ready for execution
            taskRepository.findTasksReadyForWorker(now, orphanThreshold, BATCH_SIZE)
                .flatMap(this::processTask, MAX_CONCURRENT_TASKS)
                .doOnComplete(() -> {
                    // Log per-cycle summary at INFO level
                    long cycleProcessed = totalTasksProcessed.get() - cycleStartProcessed;
                    long cycleCompleted = totalTasksCompleted.get() - cycleStartCompleted;
                    long cycleFailed = totalTasksFailed.get() - cycleStartFailed;
                    
                    if (cycleProcessed > 0) {
                        log.info("WORKER_CYCLE_SUMMARY | workerId={} | cycleProcessed={} | cycleCompleted={} | cycleFailed={} | activeTasks={}",
                            workerId, cycleProcessed, cycleCompleted, cycleFailed, activeTaskCount.get());
                    }
                    
                    log.debug("WORKER_CYCLE_COMPLETE | totalProcessed={} | totalCompleted={} | totalFailed={}",
                        totalTasksProcessed.get(), totalTasksCompleted.get(), totalTasksFailed.get());
                })
                .subscribe(
                    result -> {}, // Success handled in processTask
                    error -> log.error("WORKER_CYCLE_ERROR | error={}", error.getMessage(), error)
                );
        } finally {
            // Release lock after a small delay to let async operations start
            Mono.delay(Duration.ofMillis(100))
                .subscribe(v -> isRunning.set(false));
        }
    }
    
    // =========================================================================
    // TASK PROCESSING
    // =========================================================================
    
    /**
     * Process a single task: claim, execute, update.
     */
    private Mono<TaskExecutionResult> processTask(OrderTaskEntity task) {
        activeTaskCount.incrementAndGet();
        totalTasksProcessed.incrementAndGet();
        
        // Track orphan recovery
        boolean isOrphanRecovery = TaskStatus.EXECUTING.name().equals(task.getStatus());
        if (isOrphanRecovery) {
            recoveredOrphans.incrementAndGet();
            tasksRecoveredAfterStart.incrementAndGet();
            log.info("ORPHAN_RECOVERY | taskId={} | orderId={} | originalWorker={}", 
                task.getId(), task.getOrderId(), task.getWorkerId());
        }
        
        // Track retries
        if (TaskStatus.FAILED_RETRYING.name().equals(task.getStatus())) {
            totalRetries.incrementAndGet();
            log.info("TASK_RETRY | taskId={} | orderId={} | attempt={}/{}", 
                task.getId(), task.getOrderId(), task.getAttempts() + 1, task.getMaxAttempts());
        }
        
        log.info("TASK_PICKUP | taskId={} | orderId={} | seq={} | qty={} | status={} | attempts={}", 
            task.getId(), task.getOrderId(), task.getSequenceNumber(), 
            task.getQuantity(), task.getStatus(), task.getAttempts());
        
        // === FAILURE INJECTION HOOK: Check pause ===
        Mono<Void> pauseCheck = (failureInjectionService != null) 
            ? failureInjectionService.checkPause() 
            : Mono.empty();
        
        // Select proxy for this task
        return pauseCheck.then(selectProxy(task))
            .flatMap(proxySelection -> {
                // === FAILURE INJECTION HOOK: Check proxy ban ===
                if (failureInjectionService != null && 
                    failureInjectionService.isProxyBanned(proxySelection.proxyId())) {
                    log.warn("INJECTION_PROXY_BANNED | taskId={} | proxyId={}", 
                        task.getId(), proxySelection.proxyId());
                    return Mono.error(new RuntimeException("Injected: Proxy banned - " + proxySelection.proxyId()));
                }
                
                // Claim task atomically
                return claimTask(task, proxySelection)
                    .flatMap(claimedTask -> 
                        // Execute the delivery
                        executeDelivery(claimedTask, proxySelection)
                            // Complete task on success
                            .flatMap(delivery -> completeTask(claimedTask, delivery))
                            // Handle failure
                            .onErrorResume(error -> handleTaskFailure(claimedTask, error))
                    );
            })
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("TASK_NO_PROXY | taskId={} | orderId={}", task.getId(), task.getOrderId());
                return handleTaskFailure(task, new RuntimeException("No proxy available"));
            }))
            .doFinally(signal -> activeTaskCount.decrementAndGet());
    }
    
    /**
     * Select a proxy for task execution.
     */
    private Mono<ProxySelection> selectProxy(OrderTaskEntity task) {
        // Get order for geo targeting
        return orderRepository.findById(task.getOrderId())
            .flatMap(order -> {
                // Pass geoProfile as-is; router handles normalization of WORLDWIDE/WW/null/etc
                String geo = order.getGeoProfile();
                
                RoutingRequest request = RoutingRequest.builder()
                    .operation(OperationType.PLAY_DELIVERY)
                    .targetCountry(geo)  // Router normalizes WORLDWIDE, WW, null, blank, etc
                    .quantity(task.getQuantity())
                    .build();
                
                return proxyRouter.route(request);
            });
    }
    
    /**
     * Claim task atomically (prevents other workers from taking it).
     */
    private Mono<OrderTaskEntity> claimTask(OrderTaskEntity task, ProxySelection proxy) {
        return taskRepository.claimTask(
            task.getId(),
            Instant.now(),
            workerId,
            proxy.proxyId()
        ).doOnSuccess(claimed -> {
            if (claimed != null) {
                log.debug("TASK_CLAIMED | taskId={} | workerId={} | proxyId={}", 
                    task.getId(), workerId, proxy.proxyId());
            }
        });
    }
    
    /**
     * Execute the actual delivery.
     * 
     * When ProxyExecutorClient is available and enabled:
     * - Calls real proxy node via HTTP
     * - Returns actual execution result
     * 
     * Otherwise (simulated mode):
     * - Uses Mono.delay() to simulate execution
     * - Used for thesis tests and when proxy infra unavailable
     */
    private Mono<DeliveryResult> executeDelivery(OrderTaskEntity task, ProxySelection proxy) {
        log.info("TASK_EXECUTING | taskId={} | orderId={} | seq={} | qty={} | proxy={}", 
            task.getId(), task.getOrderId(), task.getSequenceNumber(),
            task.getQuantity(), proxy.proxyUrl());
        
        // === FAILURE INJECTION HOOKS ===
        if (failureInjectionService != null && failureInjectionService.isEnabled()) {
            FailureInjectionService.InjectionResult injection = 
                failureInjectionService.checkInjections(proxy.proxyId(), task.getId());
            
            if (!injection.shouldProceed()) {
                log.warn("INJECTION_TRIGGERED | taskId={} | type={} | reason={}", 
                    task.getId(), injection.failureType(), injection.failureReason());
                return Mono.error(new RuntimeException("Injected: " + injection.failureReason()));
            }
        }
        
        // Inject latency if configured
        Mono<Void> latencyInjection = (failureInjectionService != null) 
            ? failureInjectionService.injectLatency() 
            : Mono.empty();
        
        // === REAL PROXY EXECUTION (Phase 3) ===
        if (proxyExecutorClient != null && proxyExecutorClient.isEnabled()) {
            log.debug("USING_REAL_PROXY | taskId={} | proxyId={}", task.getId(), proxy.proxyId());
            
            return latencyInjection
                .then(proxyExecutorClient.executeTask(
                    task.getId().toString(),
                    task.getOrderId().toString(),
                    task.getQuantity(),
                    null, // trackUrl not stored on task - would need to fetch from order
                    proxy.proxyId()
                ))
                .map(result -> {
                    if (!result.success()) {
                        throw new RuntimeException("Proxy execution failed: " + result.message());
                    }
                    return new DeliveryResult(
                        task.getId(),
                        result.plays(),
                        true,
                        proxy.proxyId(),
                        Duration.ofMillis(SIMULATED_EXECUTION_MS) // TODO: Get real duration
                    );
                });
        }
        
        // === SIMULATED EXECUTION (Default/Thesis mode) ===
        return latencyInjection
            .then(Mono.delay(Duration.ofMillis(SIMULATED_EXECUTION_MS)))
            .map(v -> {
                // Legacy dev mode failure simulation (in case injection service not available)
                if (isDevMode() && failureInjectionService == null && shouldSimulateFailure()) {
                    throw new RuntimeException("Simulated proxy failure for testing");
                }
                
                return new DeliveryResult(
                    task.getId(),
                    task.getQuantity(),
                    true,
                    proxy.proxyId(),
                    Duration.ofMillis(SIMULATED_EXECUTION_MS)
                );
            });
    }
    
    /**
     * Complete task and update order progress.
     */
    private Mono<TaskExecutionResult> completeTask(OrderTaskEntity task, DeliveryResult delivery) {
        log.info("TASK_COMPLETING | taskId={} | orderId={} | delivered={}", 
            task.getId(), task.getOrderId(), delivery.deliveredPlays());
        
        return transactionalOperator.transactional(
            // 1. Mark task completed
            taskRepository.completeTask(task.getId(), Instant.now())
                // 2. Update order delivered count (atomic SQL - prevents lost updates)
                .flatMap(completedTask -> 
                    incrementOrderDelivered(task.getOrderId(), delivery.deliveredPlays())
                        .map(updatedOrder -> new TaskAndOrder(completedTask, updatedOrder)))
                // 3. Check if order is fully delivered (using just-updated order)
                .flatMap(taskAndOrder -> 
                    checkOrderCompletion(taskAndOrder.order)
                        .thenReturn(taskAndOrder.task))
        )
        .map(completedTask -> {
            totalTasksCompleted.incrementAndGet();
            
            log.info("TASK_COMPLETED | taskId={} | orderId={} | seq={} | delivered={}", 
                task.getId(), task.getOrderId(), task.getSequenceNumber(), delivery.deliveredPlays());
            
            // Report success to proxy router for metrics
            proxyRouter.reportResult(delivery.proxyId(), ProxyResult.success(500, 0)).subscribe();
            
            return new TaskExecutionResult(task.getId(), true, delivery.deliveredPlays(), null);
        });
    }
    
    /** Helper record to pass both task and order through the reactive chain */
    private record TaskAndOrder(OrderTaskEntity task, OrderEntity order) {}
    
    /**
     * Handle task execution failure.
     */
    private Mono<TaskExecutionResult> handleTaskFailure(OrderTaskEntity task, Throwable error) {
        String rawErrorMessage = error.getMessage() != null ? error.getMessage() : "Unknown error";
        final String errorMessage = rawErrorMessage.substring(0, Math.min(rawErrorMessage.length(), 500));
        
        // Calculate retry delay (exponential backoff)
        int attempts = task.getAttempts() + 1;
        long backoffSeconds = 30L * (1L << Math.min(attempts - 1, 4)); // Cap at 8x
        Instant retryAfter = Instant.now().plusSeconds(backoffSeconds);
        
        log.warn("TASK_FAILED | taskId={} | orderId={} | attempt={}/{} | error={} | retryAfter={}", 
            task.getId(), task.getOrderId(), attempts, task.getMaxAttempts(), 
            errorMessage, attempts >= task.getMaxAttempts() ? "PERMANENT" : retryAfter);
        
        return taskRepository.failTask(task.getId(), errorMessage, retryAfter)
            .flatMap(failedTask -> {
                totalTasksFailed.incrementAndGet();
                
                if (TaskStatus.FAILED_PERMANENT.name().equals(failedTask.getStatus())) {
                    totalPermanentFailures.incrementAndGet();
                    
                    log.error("TASK_PERMANENT_FAILURE | taskId={} | orderId={} | qty={} | error={}", 
                        task.getId(), task.getOrderId(), task.getQuantity(), errorMessage);
                    
                    // Update order's dead-letter count, process refund, and check completion
                    return incrementOrderFailedPermanentAndRefund(
                            task.getId(), task.getOrderId(), task.getQuantity())
                        .then(orderRepository.findById(task.getOrderId()))
                        .flatMap(order -> checkOrderCompletion(order))
                        .thenReturn(failedTask);
                } else {
                    totalTransientFailures.incrementAndGet();
                    return Mono.just(failedTask);
                }
            })
            .map(failedTask -> new TaskExecutionResult(
                task.getId(), 
                false, 
                0, 
                errorMessage
            ))
            .doOnSuccess(result -> {
                // Report failure to proxy router for metrics
                if (task.getProxyNodeId() != null) {
                    proxyRouter.reportResult(task.getProxyNodeId(), ProxyResult.failure(500, 500)).subscribe();
                }
            });
    }
    
    // =========================================================================
    // ORDER PROGRESS
    // =========================================================================
    
    /**
     * Increment order's delivered count atomically using SQL UPDATE.
     * This prevents lost updates when multiple tasks complete concurrently.
     * Returns the updated order for completion checking.
     */
    private Mono<OrderEntity> incrementOrderDelivered(UUID orderId, int deliveredPlays) {
        return progressUpdater.atomicIncrementDelivered(orderId, deliveredPlays);
    }
    
    /**
     * Increment order's failed_permanent_plays count atomically AND process refund.
     * 
     * When refundEnabled=true:
     * 1. Increment failed_permanent_plays on order
     * 2. Load order to get userId and pricePerPlay
     * 3. Atomically mark task as refunded + credit user balance + track on order
     * 
     * The refund operation is idempotent - if the task is already refunded,
     * no additional credit occurs.
     */
    private Mono<Void> incrementOrderFailedPermanentAndRefund(UUID taskId, UUID orderId, int failedPlays) {
        return progressUpdater.atomicIncrementFailedPermanent(orderId, failedPlays)
            .then(Mono.defer(() -> {
                if (!refundEnabled) {
                    log.debug("REFUND_SKIP_DISABLED | taskId={} | orderId={}", taskId, orderId);
                    return Mono.empty();
                }
                
                // Load order to get userId and calculate price per play
                return orderRepository.findById(orderId)
                    .flatMap(order -> {
                        if (order.getPricePerUnit() == null || order.getUserId() == null) {
                            log.warn("REFUND_SKIP_MISSING_DATA | taskId={} | orderId={} | pricePerUnit={} | userId={}", 
                                taskId, orderId, order.getPricePerUnit(), order.getUserId());
                            return Mono.empty();
                        }
                        
                        return progressUpdater.atomicProcessRefund(
                            taskId,
                            orderId,
                            order.getUserId(),
                            failedPlays,
                            order.getPricePerUnit()
                        ).doOnNext(result -> {
                            if (result.refundApplied()) {
                                log.info("REFUND_APPLIED | taskId={} | orderId={} | userId={} | plays={} | amount={}", 
                                    taskId, orderId, order.getUserId(), failedPlays, result.refundAmount());
                            }
                        }).then();
                    });
            }));
    }
    
    /**
     * Increment order's failed_permanent_plays count atomically.
     * @deprecated Use incrementOrderFailedPermanentAndRefund instead
     */
    @Deprecated
    private Mono<Void> incrementOrderFailedPermanent(UUID orderId, int failedPlays) {
        return progressUpdater.atomicIncrementFailedPermanent(orderId, failedPlays)
            .then();
    }
    
    /**
     * Check if order is fully delivered and mark complete.
     * Takes the already-updated order from atomicIncrementDelivered to avoid
     * race conditions with concurrent task completions.
     * 
     * Status remains COMPLETED for both full and partial success.
     * The internalNotes field captures details:
     * - Full success: "Delivered: 2,000 | Failed: 0"
     * - Partial success: "Delivered: 1,600 | Failed: 400 (PARTIAL) | Refunded: $0.80"
     */
    private Mono<Void> checkOrderCompletion(OrderEntity order) {
        // Order is complete when no remains left (all plays delivered or failed)
        log.debug("CHECK_ORDER_COMPLETION | orderId={} | remains={} | delivered={} | failed={}", 
            order.getId(), order.getRemains(), order.getDelivered(), order.getFailedPermanentPlays());
        
        if (order.getRemains() != null && order.getRemains() <= 0) {
            int totalDelivered = order.getDelivered();
            int totalFailed = order.getFailedPermanentPlays() != null 
                ? order.getFailedPermanentPlays() : 0;
            
            order.setStatus(OrderStatus.COMPLETED.name());
            order.setCompletedAt(Instant.now());
            order.markNotNew();
            
            // Build status note with partial indicator and refund info
            StringBuilder statusNote = new StringBuilder();
            statusNote.append(String.format("Delivered: %,d | Failed: %,d", totalDelivered, totalFailed));
            
            if (totalFailed > 0) {
                statusNote.append(" (PARTIAL)");
                
                // Include refund amount if available
                if (order.getRefundAmount() != null && 
                    order.getRefundAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
                    statusNote.append(String.format(" | Refunded: $%.2f", order.getRefundAmount()));
                }
            }
            
            order.setInternalNotes(statusNote.toString());
            
            if (totalFailed > 0) {
                log.warn("ORDER_COMPLETED_PARTIAL | orderId={} | delivered={} | failed={} | refund={}", 
                    order.getId(), totalDelivered, totalFailed, order.getRefundAmount());
            } else {
                log.info("ORDER_COMPLETED | orderId={} | delivered={}", order.getId(), totalDelivered);
            }
            
            return orderRepository.save(order).then();
        }
        return Mono.empty();
    }
    
    // =========================================================================
    // HELPERS
    // =========================================================================
    
    private String generateWorkerId() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            return String.format("%s-%s", hostname, UUID.randomUUID().toString().substring(0, 8));
        } catch (Exception e) {
            return "worker-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
    
    private boolean isDevMode() {
        return "local".equalsIgnoreCase(activeProfile) || 
               "dev".equalsIgnoreCase(activeProfile);
    }
    
    /**
     * Simulate failures in dev mode for testing recovery.
     * ~10% failure rate.
     */
    private boolean shouldSimulateFailure() {
        return Math.random() < 0.10;
    }
    
    // =========================================================================
    // METRICS API
    // =========================================================================
    
    public WorkerMetrics getMetrics() {
        return new WorkerMetrics(
            workerId,
            totalTasksProcessed.get(),
            totalTasksCompleted.get(),
            totalTasksFailed.get(),
            totalTransientFailures.get(),
            totalPermanentFailures.get(),
            totalRetries.get(),
            recoveredOrphans.get(),
            tasksRecoveredAfterStart.get(),
            activeTaskCount.get(),
            isRunning.get(),
            workerStartTime
        );
    }
    
    /**
     * Get failure injection status (for admin endpoint).
     */
    public FailureInjectionService.InjectionStatus getInjectionStatus() {
        if (failureInjectionService != null) {
            return failureInjectionService.getStatus();
        }
        return null;
    }
    
    /**
     * Get failure injection service (for admin controller).
     */
    public FailureInjectionService getFailureInjectionService() {
        return failureInjectionService;
    }
    
    // =========================================================================
    // DATA RECORDS
    // =========================================================================
    
    private record DeliveryResult(
        UUID taskId,
        int deliveredPlays,
        boolean success,
        UUID proxyId,
        Duration executionTime
    ) {}
    
    private record TaskExecutionResult(
        UUID taskId,
        boolean success,
        int deliveredPlays,
        String errorMessage
    ) {}
    
    public record WorkerMetrics(
        String workerId,
        long totalProcessed,
        long totalCompleted,
        long totalFailed,
        long transientFailures,
        long permanentFailures,
        long totalRetries,
        long recoveredOrphans,
        long tasksRecoveredAfterStart,
        int activeCount,
        boolean isRunning,
        Instant workerStartTime
    ) {}
}
