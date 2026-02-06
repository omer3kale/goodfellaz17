package com.goodfellaz17.domain.service;

import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class BehaviorEngine {
    private static final int MIN_STREAM_DURATION = 35000; // 35s
    private static final int MAX_STREAM_DURATION = 180000; // 3min
    private final Random random = new Random();

    public static class StreamBehavior {
        private int duration;
        private int seekCount;
        private int pauseCount;
        private int volumeChanges;
        private int delayBefore;

        public StreamBehavior(int duration, int seekCount, int pauseCount, int volumeChanges, int delayBefore) {
            this.duration = duration;
            this.seekCount = seekCount;
            this.pauseCount = pauseCount;
            this.volumeChanges = volumeChanges;
            this.delayBefore = delayBefore;
        }

        public int getDuration() { return duration; }
        public int getSeekCount() { return seekCount; }
        public int getPauseCount() { return pauseCount; }
        public int getVolumeChanges() { return volumeChanges; }
        public int getDelayBefore() { return delayBefore; }

        public static StreamBehaviorBuilder builder() { return new StreamBehaviorBuilder(); }

        public static class StreamBehaviorBuilder {
            private int duration;
            private int seekCount;
            private int pauseCount;
            private int volumeChanges;
            private int delayBefore;

            public StreamBehaviorBuilder duration(int duration) { this.duration = duration; return this; }
            public StreamBehaviorBuilder seekCount(int seekCount) { this.seekCount = seekCount; return this; }
            public StreamBehaviorBuilder pauseCount(int pauseCount) { this.pauseCount = pauseCount; return this; }
            public StreamBehaviorBuilder volumeChanges(int volumeChanges) { this.volumeChanges = volumeChanges; return this; }
            public StreamBehaviorBuilder delayBefore(int delayBefore) { this.delayBefore = delayBefore; return this; }

            public StreamBehavior build() {
                return new StreamBehavior(duration, seekCount, pauseCount, volumeChanges, delayBefore);
            }
        }
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
