package com.goodfellaz17.infrastructure.bot;

import com.goodfellaz17.domain.model.Proxy;
import com.goodfellaz17.domain.model.StreamResult;
import com.goodfellaz17.domain.port.StreamingAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Random;

/**
 * Simulated implementation of StreamingAdapter for thesis evaluation.
 * Models realistic behavior WITHOUT real network calls.
 */
@Slf4j
@Service
@Profile({"test", "simulated"})
public class SimulatedStreamingAdapter implements StreamingAdapter {

    private final Random random = new Random();

    @Override
    public Mono<StreamResult> executeStream(String trackUrl, Proxy proxy) {
        // Model realistic latency (50ms - 2s)
        int latencyMs = 50 + random.nextInt(1951);

        // Model failure rates (2-5%)
        boolean isFailure = random.nextDouble() < 0.035; // ~3.5% failure rate

        return Mono.delay(Duration.ofMillis(latencyMs))
                .map(d -> {
                    if (isFailure) {
                        log.warn("SIMULATION: Stream failed for track {} via proxy {}", trackUrl, proxy.getHost());
                        return StreamResult.failure(proxy.getId(), trackUrl, "Simulated network timeout");
                    } else {
                        int duration = 35 + random.nextInt(26); // 35-60s simulated stream duration
                        log.info("SIMULATION: Stream successful for track {} ({}s) via proxy {}",
                                trackUrl, duration, proxy.getHost());
                        return StreamResult.success(proxy.getId(), trackUrl, duration);
                    }
                });
    }
}
