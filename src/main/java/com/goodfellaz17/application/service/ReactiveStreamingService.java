package com.goodfellaz17.application.service;

import com.goodfellaz17.domain.model.GeoTarget;
import com.goodfellaz17.domain.model.Proxy;
import com.goodfellaz17.domain.model.StreamResult;
import com.goodfellaz17.domain.model.StreamSession;
import com.goodfellaz17.domain.port.StreamTaskRepositoryPort;
import com.goodfellaz17.domain.port.StreamingAdapter;
import com.goodfellaz17.infrastructure.bot.ResidentialProxyPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ReactiveStreamingService {
    private static final Logger log = LoggerFactory.getLogger(ReactiveStreamingService.class);

    private final ResidentialProxyPool proxyPool;
    private final StreamingAdapter workerClient;

    public ReactiveStreamingService(ResidentialProxyPool proxyPool, StreamingAdapter workerClient) {
        this.proxyPool = proxyPool;
        this.workerClient = workerClient;
    }

    @Autowired(required = false)
    private StreamTaskRepositoryPort taskRepo;

    @Autowired(required = false)
    private SpotifyBehaviorEngine behaviorEngine;

    /**
     * Executes a batch of streams with high concurrency and backpressure.
     * Uses SpotifyBehaviorEngine for realistic session parameters.
     * Parallelism: Dynamic based on peak hours (50-75 concurrent streams).
     */
    public Flux<StreamResult> executeStreamBatch(int batchSize, String trackId) {
        int concurrency = behaviorEngine != null ? behaviorEngine.getOptimalConcurrency() : 50;

        log.info("ZORG✓ Starting batch: {} streams, concurrency={}, peak={}",
                 batchSize, concurrency,
                 behaviorEngine != null && behaviorEngine.isPeakHour());

        return Flux.range(0, batchSize)
                .flatMap(i -> {
                    Proxy proxy = proxyPool.nextFor(GeoTarget.WORLDWIDE);

                    // Generate realistic session parameters
                    StreamSession session = behaviorEngine != null
                        ? behaviorEngine.generateSession(trackId)
                        : createDefaultSession(trackId);

                    // Skip early skips (don't count as plays)
                    if (!session.countsAsPlay()) {
                        log.debug("ZORG✓ Session {} skipped ({}s)", session.sessionId(), session.durationSeconds());
                        return Mono.just(StreamResult.failure(proxy.getId(), trackId,
                            "SKIP:" + session.durationSeconds() + "s"));
                    }

                    return workerClient.executeStream(trackId, proxy)
                            .map(result -> enrichWithSession(result, session))
                            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                            .timeout(Duration.ofMinutes(5))
                            .delayElement(Duration.ofMillis(
                                behaviorEngine != null ? behaviorEngine.getInterSessionDelayMs() : 500));
                }, concurrency)
                .flatMap(result -> {
                    if (taskRepo != null) {
                        return taskRepo.save(result)
                                .doOnNext(saved -> log.debug("ZORG✓ Stream saved: {} ({}s)",
                                    saved.id(), saved.duration()))
                                .onErrorResume(e -> {
                                    log.error("Failed to save stream result: {}", e.getMessage());
                                    return Mono.just(result);
                                });
                    } else {
                        return Mono.just(result);
                    }
                })
                .onErrorContinue((error, obj) ->
                        log.error("Critical stream failure: {}", error.getMessage()));
    }

    /**
     * Creates a default session when behavior engine is not available.
     */
    private StreamSession createDefaultSession(String trackId) {
        return StreamSession.fullListen(
            UUID.randomUUID().toString(),
            trackId,
            65, // Default 65s
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) Safari/604.1",
            java.util.List.of()
        );
    }

    /**
     * Enriches stream result with session behavior data.
     */
    private StreamResult enrichWithSession(StreamResult result, StreamSession session) {
        return new StreamResult(
            result.id(),
            result.proxyId(),
            result.trackId(),
            session.durationSeconds(), // Use session duration
            result.completedAt(),
            result.status(),
            result.errorMessage()
        );
    }
}
