package com.issa.smartmonitor.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorEntry {
    private final long timestampMillis;
    private final String endpointKey;
    private final String className;
    private final String methodName;
    private final String exceptionType;
    private final String message;
}
