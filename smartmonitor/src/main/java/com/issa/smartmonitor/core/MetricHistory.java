package com.issa.smartmonitor.core;

import com.issa.smartmonitor.model.EndpointStats;
import com.issa.smartmonitor.model.MetricSnapshot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class MetricHistory {

    private final int maxPoints;
    private final Deque<MetricSnapshot> snapshots = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();

    private double lastTotalTimeMs = 0;
    private double lastDbTimeMs = 0;
    private long lastErrorCount = 0;
    private long lastSampleMillis = System.currentTimeMillis();

    public MetricHistory(int maxPoints) {
        this.maxPoints = maxPoints;
    }

    public void sample(MetricRegistry registry) {
        long now = System.currentTimeMillis();

        double totalTime = 0, dbTime = 0;
        long totalErrors = 0;

        for (EndpointStats ep : registry.getEndpoints()) {
            totalTime += ep.totalTimeMs();
            dbTime += ep.dbTimeMs();
            totalErrors += ep.getErrorCount().sum();
        }

        double elapsedSeconds = Math.max((now - lastSampleMillis) / 1000.0, 1.0);

        double dbDelta = Math.max(dbTime - lastDbTimeMs, 0);
        double totalDelta = Math.max(totalTime - lastTotalTimeMs, 0);
        double cpuDelta = Math.max(totalDelta - dbDelta, 0);

        double dbPerSec = dbDelta / elapsedSeconds;
        double cpuPerSec = cpuDelta / elapsedSeconds;
        long errorDelta = Math.max(totalErrors - lastErrorCount, 0);

        MetricSnapshot snapshot = new MetricSnapshot(now, round(cpuPerSec), round(dbPerSec), errorDelta);

        lock.lock();
        try {
            snapshots.addLast(snapshot);
            while (snapshots.size() > maxPoints) {
                snapshots.removeFirst();
            }
        } finally {
            lock.unlock();
        }

        lastTotalTimeMs = totalTime;
        lastDbTimeMs = dbTime;
        lastErrorCount = totalErrors;
        lastSampleMillis = now;
    }

    public List<MetricSnapshot> getSnapshots() {
        lock.lock();
        try {
            return List.copyOf(snapshots);
        } finally {
            lock.unlock();
        }
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}