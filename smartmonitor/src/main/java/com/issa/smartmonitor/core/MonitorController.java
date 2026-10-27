package com.issa.smartmonitor.core;

import com.issa.smartmonitor.ai.DiagnosticAnalyzer;
import com.issa.smartmonitor.config.SmartMonitorProperties;
import com.issa.smartmonitor.enums.BottleneckType;
import com.issa.smartmonitor.model.EndpointStats;
import com.issa.smartmonitor.model.MethodStats;
import com.issa.smartmonitor.web.ApiView;
import com.issa.smartmonitor.web.ViewCatalog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class MonitorController {

    private final MetricRegistry registry;
    private final DiagnosticAnalyzer diagnosticAnalyzer;
    private final MetricHistory history;
    private final SmartMonitorProperties properties;
    private final ViewCatalog views;

    public MonitorController(MetricRegistry registry,
                             ObjectProvider<DiagnosticAnalyzer> diagnosticAnalyzer,
                             MetricHistory history,
                             SmartMonitorProperties properties,
                             ViewCatalog views) {
        this.registry = registry;
        this.diagnosticAnalyzer = diagnosticAnalyzer.getIfAvailable();
        this.history = history;
        this.properties = properties;
        this.views = views;
    }

    @GetMapping(value = "${smartmonitor.paths.overview}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiView> overview() {
        long totalErrors = 0;
        double totalTime = 0;
        double dbTime = 0;
        double memory = 0;
        for (EndpointStats endpoint : registry.getEndpoints()) {
            totalErrors += endpoint.getErrorCount().sum();
            totalTime += endpoint.totalTimeMs();
            dbTime += endpoint.dbTimeMs();
            memory += endpoint.memoryKb();
        }

        java.util.Set<String> fragmented = fragmentedEndpointKeys();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalCalls", registry.getPermanentCallCounter().total());
        data.put("totalBackgroundCalls", registry.getPermanentBackgroundCallCounter().total());
        data.put("totalErrors", totalErrors);
        data.put("totalTimeMs", round(totalTime));
        data.put("dbTimeMs", round(dbTime));
        data.put("cpuTimeMs", round(Math.max(totalTime - dbTime, 0)));
        data.put("memoryKb", round(memory));
        data.put("topCpuBottleneck", topBottleneck(BottleneckType.CPU.name(), fragmented));
        data.put("topDatabaseBottleneck", topBottleneck(BottleneckType.DATABASE.name(), fragmented));
        data.put("topUnknownBottleneck", topBottleneck(BottleneckType.UNKNOWN.name(), fragmented));
        data.put("topBottlenecks", registry.getEndpoints().stream()
                .sorted(Comparator.comparingDouble(EndpointStats::totalTimeMs).reversed())
                .limit(properties.getLimits().getOverviewTop())
                .map(endpoint -> endpointSummary(endpoint, fragmented))
                .collect(Collectors.toList()));
        data.put("aiEnabled", analyzeEnabled());
        return ok(properties.getViews().getOverview(), data, Map.of());
    }

    @GetMapping(value = "${smartmonitor.paths.endpoints}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiView> endpoints(HttpServletRequest request) {
        java.util.Set<String> fragmented = fragmentedEndpointKeys();
        String sortBy = param(request, properties.getParams().getSortBy(), properties.getSort().getEndpointsBy());
        boolean ascending = properties.getSort().getAscending().equalsIgnoreCase(param(request, properties.getParams().getSortDir(), properties.getSort().getEndpointsDir()));
        Comparator<EndpointStats> comparator = endpointComparator(sortBy);
        if (!ascending) {
            comparator = comparator.reversed();
        }
        String needle = param(request, properties.getParams().getSearch(), null);
        String lowered = needle == null ? null : needle.toLowerCase();

        List<Map<String, Object>> rows = registry.getEndpoints().stream()
                .filter(endpoint -> lowered == null || lowered.isBlank() || endpoint.getEndpointKey().toLowerCase().contains(lowered))
                .sorted(comparator)
                .limit(pageSize(request, properties.getLimits().getDefaultPageSize()))
                .map(endpoint -> endpointSummary(endpoint, fragmented))
                .collect(Collectors.toList());
        return ok(properties.getViews().getEndpoints(), rows, sortMeta(sortBy, ascending));
    }

    @GetMapping(value = "${smartmonitor.paths.endpoint-methods}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiView> endpointMethods(HttpServletRequest request) {
        String key = param(request, properties.getParams().getKey(), null);
        EndpointStats endpoint = key == null ? null : registry.getEndpoint(key);
        if (endpoint == null) {
            return ok(properties.getViews().getEndpointMethods(), List.of(), Map.of());
        }
        long endpointCalls = endpoint.getCallCount().sum();
        String sortBy = param(request, properties.getParams().getSortBy(), properties.getSort().getMethodsBy());
        boolean ascending = properties.getSort().getAscending().equalsIgnoreCase(param(request, properties.getParams().getSortDir(), properties.getSort().getMethodsDir()));
        Comparator<MethodStats> comparator = methodComparator(sortBy);
        if (!ascending) {
            comparator = comparator.reversed();
        }
        double n1At = properties.getThresholds().getN1CallsPerRequest();
        List<Map<String, Object>> rows = endpoint.getMethods().stream()
                .sorted(comparator)
                .limit(pageSize(request, properties.getLimits().getDefaultPageSize()))
                .map(method -> methodSummary(method, endpointCalls, n1At))
                .collect(Collectors.toList());
        return ok(properties.getViews().getEndpointMethods(), rows, sortMeta(sortBy, ascending));
    }

    @PostMapping(value = "${smartmonitor.paths.analyze}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiView> analyze(HttpServletRequest request) {
        String view = properties.getViews().getAnalyze();
        if (!analyzeEnabled()) {
            return status(properties.getHttp().getUnavailable(), fail(view, properties.getCodes().getDisabled(), properties.getMessages().getAiDisabled()));
        }
        boolean refresh = Boolean.parseBoolean(param(request, properties.getParams().getRefresh(), Boolean.FALSE.toString()));
        try {
            return ok(view, diagnosticAnalyzer.analyze(refresh), Map.of());
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? properties.getMessages().getAiFailed() : ex.getMessage();
            return status(properties.getHttp().getError(), fail(view, properties.getCodes().getFailed(), message));
        }
    }

    @GetMapping(value = "${smartmonitor.paths.history}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiView> history() {
        List<Map<String, Object>> rows = history.getSnapshots().stream()
                .map(snapshot -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("time", snapshot.getTimestampMillis());
                    row.put("cpuTimeMsPerSec", snapshot.getCpuTimeMsPerSec());
                    row.put("dbTimeMsPerSec", snapshot.getDbTimeMsPerSec());
                    row.put("errorCount", snapshot.getErrorCount());
                    return row;
                })
                .collect(Collectors.toList());
        return ok(properties.getViews().getHistory(), rows, Map.of());
    }

    @GetMapping(value = "${smartmonitor.paths.errors}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiView> errors() {
        List<Map<String, Object>> rows = registry.getRecentErrors().stream()
                .map(error -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("time", error.getTimestampMillis());
                    row.put("traceId", error.getTraceId());
                    row.put("endpoint", display(error.getEndpointKey()));
                    row.put("method", display(registry.getTuning().methodKey(error.getClassName(), error.getMethodName())));
                    row.put("exceptionType", error.getExceptionType());
                    row.put("message", error.getMessage());
                    return row;
                })
                .collect(Collectors.toList());
        return ok(properties.getViews().getErrors(), rows, Map.of());
    }

    @GetMapping(value = "${smartmonitor.paths.top-called}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiView> topCalled() {
        List<Map<String, Object>> rows = registry.getPermanentCallCounter().topByCallCount(properties.getLimits().getTopCalled()).stream()
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("key", entry.getKey());
                    row.put("callCount", entry.getValue());
                    return row;
                })
                .collect(Collectors.toList());
        return ok(properties.getViews().getTopCalled(), rows, Map.of());
    }

    @GetMapping(value = "${smartmonitor.paths.traces}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiView> traces(HttpServletRequest request) {
        String traceId = param(request, properties.getParams().getTraceId(), null);
        List<Map<String, Object>> rows = registry.getRecentTraces().stream()
                .filter(trace -> traceId == null || traceId.isBlank() || traceId.equals(trace.getTraceId()))
                .map(trace -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("time", trace.getTimestampMillis());
                    row.put("traceId", trace.getTraceId());
                    row.put("endpoint", display(trace.getEndpointKey()));
                    row.put("durationMs", round(trace.getDurationMs()));
                    row.put("error", trace.isError());
                    return row;
                })
                .collect(Collectors.toList());
        return ok(properties.getViews().getTraces(), rows, Map.of());
    }

    @GetMapping(value = "${smartmonitor.paths.call-graph}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiView> callGraph() {
        return ok(properties.getViews().getCallGraph(), registry.getCallGraph(), Map.of());
    }

    @GetMapping(value = "${smartmonitor.paths.background-roots}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiView> backgroundRoots() {
        java.util.Set<String> fragmented = fragmentedEndpointKeys();
        List<Map<String, Object>> rows = registry.getBackgroundRoots().stream()
                .sorted(Comparator.comparingDouble(EndpointStats::totalTimeMs).reversed())
                .limit(properties.getLimits().getTopN())
                .map(endpoint -> endpointSummary(endpoint, fragmented))
                .collect(Collectors.toList());
        return ok(properties.getViews().getBackgroundRoots(), rows, Map.of());
    }

    private boolean analyzeEnabled() {
        return diagnosticAnalyzer != null && diagnosticAnalyzer.isEnabled();
    }

    private Map<String, Object> topBottleneck(String type, java.util.Set<String> fragmented) {
        return registry.getEndpoints().stream()
                .filter(endpoint -> type.equals(endpoint.bottleneckType()))
                .max(Comparator.comparingDouble(EndpointStats::totalTimeMs))
                .map(endpoint -> endpointSummary(endpoint, fragmented))
                .orElse(null);
    }

    private java.util.Set<String> fragmentedEndpointKeys() {
        Map<String, java.util.Set<String>> byTrace = new java.util.HashMap<>();
        for (com.issa.smartmonitor.model.RequestTrace trace : registry.getRecentTraces()) {
            if (trace.getTraceId() == null) {
                continue;
            }
            byTrace.computeIfAbsent(trace.getTraceId(), key -> new java.util.HashSet<>()).add(trace.getEndpointKey());
        }
        java.util.Set<String> fragmented = new java.util.HashSet<>();
        int minimum = properties.getLimits().getFragmentMinDistinctKeys();
        for (java.util.Set<String> keys : byTrace.values()) {
            if (keys.size() > minimum) {
                fragmented.addAll(keys);
            }
        }
        return fragmented;
    }

    private Comparator<EndpointStats> endpointComparator(String sortBy) {
        return switch (sortBy) {
            case "callCount" -> Comparator.comparingLong(endpoint -> endpoint.getCallCount().sum());
            case "errorCount" -> Comparator.comparingLong(endpoint -> endpoint.getErrorCount().sum());
            case "avgTimeMs" -> Comparator.comparingDouble(EndpointStats::avgTimeMs);
            case "maxTimeMs" -> Comparator.comparingDouble(EndpointStats::maxTimeMs);
            case "p95TimeMs" -> Comparator.comparingDouble(EndpointStats::p95TimeMs);
            case "p99TimeMs" -> Comparator.comparingDouble(EndpointStats::p99TimeMs);
            case "dbTimeMs" -> Comparator.comparingDouble(EndpointStats::dbTimeMs);
            case "memoryKb" -> Comparator.comparingDouble(EndpointStats::memoryKb);
            default -> Comparator.comparingDouble(EndpointStats::totalTimeMs);
        };
    }

    private Comparator<MethodStats> methodComparator(String sortBy) {
        return switch (sortBy) {
            case "callCount" -> Comparator.comparingLong(method -> method.getCallCount().sum());
            case "errorCount" -> Comparator.comparingLong(method -> method.getErrorCount().sum());
            case "totalTimeMs" -> Comparator.comparingDouble(MethodStats::totalTimeMs);
            case "p95TimeMs" -> Comparator.comparingDouble(MethodStats::p95TimeMs);
            case "p99TimeMs" -> Comparator.comparingDouble(MethodStats::p99TimeMs);
            case "dbTimeMs" -> Comparator.comparingDouble(MethodStats::dbTimeMs);
            case "memoryKb" -> Comparator.comparingDouble(MethodStats::totalMemoryKb);
            case "className" -> Comparator.comparing(MethodStats::getClassName, Comparator.nullsLast(String::compareTo));
            default -> Comparator.comparingDouble(MethodStats::selfTimeMs);
        };
    }

    private Map<String, Object> endpointSummary(EndpointStats endpoint, java.util.Set<String> fragmented) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", endpoint.getEndpointKey());
        row.put("callCount", endpoint.getCallCount().sum());
        row.put("errorCount", endpoint.getErrorCount().sum());
        row.put("avgTimeMs", round(endpoint.avgTimeMs()));
        row.put("maxTimeMs", round(endpoint.maxTimeMs()));
        row.put("p95TimeMs", round(endpoint.p95TimeMs()));
        row.put("p99TimeMs", round(endpoint.p99TimeMs()));
        row.put("totalTimeMs", round(endpoint.totalTimeMs()));
        row.put("dbTimeMs", round(endpoint.dbTimeMs()));
        row.put("memoryKb", round(endpoint.memoryKb()));
        row.put("possibleFragment", fragmented.contains(endpoint.getEndpointKey()));
        return row;
    }

    private Map<String, Object> methodSummary(MethodStats method, long endpointCalls, double n1At) {
        long methodCalls = method.getCallCount().sum();
        double callRatio = endpointCalls == 0 ? 0 : (double) methodCalls / endpointCalls;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("className", method.getClassName());
        row.put("methodName", method.getMethodName());
        row.put("layer", method.getLayer());
        row.put("callCount", methodCalls);
        row.put("errorCount", method.getErrorCount().sum());
        row.put("selfTimeMs", round(method.selfTimeMs()));
        row.put("totalTimeMs", round(method.totalTimeMs()));
        row.put("p95TimeMs", round(method.p95TimeMs()));
        row.put("p99TimeMs", round(method.p99TimeMs()));
        row.put("dbTimeMs", round(method.dbTimeMs()));
        row.put("memoryKb", round(method.totalMemoryKb()));
        row.put("bottleneckType", method.bottleneckType());
        row.put("callsPerRequest", round(callRatio));
        row.put("n1Warning", callRatio >= n1At);
        return row;
    }

    private Map<String, Object> sortMeta(String sortBy, boolean ascending) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put(properties.getParams().getSortBy(), sortBy);
        meta.put(properties.getParams().getSortDir(), ascending ? properties.getSort().getAscending() : properties.getSort().getDescending());
        return meta;
    }

    private ResponseEntity<ApiView> ok(String view, Object data, Map<String, Object> meta) {
        return status(properties.getHttp().getOk(), ApiView.of(true, properties.getCodes().getOk(), view, null, meta, views.schema(view), data));
    }

    private ApiView fail(String view, String code, String message) {
        return ApiView.of(false, code, view, message, Map.of(), views.schema(view), null);
    }

    private ResponseEntity<ApiView> status(int status, ApiView view) {
        return ResponseEntity.status(status).body(view);
    }

    private int pageSize(HttpServletRequest request, int fallback) {
        String raw = request.getParameter(properties.getParams().getLimit());
        int parsed = fallback;
        if (raw != null && !raw.isBlank()) {
            try {
                parsed = Integer.parseInt(raw);
            } catch (NumberFormatException ignored) {
                parsed = fallback;
            }
        }
        return Math.min(Math.max(parsed, properties.getLimits().getMinPageSize()), properties.getLimits().getMaxPageSize());
    }

    private String param(HttpServletRequest request, String name, String fallback) {
        String value = request.getParameter(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private double round(double value) {
        return registry.getTuning().round(value);
    }

    private String display(String key) {
        return registry.getTuning().display(key);
    }
}
