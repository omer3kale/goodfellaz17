package com.goodfellaz17.infrastructure.bot;

import java.time.Duration;
import java.util.Map;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.goodfellaz17.domain.model.Proxy;
import com.goodfellaz17.domain.model.StreamResult;
import com.goodfellaz17.domain.port.StreamingAdapter;

import reactor.core.publisher.Mono;

@Service
@Primary
@Profile("prod")
public class NodeWorkerClient implements StreamingAdapter {

    private final WebClient webClient;

    public NodeWorkerClient(WebClient.Builder webClientBuilder) {
        // Pointing to the internal Docker service name or localhost for dev
        this.webClient = webClientBuilder
                .baseUrl("http://streaming-worker:3000")
                .build();
    }

    public Mono<StreamResult> executeStream(String trackUrl, Proxy proxy) {
        return webClient.post()
                .uri("/stream")
                .bodyValue(Map.of(
                        "trackUrl", trackUrl,
                        "proxy", Map.of(
                                "host", proxy.host(),
                                "port", proxy.port(),
                                "username", proxy.getUsername() != null ? proxy.getUsername() : "",
                                "password", proxy.getPassword() != null ? proxy.getPassword() : ""
                        )
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    boolean success = (Boolean) response.getOrDefault("success", false);
                    if (success) {
                        return StreamResult.success(proxy.getId(), trackUrl, (Integer) response.get("duration"));
                    } else {
                        return StreamResult.failure(proxy.getId(), trackUrl, (String) response.get("error"));
                    }
                })
                .timeout(Duration.ofMinutes(5));
    }
}
