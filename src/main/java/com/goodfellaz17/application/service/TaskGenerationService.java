package com.goodfellaz17.application.service;

import com.goodfellaz17.domain.model.generated.OrderEntity;
import com.goodfellaz17.domain.model.generated.OrderTaskEntity;
import com.goodfellaz17.domain.model.generated.TaskStatus;
import com.goodfellaz17.infrastructure.persistence.generated.OrderTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * TaskGenerationService - Creates delivery tasks for 15k orders.
 * 
 * When a 15k order is accepted:
 * 1. Calculates how many tasks needed (quantity / task_size)
 * 2. Spreads tasks across the delivery window (48-72h)
 * 3. Creates all task records in PENDING state
 * 4. Each task is assigned a scheduled execution time
 * 
 * Task sizing strategy:
 * - Standard task: 300-500 plays
 * - Final task: remainder (may be smaller)
 * - Tasks are evenly distributed across time window
 * 
 * @author goodfellaz17
 * @since 1.0.0
 */
@Service
public class TaskGenerationService {
    
    private static final Logger log = LoggerFactory.getLogger(TaskGenerationService.class);
    
    // === Configuration ===
    
    /** Target plays per task (optimal batch size) */
    private static final int TARGET_TASK_SIZE = 400;
    
    /** Minimum plays per task */
    private static final int MIN_TASK_SIZE = 200;
    
    /** Maximum plays per task */
    private static final int MAX_TASK_SIZE = 500;
    
    /** Default threshold for using task-based delivery (anything above this) */
    private static final int DEFAULT_TASK_DELIVERY_THRESHOLD = 1000;
    
    /** Target delivery window in hours */
    private static final int TARGET_DELIVERY_HOURS = 48;
    
    /** Maximum delivery window in hours */
    private static final int MAX_DELIVERY_HOURS = 72;
    
    /** Minimum gap between tasks in seconds */
    private static final int MIN_TASK_GAP_SECONDS = 60;
    
    private final OrderTaskRepository taskRepository;
    private final CapacityService capacityService;
    
    /** Speed multiplier for local/dev testing (1.0 = normal, 60.0 = 1 hour -> 1 minute) */
    @Value("${goodfellaz17.task.time-multiplier:1.0}")
    private double timeMultiplier;
    
    /** Active profile for adjusting behavior */
    @Value("${spring.profiles.active:prod}")
    private String activeProfile;
    
    /** 
     * Threshold for task-based delivery. Orders above this use task-based delivery.
     * Can be set to 0 in freeze-test mode to force ALL orders through task delivery.
     */
    @Value("${goodfellaz17.delivery.task-delivery-threshold:1000}")
    private int taskDeliveryThreshold;
    
    public TaskGenerationService(
            OrderTaskRepository taskRepository,
            CapacityService capacityService) {
        this.taskRepository = taskRepository;
        this.capacityService = capacityService;
    }
    
    // =========================================================================
    // PUBLIC API
    // =========================================================================
    
    /**
     * Check if an order should use task-based delivery.
     * 
     * FREEZE MODE: When taskDeliveryThreshold is set to 0, ALL orders use
     * task-based delivery, ensuring the full control flow is exercised.
     */
    public boolean shouldUseTaskDelivery(int quantity) {
        return quantity > taskDeliveryThreshold;
    }
    
    /**
     * Generate tasks for a 15k order.
     * 
     * @param order The accepted order
     * @return Flux of created tasks
     */
    public Flux<OrderTaskEntity> generateTasksForOrder(OrderEntity order) {
        int quantity = order.getQuantity();
        Instant estimatedCompletion = order.getEstimatedCompletionAt();
        
        log.info("TASK_GEN_START | orderId={} | quantity={} | eta={}", 
            order.getId(), quantity, estimatedCompletion);
        
        // Calculate task schedule
        TaskSchedule schedule = calculateSchedule(quantity, estimatedCompletion);
        
        log.info("TASK_GEN_PLAN | orderId={} | taskCount={} | taskSize={} | windowHours={}", 
            order.getId(), schedule.taskCount(), schedule.taskSize(), schedule.windowHours());
        
        // Generate task entities
        List<OrderTaskEntity> tasks = createTasks(order.getId(), schedule);
        
        // Save all tasks
        return taskRepository.saveAll(tasks)
            .doOnComplete(() -> log.info(
                "TASK_GEN_COMPLETE | orderId={} | tasksCreated={} | firstScheduled={} | lastScheduled={}",
                order.getId(), 
                tasks.size(),
                tasks.isEmpty() ? "N/A" : tasks.get(0).getScheduledAt(),
                tasks.isEmpty() ? "N/A" : tasks.get(tasks.size() - 1).getScheduledAt()));
    }
    
    /**
     * Get task generation preview without creating.
     * Useful for capacity planning UI.
     */
    public Mono<TaskSchedule> previewTaskSchedule(int quantity, Instant estimatedCompletion) {
        return Mono.just(calculateSchedule(quantity, estimatedCompletion));
    }
    
    /**
     * Calculate task distribution for capacity planning.
     */
    public Mono<TaskDistribution> calculateTaskDistribution(int quantity) {
        return capacityService.canAccept(quantity)
            .map(result -> {
                if (!result.accepted()) {
                    return new TaskDistribution(
                        quantity, 0, 0, 0, 0.0, false, result.rejectionReason());
                }
                
                TaskSchedule schedule = calculateSchedule(quantity, result.estimatedCompletion());
                
                return new TaskDistribution(
                    quantity,
                    schedule.taskCount(),
                    schedule.taskSize(),
                    schedule.windowHours(),
                    result.estimatedHours(),
                    true,
                    null);
            });
    }
    
    // =========================================================================
    // INTERNAL - Schedule Calculation
    // =========================================================================
    
    private TaskSchedule calculateSchedule(int quantity, Instant estimatedCompletion) {
        // Determine delivery window (capped at max)
        Instant now = Instant.now();
        long windowSeconds = Duration.between(now, estimatedCompletion).toSeconds();
        
        // Apply time multiplier for testing (e.g., 72 hours -> 72 minutes in dev)
        if (isDevMode()) {
            windowSeconds = (long)(windowSeconds / getEffectiveTimeMultiplier());
        }
        
        int windowHours = (int)(windowSeconds / 3600);
        windowHours = Math.max(1, Math.min(windowHours, MAX_DELIVERY_HOURS));
        
        // Calculate optimal task size
        int taskSize = TARGET_TASK_SIZE;
        int taskCount = (int) Math.ceil((double) quantity / taskSize);
        
        // Adjust if too many tasks (spread thinner)
        int maxTasksPerHour = 10; // Don't overwhelm the system
        int maxTasks = windowHours * maxTasksPerHour;
        
        if (taskCount > maxTasks) {
            taskSize = (int) Math.ceil((double) quantity / maxTasks);
            taskSize = Math.min(taskSize, MAX_TASK_SIZE);
            taskCount = (int) Math.ceil((double) quantity / taskSize);
        }
        
        // Ensure minimum task size
        if (taskSize < MIN_TASK_SIZE && taskCount > 1) {
            taskSize = MIN_TASK_SIZE;
            taskCount = (int) Math.ceil((double) quantity / taskSize);
        }
        
        // Calculate time gap between tasks
        long gapSeconds = windowSeconds / Math.max(1, taskCount);
        gapSeconds = Math.max(gapSeconds, MIN_TASK_GAP_SECONDS);
        
        // In dev mode, compress the schedule
        if (isDevMode()) {
            gapSeconds = Math.max(2, gapSeconds / (long)getEffectiveTimeMultiplier());
        }
        
        return new TaskSchedule(
            quantity,
            taskCount,
            taskSize,
            windowHours,
            gapSeconds,
            now
        );
    }
    
    private List<OrderTaskEntity> createTasks(UUID orderId, TaskSchedule schedule) {
        List<OrderTaskEntity> tasks = new ArrayList<>();
        
        int remainingQuantity = schedule.totalQuantity();
        Instant scheduledTime = schedule.startTime();
        
        for (int seq = 1; seq <= schedule.taskCount() && remainingQuantity > 0; seq++) {
            // Calculate this task's quantity (last task may be smaller)
            int taskQuantity = Math.min(schedule.taskSize(), remainingQuantity);
            remainingQuantity -= taskQuantity;
            
            // Build idempotency token
            String idempotencyToken = String.format("%s:%d:0", orderId.toString(), seq);
            
            OrderTaskEntity task = OrderTaskEntity.builder()
                .orderId(orderId)
                .sequenceNumber(seq)
                .quantity(taskQuantity)
                .status(TaskStatus.PENDING.name())
                .attempts(0)
                .maxAttempts(3)
                .scheduledAt(scheduledTime)
                .idempotencyToken(idempotencyToken)
                .build();
            
            tasks.add(task);
            
            // Move to next scheduled time
            scheduledTime = scheduledTime.plusSeconds(schedule.gapSeconds());
        }
        
        return tasks;
    }
    
    // =========================================================================
    // HELPERS
    // =========================================================================
    
    private boolean isDevMode() {
        return "local".equalsIgnoreCase(activeProfile) || 
               "dev".equalsIgnoreCase(activeProfile) ||
               "test".equalsIgnoreCase(activeProfile);
    }
    
    private double getEffectiveTimeMultiplier() {
        // In dev mode, accelerate time by default
        if (isDevMode() && timeMultiplier == 1.0) {
            return 720.0; // 72 hours -> 6 minutes
        }
        return Math.max(1.0, timeMultiplier);
    }
    
    // =========================================================================
    // DATA RECORDS
    // =========================================================================
    
    /**
     * Task schedule calculation result.
     */
    public record TaskSchedule(
        int totalQuantity,
        int taskCount,
        int taskSize,
        int windowHours,
        long gapSeconds,
        Instant startTime
    ) {
        public Duration taskGap() {
            return Duration.ofSeconds(gapSeconds);
        }
        
        public Instant estimatedCompletion() {
            return startTime.plusSeconds(gapSeconds * taskCount);
        }
    }
    
    /**
     * Task distribution info for planning.
     */
    public record TaskDistribution(
        int totalQuantity,
        int taskCount,
        int taskSize,
        int windowHours,
        double estimatedHours,
        boolean canAccept,
        String rejectionReason
    ) {}
}
