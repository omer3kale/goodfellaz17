package com.goodfellaz17.application.service;

import com.goodfellaz17.domain.port.OrderRepositoryPort;
import com.goodfellaz17.domain.port.StreamTaskRepositoryPort;
import com.goodfellaz17.infrastructure.bot.PremiumAccountFarm;
import com.goodfellaz17.infrastructure.bot.ResidentialProxyPool;
import com.goodfellaz17.domain.model.GeoTarget;
import com.goodfellaz17.domain.model.Proxy;
import com.goodfellaz17.infrastructure.proxy.generated.HybridProxyRouterV2;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import reactor.core.publisher.Mono;

@SpringBootTest
@ActiveProfiles("test")
public class ReactiveStreamingUnitTest {

    @Autowired
    private ReactiveStreamingService streamingService;

    @MockBean
    private OrderRepositoryPort orderRepositoryPort;

    @MockBean
    private StreamTaskRepositoryPort streamTaskRepositoryPort;

    @MockBean
    private PremiumAccountFarm premiumAccountFarm;

    @MockBean
    private ResidentialProxyPool residentialProxyPool;

    @MockBean
    private HybridProxyRouterV2 hybridProxyRouter;

    @Test
    @DisplayName("should execute 100 simulated streams in under 10 seconds")
    void shouldExecuteSimulatedStreamBatch() {
        when(streamTaskRepositoryPort.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        Proxy mockProxy = Proxy.builder()
                .host("127.0.0.1")
                .port(8080)
                .username("user")
                .password("pass")
                .build();
        when(residentialProxyPool.nextFor(any())).thenReturn(mockProxy);

        int batchSize = 100;
        String trackId = "spotify:track:12345";

        long startTime = System.currentTimeMillis();

        StepVerifier.create(streamingService.executeStreamBatch(batchSize, trackId))
                .expectNextCount(batchSize)
                .verifyComplete();

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Simulated batch of " + batchSize + " completed in " + duration + "ms");

        // With 50 concurrency and 50ms-2s latency, 100 tasks should take ~4s max if
        // they are properly balanced.
        assertThat(duration).isLessThan(10000);
    }
}
