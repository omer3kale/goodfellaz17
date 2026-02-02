package com.goodfellaz17.domain.port;

import com.goodfellaz17.domain.model.Proxy;
import com.goodfellaz17.domain.model.StreamResult;
import reactor.core.publisher.Mono;

/**
 * Domain Port for executing streaming tasks.
 * Can be implemented by real headless workers or simulated adapters.
 */
public interface StreamingAdapter {
    Mono<StreamResult> executeStream(String trackUrl, Proxy proxy);
}
