package com.goodfellaz17.application.service;

import com.goodfellaz17.domain.model.GeoTarget;
import com.goodfellaz17.domain.model.Proxy;
import com.goodfellaz17.domain.model.StreamResult;
import com.goodfellaz17.domain.port.StreamTaskRepositoryPort;
import com.goodfellaz17.domain.port.StreamingAdapter;
import com.goodfellaz17.infrastructure.bot.ResidentialProxyPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReactiveStreamingService {

    private final ResidentialProxyPool proxyPool;
    private final StreamingAdapter workerClient;

    @Autowired(required = false)
    private StreamTaskRepositoryPort taskRepo;

    /**
     * Executes a batch of streams with high concurrency and backpressure.
     * Parallelism: 50 concurrent streams.
     */
    public Flux<StreamResult> executeStreamBatch(int batchSize, String trackId) {
        return Flux.range(0, batchSize)
                .flatMap(i -> {
                    Proxy proxy = proxyPool.nextFor(GeoTarget.WORLDWIDE);
                    return workerClient.executeStream(trackId, proxy)
                            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                            .timeout(Duration.ofMinutes(5));
                }, 50) // Concurrency: 50 parallel streams
                .flatMap(result -> {
                    if (taskRepo != null) {
                        return taskRepo.save(result)
                                .doOnNext(saved -> log.debug("Stream result saved: {}", saved.id()))
                                .onErrorResume(e -> {
                                    log.error("Failed to save stream result: {}", e.getMessage());
                                    return Mono.just(result);
                                });
                    } else {
                        // Repository not available (e.g., startup phase) - just continue without saving
                        return Mono.just(result);
                    }
                })
                .onErrorContinue((error, obj) ->
                        log.error("Critical stream failure: {}", error.getMessage()));
    }
}
