package com.goodfellaz17.order.integration;

import com.goodfellaz17.application.service.ReactiveStreamingService;
import com.goodfellaz17.domain.model.StreamResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for reactive streaming throughput.
 * Requires Docker. Skip with SKIP_DOCKER_TESTS=true
 */
@DisabledIfEnvironmentVariable(named = "SKIP_DOCKER_TESTS", matches = "true")
public class ReactiveStreamingIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ReactiveStreamingService streamingService;

    @Test
    @DisplayName("should execute 100 streams in under 10 seconds with 50 concurrency")
    void shouldExecuteStreamBatchWithHighThroughput() {
        int batchSize = 100;
        String trackId = "spotify:track:123456789";

        // Execution and timing
        long startTime = System.currentTimeMillis();

        StepVerifier.create(streamingService.executeStreamBatch(batchSize, trackId))
                .expectNextCount(batchSize)
                .verifyComplete();

        long duration = System.currentTimeMillis() - startTime;

        // Assertions
        assertThat(duration).isLessThan(10000); // Should complete in < 10s given 50 concurrency and 100ms artificial delay
        System.out.println("Batch of " + batchSize + " streams completed in " + duration + "ms");
    }
}
