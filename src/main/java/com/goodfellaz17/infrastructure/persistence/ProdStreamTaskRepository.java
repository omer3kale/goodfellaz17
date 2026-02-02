package com.goodfellaz17.infrastructure.persistence;

import com.goodfellaz17.domain.model.StreamResult;
import com.goodfellaz17.domain.port.StreamTaskRepositoryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Production implementation of StreamTaskRepositoryPort.
 * Stores StreamResults in-memory for the reactive streaming pipeline.
 * TODO: Replace with R2DBC persistence after thesis demo.
 */
@Slf4j
@Component
@Profile("!test")
public class ProdStreamTaskRepository implements StreamTaskRepositoryPort {
    private final ConcurrentHashMap<Long, StreamResult> store = new ConcurrentHashMap<>();
    private volatile long idCounter = 0;

    @Override
    public Mono<StreamResult> save(StreamResult result) {
        long id = ++idCounter;
        store.put(id, result);
        log.debug("Thesis stream result saved: proxyId={}, trackId={}, status={}",
            result.proxyId(), result.trackId(), result.status());
        return Mono.just(result);
    }
}
