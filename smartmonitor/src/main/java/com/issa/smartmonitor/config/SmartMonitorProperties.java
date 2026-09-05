package com.issa.smartmonitor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "smartmonitor")
public class SmartMonitorProperties {
    private int windowMinutes = 15;
    private int cleanupIntervalSeconds = 60;
    private int maxEndpoints = 200;
    private int maxMethodsPerEndpoint = 100;
    private Ai ai = new Ai();

    @Setter
    @Getter
    public static class Ai {
        private boolean enabled = false;
        private String provider = "openai";
        private String apiKey;
        private String model = "gpt-4o-mini";
        private int cacheSeconds = 300;
        private String baseUrl = "http://localhost:11434";
        private String localModelPath = "./smartmonitor-models";
    }
}