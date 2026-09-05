package com.issa.smartmonitor.model;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class LatencyReservoir {

    private final AtomicLongArray samples;
    private final int capacity;
    private final AtomicLong count = new AtomicLong(0);

    public LatencyReservoir(int capacity) {
        this.capacity = capacity;
        this.samples = new AtomicLongArray(capacity);
    }

    public void record(long valueNanos) {
        long n = count.incrementAndGet();
        if (n <= capacity) {
            samples.set((int) (n - 1), valueNanos);
        } else {
            long r = ThreadLocalRandom.current().nextLong(n);
            if (r < capacity) {
                samples.set((int) r, valueNanos);
            }
        }
    }

    public long[] snapshot() {
        long n = count.get();
        int size = (int) Math.min(n, capacity);
        long[] result = new long[size];
        for (int i = 0; i < size; i++) {
            result[i] = samples.get(i);
        }
        return result;
    }

    public static double percentileOf(long[] sortedValues, double p) {
        if (sortedValues.length == 0) return 0;
        int idx = (int) Math.ceil((p / 100.0) * sortedValues.length) - 1;
        idx = Math.max(0, Math.min(sortedValues.length - 1, idx));
        return sortedValues[idx];
    }

    public double percentileMs(double p) {
        long[] values = snapshot();
        Arrays.sort(values);
        return percentileOf(values, p) / 1_000_000.0;
    }
}