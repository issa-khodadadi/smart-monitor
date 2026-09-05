package com.issa.smartmonitor.model;

import com.issa.smartmonitor.enums.BottleneckType;
import com.issa.smartmonitor.enums.Layer;
import lombok.Getter;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Getter
public class MethodStats {

    private final String className;
    private final String methodName;
    private final String layer;

    private final LongAdder callCount = new LongAdder();
    private final LongAdder errorCount = new LongAdder();
    private final AtomicLong totalTimeNanos = new AtomicLong(0);
    private final AtomicLong maxTimeNanos = new AtomicLong(0);
    private final AtomicLong dbTimeNanos = new AtomicLong(0);
    private final AtomicLong totalMemoryBytes = new AtomicLong(0);
    private final AtomicLong maxMemoryBytes = new AtomicLong(0);
    private final AtomicLong selfTimeNanos = new AtomicLong(0);
    private final AtomicLong lastAccessMillis = new AtomicLong(System.currentTimeMillis());
    private final LatencyReservoir latencyReservoir = new LatencyReservoir(512);

    public MethodStats(String className, String methodName, String layer) {
        this.className = className;
        this.methodName = methodName;
        this.layer = layer;
    }

    public void record(long durationNanos, boolean isError, long memoryBytes) {
        callCount.increment();
        if (isError) errorCount.increment();
        totalTimeNanos.addAndGet(durationNanos);
        maxTimeNanos.updateAndGet(current -> Math.max(current, durationNanos));
        latencyReservoir.record(durationNanos);
        if (memoryBytes > 0) {
            totalMemoryBytes.addAndGet(memoryBytes);
            maxMemoryBytes.updateAndGet(current -> Math.max(current, memoryBytes));
        }
        lastAccessMillis.set(System.currentTimeMillis());
    }

    public void addDbTime(long durationNanos) { dbTimeNanos.addAndGet(durationNanos); }
    public void addSelfTime(long durationNanos) { selfTimeNanos.addAndGet(Math.max(durationNanos, 0)); }

    public double avgTimeMs() {
        long calls = callCount.sum();
        if (calls == 0) return 0;
        return ((double) totalTimeNanos.get() / calls) / 1_000_000.0;
    }

    public double maxTimeMs() { return maxTimeNanos.get() / 1_000_000.0; }
    public double totalTimeMs() { return totalTimeNanos.get() / 1_000_000.0; }
    public double dbTimeMs() { return dbTimeNanos.get() / 1_000_000.0; }
    public double totalMemoryKb() { return totalMemoryBytes.get() / 1024.0; }
    public double maxMemoryKb() { return maxMemoryBytes.get() / 1024.0; }
    public double selfTimeMs() { return selfTimeNanos.get() / 1_000_000.0; }
    public long lastAccessMillis() { return lastAccessMillis.get(); }

    public double p95TimeMs() { return latencyReservoir.percentileMs(95); }
    public double p99TimeMs() { return latencyReservoir.percentileMs(99); }

    public String bottleneckType() {
        double total = totalTimeMs();
        if (total == 0)
            return BottleneckType.UNKNOWN.name();
        double dbRatio = dbTimeMs() / total;
        if (dbRatio > 0.6)
            return BottleneckType.DATABASE.name();
        if (Layer.REPOSITORY.name().equals(layer))
            return BottleneckType.DATABASE.name();
        return BottleneckType.CPU.name();
    }
}