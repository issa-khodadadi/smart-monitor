package com.issa.smartmonitor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.issa.smartmonitor.ai.DiagnosticAnalyzer;
import com.issa.smartmonitor.ai.DiagnosticModel;
import com.issa.smartmonitor.core.MetricRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

@Configuration
public class SmartMonitorAiConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "smartmonitor.ai", name = "enabled", havingValue = "true")
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
    public DiagnosticAnalyzer diagnosticAnalyzer(MetricRegistry registry, SmartMonitorProperties properties, DiagnosticModel model) {
        return new DiagnosticAnalyzer(registry, properties, model);
    }
}
