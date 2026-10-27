package com.issa.smartmonitor.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

/**
 * Loads shipped defaults at the bottom of the environment so application config wins.
 */
public class SmartMonitorEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String RESOURCE = "smartmonitor-defaults.yaml";
    static final String SOURCE_NAME = "smartmonitor-defaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(SOURCE_NAME)) {
            return;
        }
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        if (!resource.exists()) {
            return;
        }
        try {
            List<PropertySource<?>> loaded = new YamlPropertySourceLoader().load(SOURCE_NAME, resource);
            for (int i = loaded.size() - 1; i >= 0; i--) {
                environment.getPropertySources().addLast(loaded.get(i));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load " + RESOURCE, ex);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
