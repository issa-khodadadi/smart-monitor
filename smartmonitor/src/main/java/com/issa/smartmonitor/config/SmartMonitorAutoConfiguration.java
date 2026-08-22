package com.issa.smartmonitor.config;

import com.issa.smartmonitor.ai.AiAnalysisService;
import com.issa.smartmonitor.aspect.MonitoringAspect;
import com.issa.smartmonitor.core.MetricCleanupTask;
import com.issa.smartmonitor.core.MetricRegistry;
import com.issa.smartmonitor.core.MonitorController;
import com.issa.smartmonitor.core.MonitorPageController;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(SmartMonitorProperties.class)
@Import({SmartMonitorSecurityConfig.class, SmartMonitorAiConfiguration.class})
public class SmartMonitorAutoConfiguration {

    @Bean
    public MetricRegistry metricRegistry(SmartMonitorProperties properties) {
        return new MetricRegistry(properties.getMaxEndpoints(), properties.getMaxMethodsPerEndpoint());
    }

    @Bean
    public MonitoringAspect monitoringAspect(MetricRegistry registry) {
        return new MonitoringAspect(registry);
    }

    @Bean
    public MonitorController monitorController(MetricRegistry registry, ObjectProvider<AiAnalysisService> aiAnalysisServiceProvider) {
        return new MonitorController(registry, aiAnalysisServiceProvider);
    }

    @Bean
    public MonitorPageController monitorPageController() {
        return new MonitorPageController();
    }

    @Bean
    public MetricCleanupTask metricCleanupTask(MetricRegistry registry, SmartMonitorProperties properties) {
        return new MetricCleanupTask(registry, properties);
    }
}