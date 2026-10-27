package com.issa.smartmonitor.core;

import com.issa.smartmonitor.config.SmartMonitorProperties;
import com.issa.smartmonitor.model.*;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

public class MetricRegistry {

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
    @Getter
    private final MonitorTuning tuning;

    private final Deque<ErrorEntry> recentErrors = new ArrayDeque<>();
    private final Object errorsLock = new Object();

    private final Deque<RequestTrace> recentTraces = new ArrayDeque<>();
    private final Object tracesLock = new Object();

    public MetricRegistry(SmartMonitorProperties properties) {
        this.maxEndpoints = properties.getMaxEndpoints();
        this.maxMethodsPerEndpoint = properties.getMaxMethodsPerEndpoint();
        this.tuning = MonitorTuning.from(properties);
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
            ep = targetMap.computeIfAbsent(endpointKey, key -> new EndpointStats(key, tuning));
        }
        if (isRoot) ep.recordRootCall(durationNanos, isError, memoryBytes);

        MethodStats ms = ep.methodStats(className, methodName, layer, maxMethodsPerEndpoint);
        ms.record(durationNanos, isError, memoryBytes);
        ms.addSelfTime(selfTimeNanos);
    }

    public void addDbTimeToEndpoint(String endpointKey, long durationNanos) {
        EndpointStats ep = endpoints.get(endpointKey);
        if (ep != null) ep.addDbTime(durationNanos);
    }

    public void recordEdge(String parentKey, String childKey, long durationNanos) {
        String edge = parentKey + tuning.edgeSeparator() + childKey;
        edgeTimeNanos.computeIfAbsent(edge, k -> new LongAdder()).add(durationNanos);
        edgeCallCount.computeIfAbsent(edge, k -> new LongAdder()).increment();
    }

    public List<Map<String, Object>> getCallGraph() {
        return edgeTimeNanos.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum()))
                .limit(tuning.callGraphLimit())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("edge", e.getKey());
                    m.put("totalTimeMs", tuning.round(tuning.ms(e.getValue().sum())));
                    m.put("callCount", edgeCallCount.get(e.getKey()).sum());
                    return m;
                })
                .collect(Collectors.toList());
    }

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
            while (recentErrors.size() > tuning.maxErrors()) {
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
            recentTraces.addFirst(new RequestTrace(System.currentTimeMillis(), traceId, endpointKey, tuning.ms(durationNanos), isError));
            while (recentTraces.size() > tuning.maxTraces()) {
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