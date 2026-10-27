package com.issa.smartmonitor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.issa.smartmonitor.ai.AiAnalysisService;
import com.issa.smartmonitor.ai.DiagnosticAnalyzer;
import com.issa.smartmonitor.aspect.MonitoringAspect;
import com.issa.smartmonitor.aspect.MonitoringTaskDecoratorBeanPostProcessor;
import com.issa.smartmonitor.core.*;
import com.issa.smartmonitor.web.TraceIdFilter;
import com.issa.smartmonitor.web.ViewCatalog;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(SmartMonitorProperties.class)
@Import({SmartMonitorSecurityConfig.class, SmartMonitorAiConfiguration.class})
public class SmartMonitorAutoConfiguration {

    @Bean
    public MetricRegistry metricRegistry(SmartMonitorProperties properties) {
        return new MetricRegistry(properties);
    }

    @Bean
    public MonitoringAspect monitoringAspect(MetricRegistry registry, SmartMonitorProperties properties) {
        return new MonitoringAspect(registry, properties);
    }

    @Bean
    public ViewCatalog viewCatalog(SmartMonitorProperties properties, ObjectMapper objectMapper) {
        return new ViewCatalog(properties, objectMapper);
    }

    @Bean
    public MonitorController monitorController(MetricRegistry registry,
                                               ObjectProvider<DiagnosticAnalyzer> diagnosticAnalyzer,
                                               ObjectProvider<AiAnalysisService> aiAnalysisService,
                                               MetricHistory metricHistory,
                                               SmartMonitorProperties properties,
                                               ViewCatalog views,
                                               ObjectMapper objectMapper) {
        return new MonitorController(registry, diagnosticAnalyzer, aiAnalysisService, metricHistory, properties, views, objectMapper);
    }

    @Bean
    public MonitorPageController monitorPageController(SmartMonitorProperties properties, ObjectMapper objectMapper) {
        return new MonitorPageController(properties, objectMapper);
    }

    @Bean
    public MetricCleanupTask metricCleanupTask(MetricRegistry registry, SmartMonitorProperties properties) {
        return new MetricCleanupTask(registry, properties);
    }

    @Bean
    public MetricHistory metricHistory(MetricRegistry registry, SmartMonitorProperties properties) {
        return new MetricHistory(properties.getHistory().getMaxPoints(), registry.getTuning());
    }

    @Bean
    public MetricSnapshotTask metricSnapshotTask(MetricRegistry registry, MetricHistory history) {
        return new MetricSnapshotTask(registry, history);
    }

    @Bean
    public static MonitoringTaskDecoratorBeanPostProcessor monitoringTaskDecoratorBeanPostProcessor() {
        return new MonitoringTaskDecoratorBeanPostProcessor();
    }

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter(SmartMonitorProperties properties) {
        FilterRegistrationBean<TraceIdFilter> reg = new FilterRegistrationBean<>(new TraceIdFilter(properties));
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns(properties.getTrace().getUrlPattern());
        return reg;
    }
}