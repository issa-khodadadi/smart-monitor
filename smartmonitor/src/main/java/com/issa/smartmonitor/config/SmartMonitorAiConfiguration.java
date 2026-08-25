package com.issa.smartmonitor.config;

import com.issa.smartmonitor.ai.*;
import com.issa.smartmonitor.core.MetricRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "smartmonitor.ai", name = "enabled", havingValue = "true")
public class SmartMonitorAiConfiguration {

    @Bean
    public AiAnalysisProvider aiAnalysisProvider(SmartMonitorProperties properties) {
        SmartMonitorProperties.Ai ai = properties.getAi();

        if ("ollama".equalsIgnoreCase(ai.getProvider())) {
            return new OllamaProvider(ai.getBaseUrl(), ai.getModel());
        }

        if (ai.getApiKey() == null || ai.getApiKey().isBlank()) {
            return null;
        }
        if ("anthropic".equalsIgnoreCase(ai.getProvider())) {
            return new AnthropicProvider(ai.getApiKey(), ai.getModel());
        }
        return new OpenAiProvider(ai.getApiKey(), ai.getModel());
    }

    @Bean
    public AiAnalysisService aiAnalysisService(MetricRegistry registry, SmartMonitorProperties properties, AiAnalysisProvider provider) {
        return new AiAnalysisService(registry, properties, provider);
    }
}