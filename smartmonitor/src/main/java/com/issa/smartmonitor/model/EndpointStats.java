package com.issa.smartmonitor.model;

import lombok.Getter;

import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Getter
public class EndpointStats {

    private final String endpointKey;
    private final LongAdder callCount = new LongAdder();
    private final LongAdder errorCount = new LongAdder();
    private final AtomicLong totalTimeNanos = new AtomicLong(0);
    private final AtomicLong maxTimeNanos = new AtomicLong(0);
    private final AtomicLong dbTimeNanos = new AtomicLong(0);
    private final AtomicLong memoryBytes = new AtomicLong(0);
    private final AtomicLong lastAccessMillis = new AtomicLong(System.currentTimeMillis());

    private final ConcurrentHashMap<String, MethodStats> methods = new ConcurrentHashMap<>();

    public EndpointStats(String endpointKey) {
        this.endpointKey = endpointKey;
    }

    public void recordRootCall(long durationNanos, boolean isError, long memoryBytes) {
        callCount.increment();
        if (isError) errorCount.increment();
        totalTimeNanos.addAndGet(durationNanos);
        maxTimeNanos.updateAndGet(current -> Math.max(current, durationNanos));
        this.memoryBytes.addAndGet(Math.max(memoryBytes, 0));
        lastAccessMillis.set(System.currentTimeMillis());
    }

    public void addDbTime(long durationNanos) { dbTimeNanos.addAndGet(durationNanos); }

    public MethodStats methodStats(String className, String methodName, String layer, int maxMethods) {
        String key = className + "#" + methodName;
        MethodStats existing = methods.get(key);
        if (existing != null) return existing;

        if (methods.size() >= maxMethods) {
            methods.values().stream()
                    .min(Comparator.comparingLong(MethodStats::lastAccessMillis))
                    .ifPresent(oldest -> methods.remove(oldest.getClassName() + "#" + oldest.getMethodName()));
        }
        return methods.computeIfAbsent(key, k -> new MethodStats(className, methodName, layer));
    }

    public double totalTimeMs() { return totalTimeNanos.get() / 1_000_000.0; }
    public double maxTimeMs() { return maxTimeNanos.get() / 1_000_000.0; }
    public double avgTimeMs() {
        long calls = callCount.sum();
        return calls == 0 ? 0 : (totalTimeNanos.get() / (double) calls) / 1_000_000.0;
    }
    public double dbTimeMs() { return dbTimeNanos.get() / 1_000_000.0; }
    public double memoryKb() { return memoryBytes.get() / 1024.0; }
    public long lastAccessMillis() { return lastAccessMillis.get(); }

    public Collection<MethodStats> getMethods() {
        return methods.values();
    }
}