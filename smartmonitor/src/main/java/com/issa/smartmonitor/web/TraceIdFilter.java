package com.issa.smartmonitor.web;

import com.issa.smartmonitor.aspect.HttpEndpointContext;
import com.issa.smartmonitor.aspect.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class TraceIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "traceId";
    public static final String RESPONSE_HEADER = "X-Trace-Id";
    private static final String[] INBOUND_HEADERS = {"X-Request-Id", "X-Correlation-Id", "X-Trace-Id"};

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = extractInbound(request);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        TraceContext.set(traceId);
        MDC.put(MDC_KEY, traceId);
        response.setHeader(RESPONSE_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
            HttpEndpointContext.clear();
            MDC.remove(MDC_KEY);
        }
    }

    private String extractInbound(HttpServletRequest request) {
        for (String header : INBOUND_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) return value;
        }
        String traceparent = request.getHeader("traceparent");
        if (traceparent != null) {
            String[] parts = traceparent.split("-");
            if (parts.length >= 2 && !parts[1].isBlank()) return parts[1];
        }
        return null;
    }
}