package com.issa.smartmonitor.ai;

import com.issa.smartmonitor.config.SmartMonitorProperties;
import com.issa.smartmonitor.core.MetricRegistry;
import com.issa.smartmonitor.core.MonitorTuning;
import com.issa.smartmonitor.model.EndpointStats;
import com.issa.smartmonitor.model.MethodStats;
import com.issa.smartmonitor.model.RequestTrace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the live window into one short report plus a fix. Inference is the local model.
 */
public class DiagnosticAnalyzer {

    static final String LOCATE_CALLS = "maxCallsPerRequest";
    static final String LOCATE_DB = "maxDbRepository";
    static final String LOCATE_SELF = "maxSelf";
    static final String LOCATE_ERRORS = "maxErrors";
    static final String LOCATE_MEMORY = "maxMemory";

    private final MetricRegistry registry;
    private final SmartMonitorProperties properties;
    private final DiagnosticModel model;
    private final Object cacheLock = new Object();
    private Map<String, Object> cached;
    private long cachedAt;

    public DiagnosticAnalyzer(MetricRegistry registry, SmartMonitorProperties properties, DiagnosticModel model) {
        this.registry = registry;
        this.properties = properties;
        this.model = model;
    }

    public boolean isEnabled() {
        return properties.getAi().isEnabled() && model != null;
    }

    public Map<String, Object> analyze(boolean forceRefresh) {
        if (!isEnabled()) {
            throw new AiAnalysisException(properties.getMessages().getAiDisabled(), null);
        }
        long cacheMillis = properties.getAi().getCacheSeconds() * (long) properties.getUnits().getMillisPerSecond();
        synchronized (cacheLock) {
            if (!forceRefresh && cached != null && System.currentTimeMillis() - cachedAt < cacheMillis) {
                return cached;
            }
            long started = System.nanoTime();
            Map<String, Object> report = buildReport();
            double inferenceMs = registry.getTuning().ms(System.nanoTime() - started);
            report.put("footer", model.footer(footerSlots(registry.getTuning().round(inferenceMs))));
            cached = report;
            cachedAt = System.currentTimeMillis();
            return report;
        }
    }

    private Map<String, Object> buildReport() {
        MonitorTuning tuning = registry.getTuning();
        List<EndpointStats> endpoints = registry.getEndpoints().stream()
                .sorted(Comparator.comparingDouble(EndpointStats::totalTimeMs).reversed())
                .limit(properties.getLimits().getOverviewTop())
                .toList();

        if (endpoints.isEmpty()) {
            return emptyReport();
        }

        Set<String> fragmented = fragmentedKeys();
        List<Scored> scored = new ArrayList<>();
        for (EndpointStats endpoint : endpoints) {
            Observation observation = observe(endpoint, fragmented.contains(endpoint.getEndpointKey()), tuning);
            DiagnosticModel.Prediction prediction = model.predict(observation.raw());
            if (properties.getAi().getHealthyLabel().equals(prediction.label()) || prediction.confidence() < model.minConfidence()) {
                continue;
            }
            double score = prediction.confidence() * model.weight(prediction.label()) * Math.log1p(observation.totalMs());
            scored.add(new Scored(observation, prediction, score));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());

        int maxFindings = Math.max(properties.getAi().getMaxFindings(), 1);
        if (scored.size() > maxFindings) {
            scored = scored.subList(0, maxFindings);
        }

        if (scored.isEmpty()) {
            return healthyReport(observe(endpoints.get(0), fragmented.contains(endpoints.get(0).getEndpointKey()), tuning), tuning);
        }

        List<Map<String, Object>> findings = new ArrayList<>();
        for (Scored item : scored) {
            findings.add(toFinding(item, tuning));
        }
        Map<String, Object> headline = findings.get(0);
        Map<String, Object> report = new LinkedHashMap<>(headline);
        report.put("findings", findings.size() == 1 ? List.of() : findings.subList(1, findings.size()));
        return report;
    }

    private Map<String, Object> toFinding(Scored item, MonitorTuning tuning) {
        String where = locate(item.observation(), model.locate(item.prediction().label()), tuning);
        Map<String, String> slots = slots(item.observation(), where, tuning);
        Map<String, Object> finding = model.finding(item.prediction().label(), slots);
        finding.put("where", where);
        finding.put("confidence", tuning.round(item.prediction().confidence()));
        return finding;
    }

    private Map<String, Object> healthyReport(Observation observation, MonitorTuning tuning) {
        String where = tuning.display(observation.endpointKey());
        Map<String, Object> finding = model.finding(properties.getAi().getHealthyLabel(), slots(observation, where, tuning));
        finding.put("where", where);
        finding.put("confidence", tuning.round(1));
        finding.put("findings", List.of());
        return finding;
    }

    private Map<String, Object> emptyReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("where", properties.getFormat().getEmptyLabel());
        String label = properties.getAi().getHealthyLabel();
        Map<String, Object> healthy = model.finding(label, Map.of("where", properties.getFormat().getEmptyLabel()));
        report.put("problem", healthy.get("problem"));
        report.put("summary", properties.getMessages().getNoSamples());
        report.put("suggestion", healthy.get("suggestion"));
        report.put("confidence", properties.getAi().getEmptyConfidence());
        report.put("severity", healthy.get("severity"));
        report.put("evidence", List.of());
        report.put("findings", List.of());
        return report;
    }

    private Map<String, String> slots(Observation observation, String where, MonitorTuning tuning) {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("where", where);
        slots.put("callsPerRequest", number(observation.callsPerRequest(), tuning));
        double percent = properties.getUnits().getPercentScale();
        slots.put("dbPercent", number(observation.dbRatio() * percent, tuning));
        slots.put("avgMs", number(observation.avgMs(), tuning));
        slots.put("p99Ms", number(observation.p99Ms(), tuning));
        slots.put("errorPercent", number(observation.errorRate() * percent, tuning));
        slots.put("memoryKb", number(observation.memoryKb(), tuning));
        slots.put("selfPercent", number(observation.selfRatio() * percent, tuning));
        return slots;
    }

    private Map<String, String> footerSlots(double inferenceMs) {
        MonitorTuning tuning = registry.getTuning();
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("modelId", model.id());
        slots.put("modelVersion", model.version());
        slots.put("inferenceMs", number(inferenceMs, tuning));
        slots.put("holdoutAccuracy", number(model.holdoutAccuracy() * properties.getUnits().getPercentScale(), tuning) + "%");
        return slots;
    }

    private String number(double value, MonitorTuning tuning) {
        return Double.toString(tuning.round(value));
    }

    private String locate(Observation observation, String strategy, MonitorTuning tuning) {
        MethodStats method = switch (strategy) {
            case LOCATE_CALLS -> observation.hottestCalls();
            case LOCATE_DB -> observation.hottestDb();
            case LOCATE_SELF -> observation.hottestSelf();
            case LOCATE_ERRORS -> observation.hottestErrors();
            case LOCATE_MEMORY -> observation.hottestMemory();
            default -> null;
        };
        if (method == null) {
            return tuning.display(observation.endpointKey());
        }
        return tuning.display(tuning.methodKey(method.getClassName(), method.getMethodName()));
    }

    private Observation observe(EndpointStats endpoint, boolean fragmented, MonitorTuning tuning) {
        long calls = endpoint.getCallCount().sum();
        long errors = endpoint.getErrorCount().sum();
        double totalMs = endpoint.totalTimeMs();
        double dbRatio = totalMs == 0 ? 0 : endpoint.dbTimeMs() / totalMs;
        double errorRate = calls == 0 ? 0 : (double) errors / calls;

        MethodStats hottestCalls = null;
        MethodStats hottestSelf = null;
        MethodStats hottestDb = null;
        MethodStats hottestErrors = null;
        MethodStats hottestMemory = null;
        double methodTime = 0;
        double repositoryTime = 0;

        for (MethodStats method : endpoint.getMethods()) {
            if (hottestCalls == null || method.getCallCount().sum() > hottestCalls.getCallCount().sum()) {
                hottestCalls = method;
            }
            if (hottestSelf == null || method.selfTimeMs() > hottestSelf.selfTimeMs()) {
                hottestSelf = method;
            }
            if (hottestDb == null || method.dbTimeMs() > hottestDb.dbTimeMs()) {
                hottestDb = method;
            }
            if (hottestErrors == null || method.getErrorCount().sum() > hottestErrors.getErrorCount().sum()) {
                hottestErrors = method;
            }
            if (hottestMemory == null || method.totalMemoryKb() > hottestMemory.totalMemoryKb()) {
                hottestMemory = method;
            }
            methodTime += method.totalTimeMs();
            if (tuning.repositoryLayer().equals(method.getLayer())) {
                repositoryTime += method.totalTimeMs();
            }
        }

        MethodStats repository = null;
        for (MethodStats method : endpoint.getMethods()) {
            if (!tuning.repositoryLayer().equals(method.getLayer())) {
                continue;
            }
            if (repository == null || method.dbTimeMs() > repository.dbTimeMs()) {
                repository = method;
            }
        }
        if (repository != null) {
            hottestDb = repository;
        }

        double callsPerRequest = 0;
        if (hottestCalls != null && calls > 0) {
            callsPerRequest = hottestCalls.getCallCount().sum() / (double) calls;
        }
        double selfRatio = totalMs == 0 || hottestSelf == null ? 0 : hottestSelf.selfTimeMs() / totalMs;
        double repositoryShare = methodTime == 0 ? dbRatio : repositoryTime / methodTime;

        Map<String, Double> raw = new HashMap<>();
        raw.put("calls", (double) calls);
        raw.put("avg_ms", endpoint.avgTimeMs());
        raw.put("p95_ms", endpoint.p95TimeMs());
        raw.put("p99_ms", endpoint.p99TimeMs());
        raw.put("max_ms", endpoint.maxTimeMs());
        raw.put("db_ratio", dbRatio);
        raw.put("error_rate", errorRate);
        raw.put("calls_per_request", callsPerRequest);
        raw.put("self_ratio", selfRatio);
        raw.put("memory_kb", endpoint.memoryKb());
        raw.put("fragmented", fragmented ? 1.0 : 0.0);
        raw.put("repository_share", repositoryShare);
        raw.put("errors", (double) errors);

        return new Observation(endpoint.getEndpointKey(), callsPerRequest, dbRatio, errorRate, selfRatio,
                endpoint.avgTimeMs(), endpoint.p99TimeMs(), endpoint.memoryKb(), totalMs, raw,
                hottestCalls, hottestSelf, hottestDb, hottestErrors, hottestMemory);
    }

    private Set<String> fragmentedKeys() {
        Map<String, Set<String>> byTrace = new HashMap<>();
        for (RequestTrace trace : registry.getRecentTraces()) {
            if (trace.getTraceId() == null) {
                continue;
            }
            byTrace.computeIfAbsent(trace.getTraceId(), key -> new HashSet<>()).add(trace.getEndpointKey());
        }
        Set<String> fragmented = new HashSet<>();
        int minimum = properties.getLimits().getFragmentMinDistinctKeys();
        for (Set<String> keys : byTrace.values()) {
            if (keys.size() > minimum) {
                fragmented.addAll(keys);
            }
        }
        return fragmented;
    }

    private record Observation(
            String endpointKey,
            double callsPerRequest,
            double dbRatio,
            double errorRate,
            double selfRatio,
            double avgMs,
            double p99Ms,
            double memoryKb,
            double totalMs,
            Map<String, Double> raw,
            MethodStats hottestCalls,
            MethodStats hottestSelf,
            MethodStats hottestDb,
            MethodStats hottestErrors,
            MethodStats hottestMemory
    ) {
    }

    private record Scored(Observation observation, DiagnosticModel.Prediction prediction, double score) {
    }
}
