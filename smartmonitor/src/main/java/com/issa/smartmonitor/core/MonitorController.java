package com.issa.smartmonitor.core;

import com.issa.smartmonitor.ai.AiAnalysisResult;
import com.issa.smartmonitor.ai.AiAnalysisService;
import com.issa.smartmonitor.enums.BottleneckType;
import com.issa.smartmonitor.model.EndpointStats;
import com.issa.smartmonitor.model.MethodStats;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class MonitorController {

    private static final int TOP_N = 10;

    private final MetricRegistry registry;
    private final AiAnalysisService aiAnalysisService;
    private final MetricHistory history;
    private static final long N1_RATIO_THRESHOLD = 10;

    public MonitorController(MetricRegistry registry, ObjectProvider<AiAnalysisService> aiAnalysisServiceProvider, MetricHistory history) {
        this.registry = registry;
        this.aiAnalysisService = aiAnalysisServiceProvider.getIfAvailable();
        this.history = history;
    }

    @GetMapping(value = "/monitor/api/overview", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> overview() {
        long totalCalls = 0, totalErrors = 0;
        double totalTime = 0, dbTime = 0, memory = 0;

        for (EndpointStats ep : registry.getEndpoints()) {
            totalCalls += ep.getCallCount().sum();
            totalErrors += ep.getErrorCount().sum();
            totalTime += ep.totalTimeMs();
            dbTime += ep.dbTimeMs();
            memory += ep.memoryKb();
        }

        java.util.Set<String> fragmentedKeys = fragmentedEndpointKeys();

        Map<String, Object> topCpu = topBottleneckByType(BottleneckType.CPU.name(), fragmentedKeys);
        Map<String, Object> topIo = topBottleneckByType(BottleneckType.DATABASE.name(), fragmentedKeys);
        Map<String, Object> topOther = topBottleneckByType(BottleneckType.UNKNOWN.name(), fragmentedKeys);

        List<Map<String, Object>> topBottlenecks = registry.getEndpoints().stream()
                .sorted(Comparator.comparingDouble(EndpointStats::totalTimeMs).reversed())
                .limit(5)
                .map(ep -> endpointSummary(ep, fragmentedKeys))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCalls", registry.getPermanentCallCounter().total());
        result.put("totalBackgroundCalls", registry.getPermanentBackgroundCallCounter().total());
        result.put("totalErrors", totalErrors);
        result.put("totalTimeMs", round(totalTime));
        result.put("dbTimeMs", round(dbTime));
        result.put("cpuTimeMs", round(Math.max(totalTime - dbTime, 0)));
        result.put("memoryKb", round(memory));
        result.put("topCpuBottleneck", topCpu);
        result.put("topDatabaseBottleneck", topIo);
        result.put("topUnknownBottleneck", topOther);
        result.put("topBottlenecks", topBottlenecks);
        result.put("aiEnabled", aiAnalysisService != null && aiAnalysisService.isEnabled());
        return result;
    }

    private Map<String, Object> topBottleneckByType(String type, java.util.Set<String> fragmentedKeys) {
        return registry.getEndpoints().stream()
                .filter(ep -> type.equals(ep.bottleneckType()))
                .max(Comparator.comparingDouble(EndpointStats::totalTimeMs))
                .map(ep -> endpointSummary(ep, fragmentedKeys))
                .orElse(null);
    }

//    @GetMapping(value = "/monitor/api/endpoints", produces = MediaType.APPLICATION_JSON_VALUE)
//    public List<Map<String, Object>> endpoints() {
//        return registry.getEndpoints().stream()
//                .sorted(Comparator.comparingDouble(EndpointStats::totalTimeMs).reversed())
//                .limit(TOP_N)
//                .map(this::endpointSummary)
//                .collect(Collectors.toList());
//    }


    @GetMapping(value = "/monitor/api/endpoints", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> endpoints(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "totalTimeMs") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir,
            @RequestParam(required = false, defaultValue = "10") int limit) {

        java.util.Set<String> fragmentedKeys = fragmentedEndpointKeys();
        Comparator<EndpointStats> comparator = endpointComparator(sortBy);
        if (!"asc".equalsIgnoreCase(sortDir)) comparator = comparator.reversed();
        int cappedLimit = Math.min(Math.max(limit, 1), 200);
        String needle = (search == null) ? null : search.toLowerCase();

        return registry.getEndpoints().stream()
                .filter(ep -> needle == null || needle.isBlank() || ep.getEndpointKey().toLowerCase().contains(needle))
                .sorted(comparator)
                .limit(cappedLimit)
                .map(ep -> endpointSummary(ep, fragmentedKeys))
                .collect(Collectors.toList());
    }

    private java.util.Set<String> fragmentedEndpointKeys() {
        Map<String, java.util.Set<String>> byTrace = new java.util.HashMap<>();
        for (com.issa.smartmonitor.model.RequestTrace t : registry.getRecentTraces()) {
            if (t.getTraceId() == null) continue;
            byTrace.computeIfAbsent(t.getTraceId(), k -> new java.util.HashSet<>()).add(t.getEndpointKey());
        }
        java.util.Set<String> fragmented = new java.util.HashSet<>();
        for (java.util.Set<String> keys : byTrace.values()) {
            if (keys.size() > 1) fragmented.addAll(keys);
        }
        return fragmented;
    }

    private Comparator<EndpointStats> endpointComparator(String sortBy) {
        return switch (sortBy) {
            case "callCount" -> Comparator.comparingLong(ep -> ep.getCallCount().sum());
            case "errorCount" -> Comparator.comparingLong(ep -> ep.getErrorCount().sum());
            case "avgTimeMs" -> Comparator.comparingDouble(EndpointStats::avgTimeMs);
            case "maxTimeMs" -> Comparator.comparingDouble(EndpointStats::maxTimeMs);
            case "p95TimeMs" -> Comparator.comparingDouble(EndpointStats::p95TimeMs);
            case "p99TimeMs" -> Comparator.comparingDouble(EndpointStats::p99TimeMs);
            case "dbTimeMs" -> Comparator.comparingDouble(EndpointStats::dbTimeMs);
            case "memoryKb" -> Comparator.comparingDouble(EndpointStats::memoryKb);
            default -> Comparator.comparingDouble(EndpointStats::totalTimeMs);
        };
    }

//    @GetMapping(value = "/monitor/api/endpoint-methods", produces = MediaType.APPLICATION_JSON_VALUE)
//    public List<Map<String, Object>> endpointMethods(@RequestParam String key) {
//        EndpointStats ep = registry.getEndpoint(key);
//        if (ep == null) return List.of();
//        long endpointCalls = ep.getCallCount().sum();
//
//        return ep.getMethods().stream()
//                .sorted(Comparator.comparingDouble(MethodStats::selfTimeMs).reversed())
//                .limit(TOP_N)
//                .map(s -> {
//                    long methodCalls = s.getCallCount().sum();
//                    double callRatio = endpointCalls == 0 ? 0 : (double) methodCalls / endpointCalls;
//
//                    Map<String, Object> m = new LinkedHashMap<>();
//                    m.put("className", s.getClassName());
//                    m.put("methodName", s.getMethodName());
//                    m.put("layer", s.getLayer());
//                    m.put("callCount", methodCalls);
//                    m.put("errorCount", s.getErrorCount().sum());
//                    m.put("selfTimeMs", round(s.selfTimeMs()));
//                    m.put("totalTimeMs", round(s.totalTimeMs()));
//                    m.put("p95TimeMs", round(s.p95TimeMs()));
//                    m.put("p99TimeMs", round(s.p99TimeMs()));
//                    m.put("dbTimeMs", round(s.dbTimeMs()));
//                    m.put("memoryKb", round(s.totalMemoryKb()));
//                    m.put("bottleneckType", s.bottleneckType());
//                    m.put("callsPerRequest", round(callRatio));
//                    m.put("n1Warning", callRatio >= N1_RATIO_THRESHOLD);
//                    return m;
//                })
//                .collect(Collectors.toList());
//    }


    @GetMapping(value = "/monitor/api/endpoint-methods", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> endpointMethods(
            @RequestParam String key,
            @RequestParam(required = false, defaultValue = "selfTimeMs") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        EndpointStats ep = registry.getEndpoint(key);
        if (ep == null) return List.of();

        long endpointCalls = ep.getCallCount().sum();
        Comparator<MethodStats> comparator = methodComparator(sortBy);
        if (!"asc".equalsIgnoreCase(sortDir)) comparator = comparator.reversed();
        int cappedLimit = Math.min(Math.max(limit, 1), 200);

        return ep.getMethods().stream()
                .sorted(comparator)
                .limit(cappedLimit)
                .map(s -> {
                    long methodCalls = s.getCallCount().sum();
                    double callRatio = endpointCalls == 0 ? 0 : (double) methodCalls / endpointCalls;

                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("className", s.getClassName());
                    m.put("methodName", s.getMethodName());
                    m.put("layer", s.getLayer());
                    m.put("callCount", methodCalls);
                    m.put("errorCount", s.getErrorCount().sum());
                    m.put("selfTimeMs", round(s.selfTimeMs()));
                    m.put("totalTimeMs", round(s.totalTimeMs()));
                    m.put("p95TimeMs", round(s.p95TimeMs()));
                    m.put("p99TimeMs", round(s.p99TimeMs()));
                    m.put("dbTimeMs", round(s.dbTimeMs()));
                    m.put("memoryKb", round(s.totalMemoryKb()));
                    m.put("bottleneckType", s.bottleneckType());
                    m.put("callsPerRequest", round(callRatio));
                    m.put("n1Warning", callRatio >= N1_RATIO_THRESHOLD);
                    return m;
                })
                .collect(Collectors.toList());
    }

    private Comparator<MethodStats> methodComparator(String sortBy) {
        return switch (sortBy) {
            case "callCount" -> Comparator.comparingLong(s -> s.getCallCount().sum());
            case "errorCount" -> Comparator.comparingLong(s -> s.getErrorCount().sum());
            case "totalTimeMs" -> Comparator.comparingDouble(MethodStats::totalTimeMs);
            case "p95TimeMs" -> Comparator.comparingDouble(MethodStats::p95TimeMs);
            case "p99TimeMs" -> Comparator.comparingDouble(MethodStats::p99TimeMs);
            case "dbTimeMs" -> Comparator.comparingDouble(MethodStats::dbTimeMs);
            case "memoryKb" -> Comparator.comparingDouble(MethodStats::totalMemoryKb);
            default -> Comparator.comparingDouble(MethodStats::selfTimeMs);
        };
    }


    @PostMapping(value = "/monitor/api/ai-analysis", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> aiAnalysis(@RequestParam(defaultValue = "false") boolean refresh) {
        if (aiAnalysisService == null || !aiAnalysisService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "AI analysis is not enabled on this project."));
        }
        try {
            AiAnalysisResult result = aiAnalysisService.analyze(refresh);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("generatedAtMillis", result.getGeneratedAtMillis());
            body.put("rawJson", result.getRawJson());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> endpointSummary(EndpointStats ep, java.util.Set<String> fragmentedKeys) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", ep.getEndpointKey());
        m.put("callCount", ep.getCallCount().sum());
        m.put("errorCount", ep.getErrorCount().sum());
        m.put("avgTimeMs", round(ep.avgTimeMs()));
        m.put("maxTimeMs", round(ep.maxTimeMs()));
        m.put("p95TimeMs", round(ep.p95TimeMs()));
        m.put("p99TimeMs", round(ep.p99TimeMs()));
        m.put("totalTimeMs", round(ep.totalTimeMs()));
        m.put("dbTimeMs", round(ep.dbTimeMs()));
        m.put("memoryKb", round(ep.memoryKb()));
        m.put("possibleFragment", fragmentedKeys.contains(ep.getEndpointKey()));
        return m;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    @GetMapping(value = "/monitor/api/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> history() {
        return history.getSnapshots().stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("time", s.getTimestampMillis());
                    m.put("cpuTimeMsPerSec", s.getCpuTimeMsPerSec());
                    m.put("dbTimeMsPerSec", s.getDbTimeMsPerSec());
                    m.put("errorCount", s.getErrorCount());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/monitor/api/errors", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> errors() {
        return registry.getRecentErrors().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("time", e.getTimestampMillis());
                    m.put("traceId", e.getTraceId());
                    m.put("endpoint", shortLabelStatic(e.getEndpointKey()));
                    m.put("method", e.getClassName() + "." + e.getMethodName());
                    m.put("exceptionType", e.getExceptionType());
                    m.put("message", e.getMessage());
                    return m;
                })
                .collect(Collectors.toList());
    }

    private String shortLabelStatic(String key) {
        return key == null ? "-" : key.replace("#", ".");
    }

    @GetMapping(value = "/monitor/api/top-called", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> topCalled() {
        return registry.getPermanentCallCounter().topByCallCount(20).stream()
                .map(entry -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("key", entry.getKey());
                    m.put("callCount", entry.getValue());
                    return m;
                })
                .collect(Collectors.toList());
    }


    @GetMapping(value = "/monitor/api/traces", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> traces(@RequestParam(required = false) String traceId) {
        return registry.getRecentTraces().stream()
                .filter(t -> traceId == null || traceId.isBlank() || t.getTraceId().equals(traceId))
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("time", t.getTimestampMillis());
                    m.put("traceId", t.getTraceId());
                    m.put("endpoint", shortLabelStatic(t.getEndpointKey()));
                    m.put("durationMs", round(t.getDurationMs()));
                    m.put("error", t.isError());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/monitor/api/call-graph", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> callGraph() {
        return registry.getCallGraph();
    }

    @GetMapping(value = "/monitor/api/background-roots", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> backgroundRoots() {
        java.util.Set<String> fragmentedKeys = fragmentedEndpointKeys();
        return registry.getBackgroundRoots().stream()
                .sorted(Comparator.comparingDouble(EndpointStats::totalTimeMs).reversed())
                .limit(TOP_N)
                .map(ep -> endpointSummary(ep, fragmentedKeys))
                .collect(Collectors.toList());
    }
}