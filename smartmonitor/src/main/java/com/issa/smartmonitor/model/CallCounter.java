package com.issa.smartmonitor.model;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class CallCounter {

    private final ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();

    public void increment(String endpointKey) {
        counts.computeIfAbsent(endpointKey, k -> new LongAdder()).increment();
    }

    public long get(String endpointKey) {
        LongAdder adder = counts.get(endpointKey);
        return adder == null ? 0 : adder.sum();
    }

    public long total() {
        return counts.values().stream().mapToLong(LongAdder::sum).sum();
    }

    public java.util.List<java.util.Map.Entry<String, Long>> topByCallCount(int limit) {
        return counts.entrySet().stream()
                .map(e -> java.util.Map.entry(e.getKey(), e.getValue().sum()))
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
    }
}