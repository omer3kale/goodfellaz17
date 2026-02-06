package com.goodfellaz17.order.service;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;

import com.goodfellaz17.order.domain.OrderTask;
import com.goodfellaz17.order.domain.port.PlayResult;
import com.goodfellaz17.order.domain.port.SpotifyPlayCommand;
import com.goodfellaz17.order.domain.port.SpotifyPlayPort;
import com.goodfellaz17.order.repository.PlayOrderTaskRepository;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Tests for TaskExecutionService.
 *
 * Invariants validated:
 * - INV-2: Task status transitions only through valid states
 * - INV-4: startedAt + assignedProxyNode set before execution
 * - INV-5: completedAt set on final status
 */
@DisplayName("TaskExecutionService Tests")
class TaskExecutionServiceTest {

    private TaskExecutionService service;
    private SpotifyPlayPort spotifyPlayPort;
    private PlayOrderTaskRepository taskRepository;

    @BeforeEach
    void setup() {
        spotifyPlayPort = Mockito.mock(SpotifyPlayPort.class);
        taskRepository = Mockito.mock(PlayOrderTaskRepository.class);
        service = new TaskExecutionService(spotifyPlayPort, taskRepository);
    }

    @Test
    @DisplayName("execute success - task becomes COMPLETED with timestamps")
    void testExecuteTask_Success_UpdatesTaskToCompleted() {
        // Arrange: Create a PENDING task
        OrderTask task = new OrderTask();
        task.setId(UUID.randomUUID());
        task.setOrderId(UUID.randomUUID());
        task.setAccountId("account-123");
        task.setStatus("PENDING");
        task.setMaxRetries(3);

        // Mock successful play result
        PlayResult successResult = new PlayResult(
                true,
                null,
                "local-executor-1",
                1,
                Instant.now(),
                Instant.now(),
                150L);
        when(spotifyPlayPort.startPlay(any(SpotifyPlayCommand.class)))
                .thenReturn(Mono.just(successResult));

        // Mock repo saves (typed argument to avoid unchecked conversion)
        when(taskRepository.save(any(OrderTask.class))).thenAnswer(invocation -> {
            OrderTask savedTask = invocation.getArgument(0, OrderTask.class);
            return Mono.just(savedTask);
        });

        // Act & Assert
        StepVerifier.create(service.executeTask(task))
                .assertNext(completedTask -> {
                    // INV-4: startedAt set
                    assertNotNull(completedTask.getStartedAt());
                    assertNotNull(completedTask.getAssignedProxyNode());

                    // INV-5: completedAt set
                    assertNotNull(completedTask.getCompletedAt());

                    // INV-2: status is COMPLETED
                    assertEquals("COMPLETED", completedTask.getStatus());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("execute failure - task becomes FAILED with reason")
    void testExecuteTask_Failure_UpdatesTaskToFailed() {
        // Arrange
        OrderTask task = new OrderTask();
        task.setId(UUID.randomUUID());
        task.setOrderId(UUID.randomUUID());
        task.setAccountId("account-123");
        task.setStatus("PENDING");
        task.setMaxRetries(0); // No retries

        PlayResult failureResult = new PlayResult(
                false,
                "account_not_found",
                "local-executor-1",
                1,
                Instant.now(),
                Instant.now(),
                200L);

        when(spotifyPlayPort.startPlay(any(SpotifyPlayCommand.class)))
                .thenReturn(Mono.just(failureResult));

        when(taskRepository.save(any(OrderTask.class))).thenAnswer(invocation -> {
            OrderTask savedTask = invocation.getArgument(0, OrderTask.class);
            return Mono.just(savedTask);
        });

        // Act & Assert
        StepVerifier.create(service.executeTask(task))
                .assertNext(failedTask -> {
                    assertEquals("FAILED", failedTask.getStatus());
                    assertEquals("account_not_found", failedTask.getFailureReason());
                    assertNotNull(failedTask.getCompletedAt()); // INV-5
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("execute non-pending task - throws error")
    void testExecuteTask_NotPending_ThrowsError() {
        // Arrange
        OrderTask task = new OrderTask();
        task.setId(UUID.randomUUID());
        task.setStatus("COMPLETED"); // Already done

        // Act & Assert
        StepVerifier.create(service.executeTask(task))
                .expectError(IllegalStateException.class)
                .verify();
    }
}
