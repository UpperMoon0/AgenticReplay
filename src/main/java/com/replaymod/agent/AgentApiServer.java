package com.replaymod.agent;

import com.replaymod.core.Module;
import com.replaymod.core.ReplayMod;

/** Opt-in loopback RPC transport wired into ReplayMod's client lifecycle. */
public final class AgentApiServer implements Module {
    private final ReplayMod core;
    private final AgentReplayApi api;
    public AgentApiServer(ReplayMod core) { this.core = core; this.api = new AgentReplayApi(core); }
    @Override public void initClient() {
        String token = System.getProperty("agenticreplay.token", "");
        if (token.isEmpty()) return;
        try {
            ApiHttpTransport transport = new ApiHttpTransport(Integer.getInteger("agenticreplay.port", 8766),
                    token, task -> core.getMinecraft().send(task), api::call);
            Runtime.getRuntime().addShutdownHook(new Thread(transport::close));
        } catch (java.io.IOException exception) { throw new IllegalStateException("Cannot start AgenticReplay API", exception); }
    }
}
