package com.issa.smartmonitor.web;

import com.issa.smartmonitor.aspect.HttpEndpointContext;
import com.issa.smartmonitor.aspect.TraceContext;
import com.issa.smartmonitor.config.SmartMonitorProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class TraceIdFilter extends OncePerRequestFilter {

    private final SmartMonitorProperties properties;

    public TraceIdFilter(SmartMonitorProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = extractInbound(request);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        TraceContext.set(traceId);
        MDC.put(properties.getTrace().getMdcKey(), traceId);
        response.setHeader(properties.getTrace().getResponseHeader(), traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
            HttpEndpointContext.clear();
            MDC.remove(properties.getTrace().getMdcKey());
        }
    }

    private String extractInbound(HttpServletRequest request) {
        for (String header : properties.getTrace().getInboundHeaders()) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) return value;
        }
        String traceparent = request.getHeader(properties.getTrace().getTraceParentHeader());
        if (traceparent != null) {
            String[] parts = traceparent.split(properties.getTrace().getTraceParentSeparator());
            int index = properties.getTrace().getTraceParentIndex();
            if (parts.length > index && !parts[index].isBlank()) return parts[index];
        }
        return null;
    }
}