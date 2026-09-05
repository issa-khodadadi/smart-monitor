package com.issa.smartmonitor.core;

import com.issa.smartmonitor.model.*;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

public class MetricRegistry {

    private final ConcurrentHashMap<String, MethodStats> stats = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EndpointStats> endpoints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> edgeTimeNanos = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> edgeCallCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EndpointStats> backgroundRoots = new ConcurrentHashMap<>();

    @Getter
    private final CallCounter permanentCallCounter = new CallCounter();
    @Getter
    private final CallCounter permanentBackgroundCallCounter = new CallCounter();

    private final int maxEndpoints;
    private final int maxMethodsPerEndpoint;

    private final Deque<ErrorEntry> recentErrors = new ArrayDeque<>();
    private final Object errorsLock = new Object();
    private static final int MAX_ERRORS = 50;

    private final Deque<RequestTrace> recentTraces = new ArrayDeque<>();
    private final Object tracesLock = new Object();
    private static final int MAX_TRACES = 200;

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
                                 long durationNanos, long selfTimeNanos, boolean isError, long memoryBytes, boolean isRoot, boolean isHttpOrigin) {

        ConcurrentHashMap<String, EndpointStats> targetMap = isHttpOrigin ? endpoints : backgroundRoots;

        if (isRoot) {
            if (isHttpOrigin) {
                permanentCallCounter.increment(endpointKey);
            } else {
                permanentBackgroundCallCounter.increment(endpointKey);
            }
        }

        EndpointStats ep = targetMap.get(endpointKey);
        if (ep == null) {
            if (targetMap.size() >= maxEndpoints) {
                targetMap.values().stream()
                        .min(Comparator.comparingLong(EndpointStats::lastAccessMillis))
                        .ifPresent(oldest -> targetMap.remove(oldest.getEndpointKey()));
            }
            ep = targetMap.computeIfAbsent(endpointKey, EndpointStats::new);
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
    public Collection<EndpointStats> getBackgroundRoots() { return backgroundRoots.values(); }

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

        backgroundRoots.entrySet().removeIf(e -> e.getValue().lastAccessMillis() < cutoff);
    }

    public void recordError(String traceId, String endpointKey, String className, String methodName, String exceptionType, String message) {
        synchronized (errorsLock) {
            recentErrors.addFirst(new ErrorEntry(System.currentTimeMillis(), traceId, endpointKey, className, methodName, exceptionType, message));
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

    public void recordTrace(String traceId, String endpointKey, long durationNanos, boolean isError) {
        synchronized (tracesLock) {
            recentTraces.addFirst(new RequestTrace(System.currentTimeMillis(), traceId, endpointKey, durationNanos / 1_000_000.0, isError));
            while (recentTraces.size() > MAX_TRACES) {
                recentTraces.removeLast();
            }
        }
    }

    public List<RequestTrace> getRecentTraces() {
        synchronized (tracesLock) {
            return List.copyOf(recentTraces);
        }
    }
}