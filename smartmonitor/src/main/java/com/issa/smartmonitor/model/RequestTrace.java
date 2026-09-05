package com.issa.smartmonitor.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RequestTrace {
    private final long timestampMillis;
    private final String traceId;
    private final String endpointKey;
    private final double durationMs;
    private final boolean error;
}