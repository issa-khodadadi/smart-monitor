package com.issa.smartmonitor.aspect;

import java.util.ArrayDeque;
import java.util.Deque;

public class CallStack {

    public static class Frame {
        public final String key;
        public long childTimeNanos = 0;

        public Frame(String key) {
            this.key = key;
        }
    }

    private static final ThreadLocal<Deque<Frame>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    public static Frame push(String key) {
        Frame frame = new Frame(key);
        STACK.get().push(frame);
        return frame;
    }

    public static void pop() {
        Deque<Frame> stack = STACK.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) STACK.remove();
    }

    public static Frame peekParent() {
        Deque<Frame> stack = STACK.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    public static String rootKey() {
        Deque<Frame> stack = STACK.get();
        return stack.isEmpty() ? null : stack.peekLast().key;
    }
}