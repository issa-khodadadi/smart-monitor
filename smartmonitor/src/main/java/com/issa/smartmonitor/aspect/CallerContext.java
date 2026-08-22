package com.issa.smartmonitor.aspect;

public class CallerContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    public static void set(String key) {
        CURRENT.set(key);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}