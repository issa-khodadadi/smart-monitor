package com.issa.smartmonitor.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.issa.smartmonitor.config.SmartMonitorProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class MonitorPageController {

    private final SmartMonitorProperties properties;
    private final ObjectMapper mapper;

    public MonitorPageController(SmartMonitorProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    @GetMapping("${smartmonitor.paths.page}")
    public ResponseEntity<String> page(HttpServletRequest request) throws Exception {
        ClassPathResource resource = new ClassPathResource(properties.getResources().getPageTemplate());
        String html;
        try (InputStream in = resource.getInputStream()) {
            html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        String configJson = mapper.writeValueAsString(clientConfig(request.getContextPath())).replace("<", "\\u003c");
        String injected = "<script>window.SMARTMONITOR_CONFIG = " + configJson + ";</script>";
        html = html.replace("</head>", injected + "\n</head>");
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    private Map<String, Object> clientConfig(String basePath) {
        SmartMonitorProperties.Paths paths = properties.getPaths();
        SmartMonitorProperties.Params params = properties.getParams();
        SmartMonitorProperties.Ui ui = properties.getUi();
        SmartMonitorProperties.Format format = properties.getFormat();
        SmartMonitorProperties.Sort sort = properties.getSort();
        SmartMonitorProperties.Thresholds thresholds = properties.getThresholds();
        SmartMonitorProperties.Messages messages = properties.getMessages();
        SmartMonitorProperties.Ai ai = properties.getAi();

        Map<String, Object> pathMap = new LinkedHashMap<>();
        pathMap.put("overview", paths.getOverview());
        pathMap.put("endpoints", paths.getEndpoints());
        pathMap.put("endpointMethods", paths.getEndpointMethods());
        pathMap.put("analyze", paths.getAnalyze());
        pathMap.put("history", paths.getHistory());
        pathMap.put("errors", paths.getErrors());
        pathMap.put("topCalled", paths.getTopCalled());
        pathMap.put("traces", paths.getTraces());
        pathMap.put("callGraph", paths.getCallGraph());
        pathMap.put("backgroundRoots", paths.getBackgroundRoots());

        Map<String, Object> paramMap = new LinkedHashMap<>();
        paramMap.put("search", params.getSearch());
        paramMap.put("sortBy", params.getSortBy());
        paramMap.put("sortDir", params.getSortDir());
        paramMap.put("limit", params.getLimit());
        paramMap.put("key", params.getKey());
        paramMap.put("refresh", params.getRefresh());
        paramMap.put("traceId", params.getTraceId());

        Map<String, Object> uiMap = new LinkedHashMap<>();
        uiMap.put("refreshMillis", ui.getRefreshMillis());
        uiMap.put("searchDebounceMillis", ui.getSearchDebounceMillis());
        uiMap.put("clockMillis", ui.getClockMillis());
        uiMap.put("pageSizePrefix", ui.getPageSizePrefix());
        uiMap.put("pageSizeOptions", ui.getPageSizeOptions());
        uiMap.put("hotTimeMs", thresholds.getHotTimeMs());
        uiMap.put("warnTimeMs", thresholds.getWarnTimeMs());

        Map<String, Object> formatMap = new LinkedHashMap<>();
        formatMap.put("keySeparator", format.getKeySeparator());
        formatMap.put("displaySeparator", format.getDisplaySeparator());
        formatMap.put("edgeSeparator", format.getEdgeSeparator());
        formatMap.put("emptyLabel", format.getEmptyLabel());

        Map<String, Object> sortMap = new LinkedHashMap<>();
        sortMap.put("endpointsBy", sort.getEndpointsBy());
        sortMap.put("endpointsDir", sort.getEndpointsDir());
        sortMap.put("methodsBy", sort.getMethodsBy());
        sortMap.put("methodsDir", sort.getMethodsDir());
        sortMap.put("ascending", sort.getAscending());
        sortMap.put("descending", sort.getDescending());

        Map<String, Object> thresholdMap = new LinkedHashMap<>();
        thresholdMap.put("n1SevereCallsPerRequest", thresholds.getN1SevereCallsPerRequest());

        Map<String, Object> messageMap = new LinkedHashMap<>();
        messageMap.put("noData", messages.getNoData());
        messageMap.put("analyzing", messages.getAnalyzing());
        messageMap.put("requestFailed", messages.getRequestFailed());

        Map<String, Object> aiMap = new LinkedHashMap<>();
        aiMap.put("enabled", ai.isEnabled());
        aiMap.put("provider", ai.getProvider());

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("basePath", basePath);
        root.put("paths", pathMap);
        root.put("params", paramMap);
        root.put("ui", uiMap);
        root.put("format", formatMap);
        root.put("sort", sortMap);
        root.put("thresholds", thresholdMap);
        root.put("messages", messageMap);
        root.put("ai", aiMap);
        return root;
    }
}
