package com.agenticreplay.legacy;

import com.google.gson.JsonObject;

/** Reports the actual assembly model, without implying a complete historical rebuild. */
public final class LegacyEngineBuild {
    private LegacyEngineBuild() {}
    public static JsonObject describe() {
        JsonObject result = new JsonObject();
        result.addProperty("owner", "AgenticReplay");
        result.addProperty("version", BuildInfo.VERSION);
        result.addProperty("buildKind", "binary-source-overlay");
        result.addProperty("launchProfile", "unimixins");
        result.addProperty("upstream", "ReplayMod 1.7.10-2.5.2");
        result.addProperty("upstreamRevision", "ff47174fd6e94c051eaf6c0a0f3f4745a6b0e885");
        result.addProperty("externalReplayEngineRequired", false);
        return result;
    }
}
