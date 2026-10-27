package com.issa.smartmonitor.model;

import com.issa.smartmonitor.core.MonitorTuning;
import com.issa.smartmonitor.enums.BottleneckType;
import lombok.Getter;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Getter
public class MethodStats {

    private final String className;
    private final String methodName;
    private final String layer;
    private final MonitorTuning tuning;

    private final LongAdder callCount = new LongAdder();
    private final LongAdder errorCount = new LongAdder();
    private final AtomicLong totalTimeNanos = new AtomicLong(0);
    private final AtomicLong dbTimeNanos = new AtomicLong(0);
    private final AtomicLong totalMemoryBytes = new AtomicLong(0);
    private final AtomicLong selfTimeNanos = new AtomicLong(0);
    private final AtomicLong lastAccessMillis = new AtomicLong(System.currentTimeMillis());
    private final LatencyReservoir latencyReservoir;

    public MethodStats(String className, String methodName, String layer, MonitorTuning tuning) {
        this.className = className;
        this.methodName = methodName;
        this.layer = layer;
        this.tuning = tuning;
        this.latencyReservoir = new LatencyReservoir(tuning.reservoirCapacity());
    }

    public void record(long durationNanos, boolean isError, long memoryBytes) {
        callCount.increment();
        if (isError) errorCount.increment();
        totalTimeNanos.addAndGet(durationNanos);
        latencyReservoir.record(durationNanos);
        if (memoryBytes > 0) {
            totalMemoryBytes.addAndGet(memoryBytes);
        }
        lastAccessMillis.set(System.currentTimeMillis());
    }

    public void addDbTime(long durationNanos) { dbTimeNanos.addAndGet(durationNanos); }
    public void addSelfTime(long durationNanos) { selfTimeNanos.addAndGet(Math.max(durationNanos, 0)); }

    public double totalTimeMs() { return tuning.ms(totalTimeNanos.get()); }
    public double dbTimeMs() { return tuning.ms(dbTimeNanos.get()); }
    public double totalMemoryKb() { return tuning.kb(totalMemoryBytes.get()); }
    public double selfTimeMs() { return tuning.ms(selfTimeNanos.get()); }
    public long lastAccessMillis() { return lastAccessMillis.get(); }

    public double p95TimeMs() { return latencyReservoir.percentile(tuning.p95(), tuning.nanosPerMilli(), tuning.percentScale()); }
    public double p99TimeMs() { return latencyReservoir.percentile(tuning.p99(), tuning.nanosPerMilli(), tuning.percentScale()); }

    public String bottleneckType() {
        double total = totalTimeMs();
        if (total == 0)
            return BottleneckType.UNKNOWN.name();
        double dbRatio = dbTimeMs() / total;
        if (dbRatio > tuning.methodDbRatio())
            return BottleneckType.DATABASE.name();
        if (tuning.repositoryLayer().equals(layer))
            return BottleneckType.DATABASE.name();
        return BottleneckType.CPU.name();
    }
}