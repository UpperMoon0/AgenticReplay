package com.replaymod.render.hooks;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** A graph update can discover new builds after the worker queue looked empty. */
public final class EmbeddiumTerrainBarrier {
    private EmbeddiumTerrainBarrier() {}

    public static void complete(Runnable setupTerrain, BooleanSupplier pending) {
        complete(setupTerrain, pending, System::nanoTime, TimeUnit.SECONDS.toNanos(30));
    }

    static void complete(Runnable setupTerrain, BooleanSupplier pending, LongSupplier clock, long timeout) {
        long start = clock.getAsLong();
        do {
            // Use WorldRenderer's normal entry point, so Embeddium advances its
            // owning frame counter and retains the managed render-device scope.
            setupTerrain.run();
            if (!pending.getAsBoolean()) return;
        } while (clock.getAsLong() - start < timeout);
        throw new IllegalStateException("Embeddium terrain did not settle; export aborted");
    }
}
