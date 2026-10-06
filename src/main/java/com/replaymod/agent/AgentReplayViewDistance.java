package com.replaymod.agent;

/** Session-only camera budget; recorded server packets must not undo it during seeking. */
public final class AgentReplayViewDistance {
    private static volatile int override;
    private AgentReplayViewDistance() {}
    public static void set(int value) {
        if (value < 2 || value > 32) throw new IllegalArgumentException("viewDistance must be 2..32");
        override = value;
    }
    public static int effective(int recorded) { return override == 0 ? recorded : override; }
    public static void clear() { override = 0; }
}
