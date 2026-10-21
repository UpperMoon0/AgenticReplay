package com.agenticreplay.legacy;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;

/** Nonblocking admission; never holds a queue lock while executing client code. */
public final class LegacyClientQueue {
    private final ArrayBlockingQueue<Runnable> pending;
    private boolean draining;
    public LegacyClientQueue() { this(32); }
    LegacyClientQueue(int capacity) { pending = new ArrayBlockingQueue<Runnable>(capacity); }
    public void enqueue(Runnable task) {
        if (!pending.offer(task)) throw new RejectedExecutionException("Client queue is full; retry after checking status");
    }
    /** Called only on the render thread, even when simulation/replay time is paused. */
    public void drain() {
        if (draining) return;
        draining = true;
        try {
            // Work admitted during this frame waits for the next frame. This also
            // bounds work if a task enqueues another task or triggers nested renders.
            int count = pending.size();
            for (int i = 0; i < count; i++) {
                Runnable task = pending.poll();
                if (task != null) task.run();
            }
        } finally { draining = false; }
    }
}
