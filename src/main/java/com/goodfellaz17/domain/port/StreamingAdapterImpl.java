package com.goodfellaz17.domain.port;

import org.springframework.stereotype.Service;

import com.goodfellaz17.domain.model.Proxy;
import com.goodfellaz17.domain.model.StreamResult;

import reactor.core.publisher.Mono;

/**
 * Stub StreamingAdapter for thesis demo.
 */
@Service
public class StreamingAdapterImpl implements StreamingAdapter {

    @Override
    public Mono<StreamResult> executeStream(String trackUrl, Proxy proxy) {
        return Mono.empty();
    }
}
