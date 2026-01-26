package com.goodfellaz17.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the automatic task executor (background polling loop).
 *
 * Example application.yml:
 * app:
 *   task-executor:
 *     enabled: true
 *     poll-interval-ms: 100
 *     max-concurrent-tasks: 10
 */
@Component
@ConfigurationProperties(prefix = "app.task-executor")
public class TaskExecutorProperties {

    private boolean enabled = true;
    private int pollIntervalMs = 100;
    private int maxConcurrentTasks = 10;

    // Getters and setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(int pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getMaxConcurrentTasks() {
        return maxConcurrentTasks;
    }

    public void setMaxConcurrentTasks(int maxConcurrentTasks) {
        this.maxConcurrentTasks = maxConcurrentTasks;
    }
}
