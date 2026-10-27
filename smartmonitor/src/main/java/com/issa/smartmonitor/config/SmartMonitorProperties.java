package com.issa.smartmonitor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@ConfigurationProperties(prefix = "smartmonitor")
public class SmartMonitorProperties {

    private int windowMinutes;
    private long cleanupIntervalMillis;
    private int maxEndpoints;
    private int maxMethodsPerEndpoint;
    private Paths paths = new Paths();
    private Params params = new Params();
    private Views views = new Views();
    private Limits limits = new Limits();
    private Sort sort = new Sort();
    private Thresholds thresholds = new Thresholds();
    private Reservoir reservoir = new Reservoir();
    private Units units = new Units();
    private Format format = new Format();
    private History history = new History();
    private Ui ui = new Ui();
    private Http http = new Http();
    private Codes codes = new Codes();
    private Messages messages = new Messages();
    private Resources resources = new Resources();
    private Trace trace = new Trace();
    private Ai ai = new Ai();

    @Setter
    @Getter
    public static class Paths {
        private String page;
        private String permitPattern;
        private String overview;
        private String endpoints;
        private String endpointMethods;
        private String analyze;
        private String history;
        private String errors;
        private String topCalled;
        private String traces;
        private String callGraph;
        private String backgroundRoots;
    }

    @Setter
    @Getter
    public static class Params {
        private String search;
        private String sortBy;
        private String sortDir;
        private String limit;
        private String key;
        private String refresh;
        private String traceId;
    }

    @Setter
    @Getter
    public static class Views {
        private String resource;
        private String overview;
        private String endpoints;
        private String endpointMethods;
        private String analyze;
        private String history;
        private String errors;
        private String topCalled;
        private String traces;
        private String callGraph;
        private String backgroundRoots;
    }

    @Setter
    @Getter
    public static class Limits {
        private int topN;
        private int overviewTop;
        private int defaultPageSize;
        private int maxPageSize;
        private int minPageSize;
        private int topCalled;
        private int callGraphEdges;
        private int maxErrors;
        private int maxTraces;
        private int chartTop;
        private int fragmentMinDistinctKeys;
    }

    @Setter
    @Getter
    public static class Sort {
        private String endpointsBy;
        private String endpointsDir;
        private String methodsBy;
        private String methodsDir;
        private String ascending;
        private String descending;
    }

    @Setter
    @Getter
    public static class Thresholds {
        private double n1CallsPerRequest;
        private double n1SevereCallsPerRequest;
        private double endpointDbRatio;
        private double endpointErrorRate;
        private double methodDbRatio;
        private double hotTimeMs;
        private double warnTimeMs;
    }

    @Setter
    @Getter
    public static class Reservoir {
        private int capacity;
        private double p95;
        private double p99;
    }

    @Setter
    @Getter
    public static class Units {
        private double nanosPerMilli;
        private double bytesPerKb;
        private double millisPerSecond;
        private long millisPerMinute;
        private double roundFactor;
        private double percentScale;
    }

    @Setter
    @Getter
    public static class Format {
        private String keySeparator;
        private String displaySeparator;
        private String edgeSeparator;
        private String emptyLabel;
        private String repositoryLayer;
    }

    @Setter
    @Getter
    public static class History {
        private long sampleMillis;
        private int maxPoints;
        private double minElapsedSeconds;
    }

    @Setter
    @Getter
    public static class Ui {
        private long refreshMillis;
        private long searchDebounceMillis;
        private long clockMillis;
        private String pageSizePrefix;
        private List<Integer> pageSizeOptions = new ArrayList<>();
    }

    @Setter
    @Getter
    public static class Http {
        private int ok;
        private int unavailable;
        private int error;
    }

    @Setter
    @Getter
    public static class Codes {
        private String ok;
        private String disabled;
        private String failed;
    }

    @Setter
    @Getter
    public static class Messages {
        private String aiDisabled;
        private String aiFailed;
        private String analyzing;
        private String requestFailed;
        private String unknownProvider;
        private String noData;
        private String noSamples;
        private String missingEndpoint;
    }

    @Setter
    @Getter
    public static class Resources {
        private String pageTemplate;
    }

    @Setter
    @Getter
    public static class Trace {
        private String urlPattern;
        private String mdcKey;
        private String responseHeader;
        private String traceParentHeader;
        private String traceParentSeparator;
        private int traceParentIndex;
        private List<String> inboundHeaders = new ArrayList<>();
    }

    @Setter
    @Getter
    public static class Ai {
        private boolean enabled;
        private String provider;
        private String apiKey;
        private String model;
        private int cacheSeconds;
        private String openaiBaseUrl;
        private String anthropicBaseUrl;
        private String anthropicVersion;
        private String ollamaBaseUrl;
        private String localModelPath;
        private String modelResource;
        private String bundledResource;
        private double temperature;
        private int maxTokens;
        private int anthropicMaxTokens;
        private int promptEndpoints;
        private int maxFindings;
        private String healthyLabel;
        private double emptyConfidence;
        private String systemPrompt;
        private List<String> modelFiles = new ArrayList<>();
    }
}
