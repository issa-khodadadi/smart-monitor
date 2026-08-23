package com.issa.smartmonitor.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MetricSnapshot {
    private final long timestampMillis;
    private final double cpuTimeMsPerSec;
    private final double dbTimeMsPerSec;
    private final long errorCount;
}