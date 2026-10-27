package com.issa.smartmonitor.core;

import com.issa.smartmonitor.config.SmartMonitorProperties;
import org.springframework.scheduling.annotation.Scheduled;

public class MetricCleanupTask {

    private final MetricRegistry registry;
    private final SmartMonitorProperties properties;

    public MetricCleanupTask(MetricRegistry registry, SmartMonitorProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${smartmonitor.cleanup-interval-millis}")
    public void cleanup() {
        long windowMillis = properties.getWindowMinutes() * properties.getUnits().getMillisPerMinute();
        registry.evictOlderThan(windowMillis);
    }
}