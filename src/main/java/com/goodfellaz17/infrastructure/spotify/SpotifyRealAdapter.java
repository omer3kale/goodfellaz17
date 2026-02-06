package com.goodfellaz17.infrastructure.spotify;

import com.goodfellaz17.application.proxy.ProxyTaskDispatchService;
import com.goodfellaz17.domain.model.generated.OrderTaskEntity;
import com.goodfellaz17.order.domain.port.SpotifyPlayCommand;
import com.goodfellaz17.order.domain.port.SpotifyPlayPort;
import com.goodfellaz17.order.domain.port.PlayResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * SpotifyRealAdapter: PRODUCTION Spotify execution via proxy network.
 *
 * Uses ProxyTaskDispatchService to execute real plays through:
 * - Two-machine proxy architecture (Mac + Laptop)
 * - Health-aware proxy selection (HEALTHY > DEGRADED > OFFLINE)
 * - Automatic retry with exponential backoff
 *
 * Invariants enforced:
 * - INV-5: Always returns PlayResult with completedAt timestamp
 * - Proxy health maintained via ProxyMetricsController updates
 *
 * Activation: SPRING_PROFILES_ACTIVE=prod (or app.spotify.adapter=real)
 */
@Service
@ConditionalOnProperty(name = "app.spotify.adapter", havingValue = "real")
public class SpotifyRealAdapter implements SpotifyPlayPort {
    private static final Logger log = LoggerFactory.getLogger(SpotifyRealAdapter.class);

    private final ProxyTaskDispatchService proxyDispatch;

    public SpotifyRealAdapter(ProxyTaskDispatchService proxyDispatch) {
        this.proxyDispatch = proxyDispatch;
    }

    /**
     * Execute real Spotify play via proxy network.
     *
     * Flow:
     * 1. Dispatch task to proxy machine (Mac/Laptop)
     * 2. Proxy executes real Spotify track play via Selenium
     * 3. Wait for playback completion (30+ seconds for royalty)
     * 4. Return success/failure with timing data
     *
     * @param command Contains track, account, retry context
     * @return Mono<PlayResult> with real proxy execution result
     */
    @Override
    public Mono<PlayResult> startPlay(SpotifyPlayCommand command) {
        Instant startedAt = Instant.now();

        log.info("🎵 SpotifyRealAdapter: Dispatching to proxy network | taskId={} | track={} | account={}",
                command.taskId(), command.trackId(), command.accountId());

        // Create minimal OrderTaskEntity for dispatch (only needs ID and trackUrl)
        // In real scenario, this would come from the task execution pipeline
        OrderTaskEntity dummyTask = new OrderTaskEntity();
        dummyTask.setId(command.taskId());
        dummyTask.setOrderId(command.orderId());
        dummyTask.setQuantity(1);  // Single track play

        String trackUrl = "spotify:track:" + command.trackId();

        // Dispatch via proxy network (health-aware selection)
        return proxyDispatch.dispatchTask(dummyTask, trackUrl)
                .map(dispatchResult -> {
                    Instant completedAt = Instant.now();
                    long durationMs = Duration.between(startedAt, completedAt).toMillis();

                    boolean success = dispatchResult.success();
                    String nodeId = dispatchResult.proxy() != null
                            ? dispatchResult.proxy().getId().toString()
                            : "dispatch-failed";

                    if (success) {
                        log.info("✅ Play delivered via proxy | taskId={} | node={} | duration={}ms",
                                command.taskId(), nodeId, durationMs);

                        return new PlayResult(
                                true,
                                null,  // no error
                                nodeId,
                                command.retryCount() + 1,
                                startedAt,
                                completedAt,
                                durationMs
                        );
                    } else {
                        String errorMsg = dispatchResult.errorMessage() != null
                                ? dispatchResult.errorMessage()
                                : "proxy_execution_failed";

                        log.warn("❌ Play failed via proxy | taskId={} | node={} | error={}",
                                command.taskId(), nodeId, errorMsg);

                        return new PlayResult(
                                false,
                                errorMsg,
                                nodeId,
                                command.retryCount() + 1,
                                startedAt,
                                completedAt,
                                durationMs
                        );
                    }
                })
                .onErrorResume(error -> {
                    Instant completedAt = Instant.now();
                    long durationMs = Duration.between(startedAt, completedAt).toMillis();

                    log.error("🚨 SpotifyRealAdapter error | taskId={} | error={}",
                            command.taskId(), error.getMessage(), error);

                    // Return failed result preserving invariants
                    return Mono.just(new PlayResult(
                            false,
                            "adapter_error: " + error.getMessage(),
                            "error-handler",
                            command.retryCount() + 1,
                            startedAt,
                            completedAt,
                            durationMs
                    ));
                });
    }
}
