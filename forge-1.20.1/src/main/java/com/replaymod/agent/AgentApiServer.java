package com.replaymod.agent;

import com.replaymod.core.Module;
import com.replaymod.core.ReplayMod;

/** Opt-in loopback RPC transport wired into ReplayMod's client lifecycle. */
public final class AgentApiServer implements Module {
    private static AgentReplayApi activeApi;
    private final ReplayMod core;
    private final AgentReplayApi api;
    public AgentApiServer(ReplayMod core) { this.core = core; this.api = new AgentReplayApi(core); }
    @Override public void initClient() {
        String token = System.getProperty("agenticreplay.token", "");
        if (token.isEmpty()) return;
        if (Boolean.getBoolean("agenticreplay.background")) core.getMinecraft().options.pauseOnLostFocus = false;
        try {
            ApiHttpTransport transport = new ApiHttpTransport(Integer.getInteger("agenticreplay.port", 8766),
                    token, task -> core.getMinecraft().send(task), api::call);
            Runtime.getRuntime().addShutdownHook(new Thread(transport::close));
            activeApi = api;
            new de.johni0702.minecraft.gui.utils.EventRegistrations()
                    .on(de.johni0702.minecraft.gui.versions.callbacks.PreTickCallback.EVENT, api::tickPlayer)
                    .register();
        } catch (java.io.IOException exception) { throw new IllegalStateException("Cannot start AgenticReplay API", exception); }
    }
    public static void physicalInput() {
        if (activeApi != null) activeApi.stopPlayer("Physical input took control");
    }
    public static void tickActors() { if (activeApi != null) activeApi.tickActors(); }
    public static boolean holdingAttack() { return activeApi != null && activeApi.holdingAttack(); }
}
