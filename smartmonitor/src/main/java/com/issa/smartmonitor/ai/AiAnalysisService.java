package com.issa.smartmonitor.ai;

import com.issa.smartmonitor.config.SmartMonitorProperties;
import com.issa.smartmonitor.core.MetricRegistry;

import java.util.concurrent.atomic.AtomicReference;

public class AiAnalysisService {

    private final MetricRegistry registry;
    private final SmartMonitorProperties properties;
    private final AiAnalysisProvider provider;
    private final PromptBuilder promptBuilder = new PromptBuilder();

    private final AtomicReference<AiAnalysisResult> cache = new AtomicReference<>();

    public AiAnalysisService(MetricRegistry registry, SmartMonitorProperties properties, AiAnalysisProvider provider) {
        this.registry = registry;
        this.properties = properties;
        this.provider = provider;
    }

    public boolean isEnabled() {
        return properties.getAi().isEnabled() && provider != null;
    }

    public synchronized AiAnalysisResult analyze(boolean forceRefresh) {
        if (!isEnabled()) {
            throw new AiAnalysisException("AI analysis is not enabled or misconfigured.", null);
        }

        AiAnalysisResult cached = cache.get();
        long cacheMillis = properties.getAi().getCacheSeconds() * 1000L;
        if (!forceRefresh && cached != null &&
                (System.currentTimeMillis() - cached.getGeneratedAtMillis()) < cacheMillis) {
            return cached;
        }

        String prompt = promptBuilder.buildOverviewPrompt(new java.util.ArrayList<>(registry.getEndpoints()));

        try {
            String result = provider.analyze(prompt);
            AiAnalysisResult analysisResult = new AiAnalysisResult(result, System.currentTimeMillis());
            cache.set(analysisResult);
            return analysisResult;
        } catch (Exception e) {
            throw new AiAnalysisException("AI analysis failed: " + e.getMessage(), e);
        }
    }
}