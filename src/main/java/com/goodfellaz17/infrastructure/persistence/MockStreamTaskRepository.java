package com.goodfellaz17.infrastructure.persistence;

import com.goodfellaz17.domain.model.StreamResult;
import com.goodfellaz17.domain.port.StreamTaskRepositoryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@Profile("test")
public class MockStreamTaskRepository implements StreamTaskRepositoryPort {
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Mono<StreamResult> save(StreamResult result) {
        return Mono.just(new StreamResult(
                idGenerator.getAndIncrement(),
                result.proxyId(),
                result.trackId(),
                result.duration(),
                result.completedAt(),
                result.status(),
                result.errorMessage()
        ));
    }
}
