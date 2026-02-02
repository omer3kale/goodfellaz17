package com.goodfellaz17.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BehaviorEngineTest {

    private final BehaviorEngine behaviorEngine = new BehaviorEngine();

    @Test
    @DisplayName("should generate randomized behaviors within acceptable bounds")
    void shouldGenerateRandomizedBehaviors() {
        Set<Integer> durations = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            BehaviorEngine.StreamBehavior behavior = behaviorEngine.generateBehavior();

            assertThat(behavior.getDuration()).isBetween(35000, 180000);
            durations.add(behavior.getDuration());
        }

        // Ensure randomization (at least 90 unique durations in 100 samples)
        assertThat(durations.size()).isGreaterThan(90);
    }
}
