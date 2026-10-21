package com.agenticreplay.legacy;

import com.google.gson.*;
import com.replaymod.agent.ApiHttpTransport;

/** Opt-in RPC lifecycle. A render-driven queue also runs while paused, without engine queue locks. */
public final class LegacyClientProxy extends LegacyServerProxy {
    @Override public void initialize() {
        final String token = System.getProperty("agenticreplay.token", "");
        if (token.isEmpty()) return;
        final LegacyReplayBridge replay = new LegacyReplayBridge();
        final LegacyClientControls live = new LegacyClientControls(replay);
        final LegacyClientQueue queue = new LegacyClientQueue();
        new LegacyLifecycle(replay, queue).register();
        try {
            final ApiHttpTransport transport = new ApiHttpTransport(Integer.getInteger("agenticreplay.port", 8766), token,
                    queue::enqueue, (method, params) -> {
                if ("capabilities".equals(method)) {
                    JsonObject result = new JsonObject();
                    result.addProperty("apiVersion", 1);
                    result.addProperty("minecraftVersion", "1.7.10");
                    result.addProperty("engine", "ReplayMod 1.7.10-2.5.2");
                    result.add("engineBuild", LegacyEngineBuild.describe());
                    result.add("renderCompatibility", LegacyRenderCompatibility.current());
                    JsonArray methods = new JsonArray();
                    methods.add(new JsonPrimitive("capabilities"));
                    for (String name : LegacyReplayBridge.methods()) methods.add(new JsonPrimitive(name));
                    for (String name : LegacyClientControls.METHODS) methods.add(new JsonPrimitive(name));
                    result.add("methods", methods);
                    result.addProperty("recordingSemantics", "Enable before connection; pause/stop use cut/split markers; disconnect finalizes asynchronously");
                    result.addProperty("limitations", "No remote export/path editing/scripted actors/player sequences/native capture; original ReplayMod UI remains available");
                    return result;
                }
                if ("status".equals(method)) {
                    JsonObject result = replay.call(method, params).getAsJsonObject();
                    result.add("client", live.state());
                    return result;
                }
                return replay.supports(method) ? replay.call(method, params) : live.call(method, params);
            });
            Runtime.getRuntime().addShutdownHook(new Thread(transport::close, "AgenticReplay legacy shutdown"));
        } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot start AgenticReplay legacy API", failure); }
    }
}
