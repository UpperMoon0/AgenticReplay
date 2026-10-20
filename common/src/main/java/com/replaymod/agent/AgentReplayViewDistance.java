package com.replaymod.agent;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Session-only camera budget; recorded server packets must not undo it during seeking. */
public final class AgentReplayViewDistance {
    private static volatile int override;
    private static int previousClientDistance;
    private static IntConsumer restoreClientDistance;
    private AgentReplayViewDistance() {}
    public static void set(int value, IntSupplier clientDistance, IntConsumer setClientDistance) {
        if (value < 2 || value > 32) throw new IllegalArgumentException("viewDistance must be 2..32");
        if (restoreClientDistance == null) {
            previousClientDistance = clientDistance.getAsInt();
            restoreClientDistance = setClientDistance;
        }
        override = value;
        setClientDistance.accept(value);
    }
    public static int effective(int recorded) { return override == 0 ? recorded : override; }
    public static void clear() {
        override = 0;
        IntConsumer restore = restoreClientDistance;
        restoreClientDistance = null;
        if (restore != null) restore.accept(previousClientDistance);
    }
}
