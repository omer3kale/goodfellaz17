package com.goodfellaz17.order.service;

import com.goodfellaz17.order.domain.OrderTask;
import com.goodfellaz17.order.domain.port.SpotifyPlayCommand;
import com.goodfellaz17.order.domain.port.SpotifyPlayPort;
import com.goodfellaz17.order.domain.port.PlayResult;
import com.goodfellaz17.order.repository.PlayOrderTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * TaskExecutionService: orchestrates task lifecycle from PENDING → EXECUTING → COMPLETED/FAILED.
 *
 * Responsibility:
 * - Validate task preconditions (PENDING status)
 * - Build SpotifyPlayCommand from OrderTask
 * - Call SpotifyPlayPort to execute
 * - Persist task status updates
 *
 * Invariants preserved:
 * - INV-2: Task transitions only through valid states
 * - INV-4: Set startedAt + assignedProxyNode before execution
 * - INV-5: Set completedAt on final state
 */
@Service
public class TaskExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);

    private final SpotifyPlayPort spotifyPlayPort;
    private final PlayOrderTaskRepository taskRepository;

    @Value("${task.proxy-node:local-executor}")
    private String assignedProxyNode;

    public TaskExecutionService(
        SpotifyPlayPort spotifyPlayPort,
        PlayOrderTaskRepository taskRepository) {
        this.spotifyPlayPort = spotifyPlayPort;
        this.taskRepository = taskRepository;
    }

    /**
     * Execute a single task.
     *
     * Precondition: task.status == PENDING
     * Postcondition: task persisted with COMPLETED or FAILED status
     *
     * @param task OrderTask in PENDING state
     * @return Mono<OrderTask> with final status
     */
    public Mono<OrderTask> executeTask(OrderTask task) {
        // Validate precondition (INV-2, INV-4)
        if (!"PENDING".equals(task.getStatus())) {
            return Mono.error(new IllegalStateException(
                "Task " + task.getId() + " is not PENDING; cannot execute (status=" + task.getStatus() + ")"));
        }

        // Move to EXECUTING state, set startedAt (INV-4)
        task.setStatus("EXECUTING");
        task.setStartedAt(Instant.now());
        task.setAssignedProxyNode(assignedProxyNode);

        return taskRepository.save(task)
            .flatMap(executingTask -> {
                // Build command and execute
                // FIXED: Use track ID from Order (not available in OrderTask directly)
                // For now, use placeholder - future: inject OrderRepository and fetch Order by ID
                String trackId = "spotify:track:placeholder";

                SpotifyPlayCommand command = new SpotifyPlayCommand(
                    executingTask.getId(),
                    executingTask.getOrderId(),
                    trackId,
                    executingTask.getAccountId(),
                    assignedProxyNode,
                    executingTask.getRetryCount(),
                    executingTask.getMaxRetries(),
                    Instant.now(),
                    5000L  // 5 sec timeout
                );

                return spotifyPlayPort.startPlay(command)
                    .flatMap(result -> updateTaskWithResult(executingTask, result))
                    .onErrorResume(ex -> failTaskWithError(executingTask, ex));
            })
            .doOnError(err -> log.error("Task execution failed: {}", task.getId(), err));
    }

    /**
     * Update task based on successful PlayResult.
     * Sets final status (COMPLETED/FAILED) and timestamps (INV-5).
     */
    private Mono<OrderTask> updateTaskWithResult(OrderTask task, PlayResult result) {
        task.setCompletedAt(Instant.now());  // INV-5
        task.setRetryCount(result.attempts());

        if (result.success()) {
            task.setStatus("COMPLETED");
            log.info("Task {} completed successfully", task.getId());
        } else {
            task.setStatus("FAILED");
            task.setFailureReason(result.failureReason());
            log.warn("Task {} failed: {}", task.getId(), result.failureReason());
        }

        return taskRepository.save(task);
    }

    /**
     * Handle unexpected errors (network, timeout, etc.).
     * Decide retry vs fail based on retry count.
     */
    private Mono<OrderTask> failTaskWithError(OrderTask task, Throwable error) {
        task.setCompletedAt(Instant.now());  // INV-5
        task.incrementRetry();

        if (task.canRetry()) {
            task.setStatus("PENDING");  // Allow retry
            task.setFailureReason(error.getMessage());
            log.info("Task {} retriable; resetting to PENDING (attempts: {}/{})",
                task.getId(), task.getRetryCount(), task.getMaxRetries());
        } else {
            task.setStatus("FAILED");
            task.setFailureReason(error.getClass().getSimpleName() + ": " + error.getMessage());
            log.warn("Task {} exhausted retries; marking FAILED", task.getId());
        }

        return taskRepository.save(task);
    }
}
