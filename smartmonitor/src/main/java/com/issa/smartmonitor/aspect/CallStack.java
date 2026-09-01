package com.issa.smartmonitor.aspect;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;

public class CallStack {

    public static class Frame {
        public final String key;
        public final AtomicLong childTimeNanos = new AtomicLong(0); // CHANGED: long -> AtomicLong (cross-thread mutation ممکنه)

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

    /** NEW: کپی از استک ترد فعلی می‌گیره (همون Frame reference ها) تا برای ترد دیگه propagate بشه. */
    public static Deque<Frame> snapshot() {
        Deque<Frame> current = STACK.get();
        return current.isEmpty() ? null : new ArrayDeque<>(current);
    }

    /** NEW: یه snapshot گرفته‌شده رو روی ترد فعلی نصب می‌کنه. */
    public static void restore(Deque<Frame> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            STACK.remove();
            return;
        }
        STACK.set(new ArrayDeque<>(snapshot));
    }

    /** NEW: باید حتماً بعد از اتمام تسک propagate-شده صدا زده بشه، وگرنه thread pool آلوده می‌مونه. */
    public static void clear() {
        STACK.remove();
    }
}