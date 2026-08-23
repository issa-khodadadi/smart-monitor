package com.issa.smartmonitor.core;

import org.springframework.scheduling.annotation.Scheduled;

public class MetricSnapshotTask {

    private final MetricRegistry registry;
    private final MetricHistory history;

    public MetricSnapshotTask(MetricRegistry registry, MetricHistory history) {
        this.registry = registry;
        this.history = history;
    }

    @Scheduled(fixedDelay = 2000L)
    public void sample() {
        history.sample(registry);
    }
}