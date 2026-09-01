package com.issa.smartmonitor.aspect;

import java.util.Deque;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public final class MonitoringContext {

    private final Deque<CallStack.Frame> stack;
    private final String callerKey;

    private MonitoringContext(Deque<CallStack.Frame> stack, String callerKey) {
        this.stack = stack;
        this.callerKey = callerKey;
    }

    public static MonitoringContext capture() {
        return new MonitoringContext(CallStack.snapshot(), CallerContext.get());
    }

    private void applyToCurrentThread() {
        CallStack.restore(stack);
        if (callerKey != null) CallerContext.set(callerKey);
    }

    private void clearFromCurrentThread() {
        CallStack.clear();
        CallerContext.clear();
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