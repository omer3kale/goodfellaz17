package com.goodfellaz17.domain.service;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class BehaviorEngine {
    private static final int MIN_STREAM_DURATION = 35000; // 35s
    private static final int MAX_STREAM_DURATION = 180000; // 3min
    private final Random random = new Random();

    @Data
    @Builder
    public static class StreamBehavior {
        private int duration;
        private int seekCount;
        private int pauseCount;
        private int volumeChanges;
        private int delayBefore;
    }

    public StreamBehavior generateBehavior() {
        return StreamBehavior.builder()
                .duration(randomDuration())
                .seekCount(randomSeeks())
                .pauseCount(randomPauses())
                .volumeChanges(randomVolumeChanges())
                .delayBefore(randomDelay())
                .build();
    }

    private int randomDuration() {
        // Normal distribution around 50s (not uniform)
        int duration = (int) (MIN_STREAM_DURATION + (random.nextGaussian() * 15000) + 15000);
        return Math.min(MAX_STREAM_DURATION, Math.max(MIN_STREAM_DURATION, duration));
    }

    private int randomSeeks() {
        // 30% chance of 1-2 seeks
        return random.nextDouble() > 0.7 ? random.nextInt(2) + 1 : 0;
    }

    private int randomPauses() {
        return random.nextDouble() > 0.8 ? 1 : 0;
    }

    private int randomVolumeChanges() {
        return random.nextInt(3);
    }

    private int randomDelay() {
        // 2-8 minute delay between streams (exponential distribution simulation)
        return 120000 + random.nextInt(360000);
    }
}
