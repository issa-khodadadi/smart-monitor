package com.issa.smartmonitor.core;

import com.issa.smartmonitor.config.SmartMonitorProperties;

/**
 * Snapshot of the numeric rules the collectors need on the hot path.
 */
public record MonitorTuning(
        int reservoirCapacity,
        double p95,
        double p99,
        double endpointDbRatio,
        double endpointErrorRate,
        double methodDbRatio,
        double nanosPerMilli,
        double bytesPerKb,
        double roundFactor,
        String keySeparator,
        String displaySeparator,
        String edgeSeparator,
        String emptyLabel,
        String repositoryLayer,
        int maxErrors,
        int maxTraces,
        int callGraphLimit,
        double millisPerSecond,
        double minElapsedSeconds,
        double percentScale
) {

    public static MonitorTuning from(SmartMonitorProperties properties) {
        SmartMonitorProperties.Units units = properties.getUnits();
        SmartMonitorProperties.Format format = properties.getFormat();
        SmartMonitorProperties.Thresholds thresholds = properties.getThresholds();
        SmartMonitorProperties.Reservoir reservoir = properties.getReservoir();
        SmartMonitorProperties.Limits limits = properties.getLimits();
        SmartMonitorProperties.History history = properties.getHistory();
        return new MonitorTuning(
                reservoir.getCapacity(),
                reservoir.getP95(),
                reservoir.getP99(),
                thresholds.getEndpointDbRatio(),
                thresholds.getEndpointErrorRate(),
                thresholds.getMethodDbRatio(),
                units.getNanosPerMilli(),
                units.getBytesPerKb(),
                units.getRoundFactor(),
                format.getKeySeparator(),
                format.getDisplaySeparator(),
                format.getEdgeSeparator(),
                format.getEmptyLabel(),
                format.getRepositoryLayer(),
                limits.getMaxErrors(),
                limits.getMaxTraces(),
                limits.getCallGraphEdges(),
                units.getMillisPerSecond(),
                history.getMinElapsedSeconds(),
                units.getPercentScale()
        );
    }

    public double round(double value) {
        return Math.round(value * roundFactor) / roundFactor;
    }

    public double ms(long nanos) {
        return nanos / nanosPerMilli;
    }

    public double kb(long bytes) {
        return bytes / bytesPerKb;
    }

    public String methodKey(String className, String methodName) {
        return className + keySeparator + methodName;
    }

    public String display(String key) {
        if (key == null || key.isBlank()) {
            return emptyLabel;
        }
        return key.replace(keySeparator, displaySeparator);
    }
}
