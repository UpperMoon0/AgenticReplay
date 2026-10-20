package com.replaymod.agent;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import java.util.Set;

/** Bounded native graphics controls; optional mods are accessed through public APIs. */
final class NativeGraphicsControls {
    static void validate(JsonObject p) {
        for (String key : p.keySet()) {
            if (!Set.of("clouds", "dhEnabled", "shadersEnabled", "dhDistance").contains(key))
                throw new IllegalArgumentException("Unknown graphics setting: " + key);
            var value = p.get(key);
            if (!value.isJsonPrimitive()) throw new IllegalArgumentException("Primitive graphics setting required");
            if (key.equals("dhDistance")) {
                if (!value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Integer DH distance required");
                double distance = value.getAsDouble();
                if (distance < 16 || distance > 256 || distance != Math.floor(distance))
                    throw new IllegalArgumentException("DH distance must be an integer in 16..256");
            } else if (!value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Boolean graphics setting required");
        }
    }
    private static Class<?> optional(String name) {
        try { return Class.forName(name); } catch (ClassNotFoundException e) { return null; }
    }
    @FunctionalInterface interface ReadyGraphics { JsonObject apply() throws Exception; }
    @FunctionalInterface interface ShaderStateChange { void apply(boolean enabled) throws Exception; }
    static JsonObject whenReady(boolean loading, ReadyGraphics action) throws Exception {
        if (loading) throw new IllegalStateException("Wait for player.state.loading=false before accessing graphics controls");
        return action.apply();
    }
    static void applyShaderState(boolean current, boolean requested, ShaderStateChange change) throws Exception {
        if (current != requested) change.apply(requested);
    }
    static JsonObject apply(MinecraftClient mc, JsonObject p) throws Exception {
        validate(p);
        return whenReady(mc.getOverlay() != null, () -> applyReady(mc, p));
    }
    private static JsonObject applyReady(MinecraftClient mc, JsonObject p) throws Exception {
        Class<?> dh = optional("com.seibel.distanthorizons.api.DhApi$Delayed");
        Class<?> iris = optional("net.irisshaders.iris.api.v0.IrisApi");
        if ((p.has("dhEnabled") || p.has("dhDistance")) && dh == null)
            throw new IllegalStateException("Distant Horizons public API is unavailable");
        if (p.has("shadersEnabled") && iris == null) throw new IllegalStateException("Iris/Oculus public API is unavailable");
        Object graphics = null;
        Class<?> dhValue = null;
        Class<?> dhGraphics = null;
        if (dh != null) {
            Object config = dh.getField("configs").get(null);
            if (config == null) throw new IllegalStateException("Distant Horizons is not initialized");
            graphics = Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig").getMethod("graphics").invoke(config);
            dhGraphics = Class.forName("com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig");
            dhValue = Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue");
        }
        if (p.has("clouds")) mc.options.getCloudRenderMode().setValue(p.get("clouds").getAsBoolean() ? CloudRenderMode.FANCY : CloudRenderMode.OFF);
        JsonObject out = new JsonObject();
        out.addProperty("dhAvailable", dh != null); out.addProperty("shaderApiAvailable", iris != null);
        if (dh != null) {
            for (String name : Set.of("renderingEnabled", "chunkRenderDistance")) {
                Object value = dhGraphics.getMethod(name).invoke(graphics);
                String key = name.equals("renderingEnabled") ? "dhEnabled" : "dhDistance";
                if (p.has(key)) {
                    Object requested = key.equals("dhEnabled") ? (Object)p.get(key).getAsBoolean() : p.get(key).getAsInt();
                    if (!Boolean.TRUE.equals(dhValue.getMethod("setValue", Object.class).invoke(value, requested)))
                        throw new IllegalStateException("DH rejected config override: " + key);
                }
                Object actual = dhValue.getMethod("getValue").invoke(value);
                if (actual instanceof Boolean b) out.addProperty(key, b); else out.addProperty(key, (Number)actual);
            }
        }
        if (iris != null) {
            Object api = iris.getMethod("getInstance").invoke(null);
            Object config = iris.getMethod("getConfig").invoke(api);
            Class<?> configType = Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig");
            if (p.has("shadersEnabled")) {
                boolean current = (Boolean)configType.getMethod("areShadersEnabled").invoke(config);
                applyShaderState(current, p.get("shadersEnabled").getAsBoolean(),
                    enabled -> configType.getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, enabled));
            }
            out.addProperty("shadersEnabled", (Boolean)configType.getMethod("areShadersEnabled").invoke(config));
        }
        out.addProperty("clouds", mc.options.getCloudRenderMode().getValue() != CloudRenderMode.OFF);
        return out;
    }
}
