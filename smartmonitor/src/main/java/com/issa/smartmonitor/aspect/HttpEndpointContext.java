package com.issa.smartmonitor.aspect;

public class HttpEndpointContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    public static void set(String endpointKey) {
        CURRENT.set(endpointKey);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}