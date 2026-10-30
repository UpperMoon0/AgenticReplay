/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.recording.packet;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Network packets, synthetic packets and finalization share one FIFO. */
public final class OrderedClientTasks {
    private final ConcurrentLinkedQueue<Runnable> pending = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final BooleanSupplier onClient;
    private final Consumer<Runnable> schedule;
    private boolean draining;

    public OrderedClientTasks(BooleanSupplier onClient, Consumer<Runnable> schedule) {
        this.onClient = onClient;
        this.schedule = schedule;
    }

    public void submit(Runnable task) {
        pending.add(task);
        if (onClient.getAsBoolean()) drain();
        else requestDrain();
    }

    private void requestDrain() {
        if (scheduled.compareAndSet(false, true)) schedule.accept(this::drain);
    }

    private void drain() {
        if (!onClient.getAsBoolean()) throw new IllegalStateException("Recorder requires client thread");
        if (draining) return;
        draining = true;
        try {
            Runnable task;
            while ((task = pending.poll()) != null) task.run();
        } finally {
            draining = false;
            scheduled.set(false);
            if (!pending.isEmpty()) requestDrain();
        }
    }
}
