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
    private final AiAnalysisService aiAnalysisService; // may be null if AI is disabled
    private final MetricHistory history;


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

        Map<String, Object> topCpu = topBottleneckByType(BottleneckType.CPU.name());
        Map<String, Object> topIo = topBottleneckByType(BottleneckType.DATABASE.name());
        Map<String, Object> topOther = topBottleneckByType(BottleneckType.UNKNOWN.name());

        List<Map<String, Object>> topBottlenecks = registry.getEndpoints().stream()
                .sorted(Comparator.comparingDouble(EndpointStats::totalTimeMs).reversed())
                .limit(5)
                .map(this::endpointSummary)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCalls", totalCalls);
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

    private Map<String, Object> topBottleneckByType(String type) {
        return registry.getEndpoints().stream()
                .filter(ep -> type.equals(ep.bottleneckType()))
                .max(Comparator.comparingDouble(EndpointStats::totalTimeMs))
                .map(this::endpointSummary)
                .orElse(null);
    }

    @GetMapping(value = "/monitor/api/endpoints", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> endpoints() {
        return registry.getEndpoints().stream()
                .sorted(Comparator.comparingDouble(EndpointStats::totalTimeMs).reversed())
                .limit(TOP_N)
                .map(this::endpointSummary)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/monitor/api/endpoint-methods", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> endpointMethods(@RequestParam String key) {
        EndpointStats ep = registry.getEndpoint(key);
        if (ep == null) return List.of();

        return ep.getMethods().stream()
                .sorted(Comparator.comparingDouble(MethodStats::selfTimeMs).reversed())
                .limit(TOP_N)
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("className", s.getClassName());
                    m.put("methodName", s.getMethodName());
                    m.put("layer", s.getLayer());
                    m.put("callCount", s.getCallCount().sum());
                    m.put("errorCount", s.getErrorCount().sum());
                    m.put("selfTimeMs", round(s.selfTimeMs()));
                    m.put("totalTimeMs", round(s.totalTimeMs()));
                    m.put("dbTimeMs", round(s.dbTimeMs()));
                    m.put("memoryKb", round(s.totalMemoryKb()));
                    m.put("bottleneckType", s.bottleneckType());
                    return m;
                })
                .collect(Collectors.toList());
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

    private Map<String, Object> endpointSummary(EndpointStats ep) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", ep.getEndpointKey());
        m.put("callCount", ep.getCallCount().sum());
        m.put("errorCount", ep.getErrorCount().sum());
        m.put("avgTimeMs", round(ep.avgTimeMs()));
        m.put("maxTimeMs", round(ep.maxTimeMs()));
        m.put("totalTimeMs", round(ep.totalTimeMs()));
        m.put("dbTimeMs", round(ep.dbTimeMs()));
        m.put("memoryKb", round(ep.memoryKb()));
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
}