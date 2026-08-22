package com.issa.smartmonitor.ai;

public class AiAnalysisResult {
    private final String rawJson;
    private final long generatedAtMillis;

    public AiAnalysisResult(String rawJson, long generatedAtMillis) {
        this.rawJson = rawJson;
        this.generatedAtMillis = generatedAtMillis;
    }

    public String getRawJson() { return rawJson; }
    public long getGeneratedAtMillis() { return generatedAtMillis; }
}