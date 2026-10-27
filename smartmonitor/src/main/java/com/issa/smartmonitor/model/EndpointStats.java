package com.issa.smartmonitor.model;

import com.issa.smartmonitor.core.MonitorTuning;
import com.issa.smartmonitor.enums.BottleneckType;
import lombok.Getter;

import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Getter
public class EndpointStats {

    private final String endpointKey;
    private final MonitorTuning tuning;
    private final LongAdder callCount = new LongAdder();
    private final LongAdder errorCount = new LongAdder();
    private final AtomicLong totalTimeNanos = new AtomicLong(0);
    private final AtomicLong maxTimeNanos = new AtomicLong(0);
    private final AtomicLong dbTimeNanos = new AtomicLong(0);
    private final AtomicLong memoryBytes = new AtomicLong(0);
    private final AtomicLong lastAccessMillis = new AtomicLong(System.currentTimeMillis());
    private final LatencyReservoir latencyReservoir;

    private final ConcurrentHashMap<String, MethodStats> methods = new ConcurrentHashMap<>();

    public EndpointStats(String endpointKey, MonitorTuning tuning) {
        this.endpointKey = endpointKey;
        this.tuning = tuning;
        this.latencyReservoir = new LatencyReservoir(tuning.reservoirCapacity());
    }

    public void recordRootCall(long durationNanos, boolean isError, long memoryBytes) {
        callCount.increment();
        if (isError) errorCount.increment();
        totalTimeNanos.addAndGet(durationNanos);
        maxTimeNanos.updateAndGet(current -> Math.max(current, durationNanos));
        latencyReservoir.record(durationNanos);
        this.memoryBytes.addAndGet(Math.max(memoryBytes, 0));
        lastAccessMillis.set(System.currentTimeMillis());
    }

    public void addDbTime(long durationNanos) { dbTimeNanos.addAndGet(durationNanos); }

    public MethodStats methodStats(String className, String methodName, String layer, int maxMethods) {
        String key = tuning.methodKey(className, methodName);
        MethodStats existing = methods.get(key);
        if (existing != null) return existing;

        if (methods.size() >= maxMethods) {
            methods.values().stream()
                    .min(Comparator.comparingLong(MethodStats::lastAccessMillis))
                    .ifPresent(oldest -> methods.remove(tuning.methodKey(oldest.getClassName(), oldest.getMethodName())));
        }
        return methods.computeIfAbsent(key, k -> new MethodStats(className, methodName, layer, tuning));
    }

    public double totalTimeMs() { return tuning.ms(totalTimeNanos.get()); }
    public double maxTimeMs() { return tuning.ms(maxTimeNanos.get()); }
    public double p95TimeMs() { return latencyReservoir.percentile(tuning.p95(), tuning.nanosPerMilli(), tuning.percentScale()); }
    public double p99TimeMs() { return latencyReservoir.percentile(tuning.p99(), tuning.nanosPerMilli(), tuning.percentScale()); }
    public double avgTimeMs() {
        long calls = callCount.sum();
        return calls == 0 ? 0 : tuning.ms(totalTimeNanos.get()) / calls;
    }
    public double dbTimeMs() { return tuning.ms(dbTimeNanos.get()); }
    public double memoryKb() { return tuning.kb(memoryBytes.get()); }
    public long lastAccessMillis() { return lastAccessMillis.get(); }

    public Collection<MethodStats> getMethods() {
        return methods.values();
    }

    public String bottleneckType() {
        double total = totalTimeMs();
        if (total == 0) return BottleneckType.UNKNOWN.name();

        double dbRatio = dbTimeMs() / total;
        long calls = callCount.sum();
        double errorRate = calls == 0 ? 0 : (double) errorCount.sum() / calls;

        if (dbRatio > tuning.endpointDbRatio()) return BottleneckType.DATABASE.name();
        if (errorRate > tuning.endpointErrorRate()) return BottleneckType.UNKNOWN.name();
        return BottleneckType.CPU.name();
    }
}