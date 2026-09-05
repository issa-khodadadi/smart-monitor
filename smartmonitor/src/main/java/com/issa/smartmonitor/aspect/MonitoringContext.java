package com.issa.smartmonitor.aspect;

import java.util.Deque;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public final class MonitoringContext {

    private final Deque<CallStack.Frame> stack;
    private final String callerKey;
    private final String traceId;
    private final String httpEndpointKey;

    private MonitoringContext(Deque<CallStack.Frame> stack, String callerKey, String traceId, String httpEndpointKey) {
        this.stack = stack;
        this.callerKey = callerKey;
        this.traceId = traceId;
        this.httpEndpointKey = httpEndpointKey;
    }

    public static MonitoringContext capture() {
        return new MonitoringContext(CallStack.snapshot(), CallerContext.get(), TraceContext.get(), HttpEndpointContext.get());
    }

    private void applyToCurrentThread() {
        CallStack.restore(stack);
        if (callerKey != null) CallerContext.set(callerKey);
        if (traceId != null) TraceContext.set(traceId);
        if (httpEndpointKey != null) HttpEndpointContext.set(httpEndpointKey);
    }

    private void clearFromCurrentThread() {
        CallStack.clear();
        CallerContext.clear();
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

    public <T> Callable<T> wrap(Callable<T> task) {
        return () -> {
            applyToCurrentThread();
            try {
                return task.call();
            } finally {
                clearFromCurrentThread();
            }
        };
    }

    public <T> Supplier<T> wrap(Supplier<T> task) {
        return () -> {
            applyToCurrentThread();
            try {
                return task.get();
            } finally {
                clearFromCurrentThread();
            }
        };
    }
}