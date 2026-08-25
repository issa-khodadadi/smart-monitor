package com.issa.smartmonitor.core;

import com.issa.smartmonitor.model.EndpointStats;
import com.issa.smartmonitor.model.ErrorEntry;
import com.issa.smartmonitor.model.MethodStats;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

public class MetricRegistry {

    private final ConcurrentHashMap<String, MethodStats> stats = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EndpointStats> endpoints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> edgeTimeNanos = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> edgeCallCount = new ConcurrentHashMap<>();

    private final int maxEndpoints;
    private final int maxMethodsPerEndpoint;

    private final Deque<ErrorEntry> recentErrors = new ArrayDeque<>();
    private final Object errorsLock = new Object();
    private static final int MAX_ERRORS = 50;

    public MetricRegistry(int maxEndpoints, int maxMethodsPerEndpoint) {
        this.maxEndpoints = maxEndpoints;
        this.maxMethodsPerEndpoint = maxMethodsPerEndpoint;
    }

    public void record(String className, String methodName, String layer, long durationNanos, boolean isError, long memoryBytes) {
        String key = className + "#" + methodName;
        stats.computeIfAbsent(key, k -> new MethodStats(className, methodName, layer))
                .record(durationNanos, isError, memoryBytes);
    }

    public void recordToEndpoint(String endpointKey, String className, String methodName, String layer,
                                 long durationNanos, long selfTimeNanos, boolean isError, long memoryBytes, boolean isRoot) {
        EndpointStats ep = endpoints.get(endpointKey);
        if (ep == null) {
            if (endpoints.size() >= maxEndpoints) {
                endpoints.values().stream()
                        .min(Comparator.comparingLong(EndpointStats::lastAccessMillis))
                        .ifPresent(oldest -> endpoints.remove(oldest.getEndpointKey()));
            }
            ep = endpoints.computeIfAbsent(endpointKey, EndpointStats::new);
        }
        if (isRoot) ep.recordRootCall(durationNanos, isError, memoryBytes);

        MethodStats ms = ep.methodStats(className, methodName, layer, maxMethodsPerEndpoint);
        ms.record(durationNanos, isError, memoryBytes);
        ms.addSelfTime(selfTimeNanos);
    }

    public void addDbTimeToCaller(String key, long durationNanos) {
        MethodStats s = stats.get(key);
        if (s != null) s.addDbTime(durationNanos);
    }

    public void addDbTimeToEndpoint(String endpointKey, long durationNanos) {
        EndpointStats ep = endpoints.get(endpointKey);
        if (ep != null) ep.addDbTime(durationNanos);
    }

    public void addSelfTime(String key, long durationNanos) {
        MethodStats s = stats.get(key);
        if (s != null) s.addSelfTime(durationNanos);
    }

    public void recordEdge(String parentKey, String childKey, long durationNanos) {
        String edge = parentKey + " -> " + childKey;
        edgeTimeNanos.computeIfAbsent(edge, k -> new LongAdder()).add(durationNanos);
        edgeCallCount.computeIfAbsent(edge, k -> new LongAdder()).increment();
    }

    public List<Map<String, Object>> getCallGraph() {
        return edgeTimeNanos.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum()))
                .limit(20)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("edge", e.getKey());
                    m.put("totalTimeMs", Math.round((e.getValue().sum() / 1_000_000.0) * 100.0) / 100.0);
                    m.put("callCount", edgeCallCount.get(e.getKey()).sum());
                    return m;
                })
                .collect(Collectors.toList());
    }

    public Collection<MethodStats> getAll() { return stats.values(); }
    public Collection<EndpointStats> getEndpoints() { return endpoints.values(); }
    public EndpointStats getEndpoint(String key) { return endpoints.get(key); }

    /** Removes endpoints (and their edges) not accessed within windowMillis. */
    public void evictOlderThan(long windowMillis) {
        long cutoff = System.currentTimeMillis() - windowMillis;

        Set<String> removedEndpointKeys = new HashSet<>();
        endpoints.entrySet().removeIf(e -> {
            boolean expired = e.getValue().lastAccessMillis() < cutoff;
            if (expired) removedEndpointKeys.add(e.getKey());
            return expired;
        });

        if (!removedEndpointKeys.isEmpty()) {
            edgeTimeNanos.keySet().removeIf(edge -> removedEndpointKeys.stream().anyMatch(edge::startsWith));
            edgeCallCount.keySet().removeIf(edge -> removedEndpointKeys.stream().anyMatch(edge::startsWith));
        }
    }

    public void recordError(String endpointKey, String className, String methodName, String exceptionType, String message) {
        synchronized (errorsLock) {
            recentErrors.addFirst(new ErrorEntry(System.currentTimeMillis(), endpointKey, className, methodName, exceptionType, message));
            while (recentErrors.size() > MAX_ERRORS) {
                recentErrors.removeLast();
            }
        }
    }

    public List<ErrorEntry> getRecentErrors() {
        synchronized (errorsLock) {
            return List.copyOf(recentErrors);
        }
    }
}