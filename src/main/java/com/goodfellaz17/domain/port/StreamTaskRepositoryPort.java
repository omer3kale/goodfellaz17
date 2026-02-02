package com.goodfellaz17.domain.port;

import com.goodfellaz17.domain.model.StreamResult;
import reactor.core.publisher.Mono;

public interface StreamTaskRepositoryPort {
    Mono<StreamResult> save(StreamResult result);
}
