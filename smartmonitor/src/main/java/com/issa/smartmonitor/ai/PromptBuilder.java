package com.issa.smartmonitor.ai;

import com.issa.smartmonitor.model.EndpointStats;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class PromptBuilder {

    private static final int MAX_ENDPOINTS = 3;

    public String buildOverviewPrompt(List<EndpointStats> endpoints) {
        List<EndpointStats> top = endpoints.stream()
                .sorted(Comparator.comparingDouble(EndpointStats::totalTimeMs).reversed())
                .limit(MAX_ENDPOINTS)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("You are a performance monitoring assistant. Below are the slowest API endpoints. ")
                .append("For EACH one, write exactly one line in this exact format, nothing else:\n")
                .append("ENDPOINT_NAME | CAUSE(CPU or DATABASE or OTHER) | ONE_SHORT_SUGGESTION\n\n")
                .append("Data:\n");

        for (EndpointStats ep : top) {
            double dbRatio = ep.totalTimeMs() == 0 ? 0 : (ep.dbTimeMs() / ep.totalTimeMs()) * 100;
            long calls = ep.getCallCount().sum();
            double errorRate = calls == 0 ? 0 : ((double) ep.getErrorCount().sum() / calls) * 100;

            sb.append("- ").append(ep.getEndpointKey().replace("#", "."))
                    .append(": totalMs=").append(round(ep.totalTimeMs()))
                    .append(", selfMs=").append(round(ep.totalTimeMs()))
                    .append(", calls=").append(calls)
                    .append(", dbRatio=").append(round(dbRatio)).append("%")
                    .append(", errorRate=").append(round(errorRate)).append("%")
                    .append(", memoryKb=").append(round(ep.memoryKb()))
                    .append("\n");
        }

        return sb.toString();
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}