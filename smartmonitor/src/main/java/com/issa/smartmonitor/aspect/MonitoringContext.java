package com.issa.smartmonitor.aspect;

import java.util.Deque;

public final class MonitoringContext {

    private final Deque<CallStack.Frame> stack;
    private final String traceId;
    private final String httpEndpointKey;

    private MonitoringContext(Deque<CallStack.Frame> stack, String traceId, String httpEndpointKey) {
        this.stack = stack;
        this.traceId = traceId;
        this.httpEndpointKey = httpEndpointKey;
    }

    public static MonitoringContext capture() {
        return new MonitoringContext(CallStack.snapshot(), TraceContext.get(), HttpEndpointContext.get());
    }

    private void applyToCurrentThread() {
        CallStack.restore(stack);
        if (traceId != null) TraceContext.set(traceId);
        if (httpEndpointKey != null) HttpEndpointContext.set(httpEndpointKey);
    }

    private void clearFromCurrentThread() {
        CallStack.clear();
        TraceContext.clear();
        HttpEndpointContext.clear();
    }

    public Runnable wrap(Runnable task) {
        return () -> {
            applyToCurrentThread();
            try {
                task.run();
            } finally {
                clearFromCurrentThread();
            }
        };
    }
}