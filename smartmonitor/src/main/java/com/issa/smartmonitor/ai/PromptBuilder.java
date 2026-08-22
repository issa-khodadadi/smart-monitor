package com.issa.smartmonitor.ai;

import com.issa.smartmonitor.model.EndpointStats;
import com.issa.smartmonitor.model.MethodStats;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class PromptBuilder {

    public String buildOverviewPrompt(List<EndpointStats> endpoints) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a backend performance analyst. Analyze the following Spring Boot application ")
                .append("runtime metrics (aggregated over a rolling time window) and identify the top bottlenecks.\n\n")
                .append("For each significant issue, explain: what is slow, why (CPU-bound / DB-bound / high call count), ")
                .append("and a concrete optimization suggestion.\n\n")
                .append("Respond ONLY in this JSON array format, no extra text:\n")
                .append("[{\"endpoint\":\"...\",\"issue\":\"...\",\"cause\":\"CPU|DATABASE|N_PLUS_ONE|MEMORY|OTHER\",")
                .append("\"severity\":\"HIGH|MEDIUM|LOW\",\"suggestion\":\"...\"}]\n\n")
                .append("Metrics data:\n");

        List<EndpointStats> sorted = endpoints.stream()
                .sorted(Comparator.comparingDouble(EndpointStats::totalTimeMs).reversed())
                .limit(10)
                .toList();

        for (EndpointStats ep : sorted) {
            sb.append("- Endpoint: ").append(ep.getEndpointKey().replace("#", ".")).append("\n")
                    .append("  calls=").append(ep.getCallCount().sum())
                    .append(", errors=").append(ep.getErrorCount().sum())
                    .append(", avgMs=").append(round(ep.avgTimeMs()))
                    .append(", maxMs=").append(round(ep.maxTimeMs()))
                    .append(", totalMs=").append(round(ep.totalTimeMs()))
                    .append(", dbTimeMs=").append(round(ep.dbTimeMs()))
                    .append(", memoryKb=").append(round(ep.memoryKb())).append("\n");

            List<MethodStats> topMethods = ep.getMethods().stream()
                    .sorted(Comparator.comparingDouble(MethodStats::selfTimeMs).reversed())
                    .limit(5)
                    .toList();

            for (MethodStats m : topMethods) {
                sb.append("    * ").append(m.getClassName()).append(".").append(m.getMethodName())
                        .append(" [").append(m.getLayer()).append("]")
                        .append(" calls=").append(m.getCallCount().sum())
                        .append(", selfMs=").append(round(m.selfTimeMs()))
                        .append(", dbMs=").append(round(m.dbTimeMs()))
                        .append(", memoryKb=").append(round(m.totalMemoryKb()))
                        .append(", type=").append(m.bottleneckType()).append("\n");
            }
        }

        return sb.toString();
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}