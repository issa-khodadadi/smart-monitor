package com.issa.smartmonitor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.issa.smartmonitor.ai.AiAnalysisProvider;
import com.issa.smartmonitor.ai.AiAnalysisService;
import com.issa.smartmonitor.ai.AnthropicProvider;
import com.issa.smartmonitor.ai.DiagnosticAnalyzer;
import com.issa.smartmonitor.ai.DiagnosticModel;
import com.issa.smartmonitor.ai.JlamaProvider;
import com.issa.smartmonitor.ai.OllamaProvider;
import com.issa.smartmonitor.ai.OpenAiProvider;
import com.issa.smartmonitor.core.MetricRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

@Configuration
public class SmartMonitorAiConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "smartmonitor.ai", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "smartmonitor.ai", name = "provider", havingValue = "diagnostic", matchIfMissing = true)
    public DiagnosticModel diagnosticModel(SmartMonitorProperties properties, ObjectMapper mapper) {
        String resource = properties.getAi().getModelResource();
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            return DiagnosticModel.load(in, mapper);
        } catch (Exception ex) {
            throw new IllegalStateException(properties.getMessages().getAiFailed(), ex);
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = "smartmonitor.ai", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "smartmonitor.ai", name = "provider", havingValue = "diagnostic", matchIfMissing = true)
    public DiagnosticAnalyzer diagnosticAnalyzer(MetricRegistry registry, SmartMonitorProperties properties, DiagnosticModel model) {
        return new DiagnosticAnalyzer(registry, properties, model);
    }

    @Bean
    @ConditionalOnExpression("${smartmonitor.ai.enabled:false} and '${smartmonitor.ai.provider:diagnostic}' != 'diagnostic'")
    public AiAnalysisProvider aiAnalysisProvider(SmartMonitorProperties properties) {
        return switch (properties.getAi().getProvider()) {
            case "openai" -> new OpenAiProvider(properties);
            case "anthropic" -> new AnthropicProvider(properties);
            case "ollama" -> new OllamaProvider(properties);
            case "jlama" -> new JlamaProvider(properties);
            default -> throw new IllegalStateException(properties.getMessages().getUnknownProvider());
        };
    }

    @Bean
    @ConditionalOnExpression("${smartmonitor.ai.enabled:false} and '${smartmonitor.ai.provider:diagnostic}' != 'diagnostic'")
    public AiAnalysisService aiAnalysisService(MetricRegistry registry, SmartMonitorProperties properties, AiAnalysisProvider provider) {
        return new AiAnalysisService(registry, properties, provider);
    }
}
