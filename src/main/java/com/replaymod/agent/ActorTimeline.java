package com.replaymod.agent;

import com.google.gson.*;
import java.util.*;

/** Independent, deterministic tick clocks for cinematic actors. No keyboard ownership. */
final class ActorTimeline {
    record Step(String action, int ticks, JsonObject params) {}
    interface Port {
        void begin(Step step);
        void apply(Step step, double progress);
    }
    private final Port port;
    private List<Step> plan = List.of();
    private String jobId, state = "idle", error;
    private int index, elapsed;

    ActorTimeline(Port port) { this.port = port; }
    static List<Step> parse(JsonArray steps) {
        if (steps == null || steps.isEmpty() || steps.size() > 128)
            throw new IllegalArgumentException("steps must contain 1..128 actions");
        List<Step> plan = new ArrayList<>();
        int total = 0;
        for (JsonElement value : steps) {
            if (!value.isJsonObject()) throw new IllegalArgumentException("Each step must be an object");
            JsonObject p = value.getAsJsonObject().deepCopy();
            String action = text(p, "action");
            int ticks = integer(p, "ticks", 1, 1, 1200);
            Set<String> fields = new HashSet<>(Set.of("action", "ticks"));
            switch (action) {
                case "move": case "teleport":
                    fields.addAll(Set.of("x", "y", "z"));
                    position(p);
                    if (action.equals("move")) {
                        fields.addAll(Set.of("yaw", "pitch"));
                        if (p.has("yaw") && Math.abs(number(p, "yaw")) > 1000000) throw new IllegalArgumentException("Invalid yaw");
                        if (p.has("pitch") && Math.abs(number(p, "pitch")) > 90) throw new IllegalArgumentException("Invalid pitch");
                    }
                    break;
                case "look":
                    fields.addAll(Set.of("yaw", "pitch"));
                    if (Math.abs(number(p, "yaw")) > 1000000) throw new IllegalArgumentException("yaw must be -1000000..1000000");
                    if (Math.abs(number(p, "pitch")) > 90) throw new IllegalArgumentException("pitch must be -90..90");
                    break;
                case "pose":
                    fields.addAll(Set.of("pose", "sprinting"));
                    if (!Set.of("standing", "crouching", "swimming", "fall_flying", "sleeping").contains(text(p, "pose")))
                        throw new IllegalArgumentException("Unsupported pose");
                    if (p.has("sprinting")) bool(p, "sprinting");
                    break;
                case "equip":
                    fields.addAll(Set.of("slot", "item", "count", "nbt"));
                    if (!Set.of("mainhand", "offhand", "head", "chest", "legs", "feet").contains(text(p, "slot")))
                        throw new IllegalArgumentException("Unsupported equipment slot");
                    text(p, "item"); integer(p, "count", 1, 1, 64);
                    if (p.has("nbt")) text(p, "nbt");
                    break;
                case "swing":
                    fields.add("hand");
                    if (p.has("hand") && !Set.of("mainhand", "offhand").contains(text(p, "hand")))
                        throw new IllegalArgumentException("hand must be mainhand or offhand");
                    break;
                case "wait": case "hurt": break;
                default: throw new IllegalArgumentException("Unknown actor action: " + action);
            }
            if (!fields.containsAll(p.keySet())) throw new IllegalArgumentException("Unknown " + action + " field");
            if (!Set.of("move", "look", "wait").contains(action) && ticks != 1)
                throw new IllegalArgumentException(action + " takes one tick");
            total += ticks;
            if (total > 12000) throw new IllegalArgumentException("Plan exceeds 12000 ticks");
            plan.add(new Step(action, ticks, p));
        }
        return List.copyOf(plan);
    }
    void start(List<Step> steps) {
        if (active()) throw new IllegalStateException("Actor script active; stop it before replacing it");
        if (steps.isEmpty()) throw new IllegalArgumentException("Empty plan");
        plan = steps; index = elapsed = 0; error = null;
        jobId = UUID.randomUUID().toString(); state = "queued";
    }
    boolean active() { return state.equals("queued") || state.equals("running"); }
    void tick() {
        if (!active()) return;
        try {
            state = "running";
            Step step = plan.get(index);
            if (elapsed == 0) port.begin(step);
            port.apply(step, (double) ++elapsed / step.ticks());
            if (elapsed == step.ticks()) {
                elapsed = 0;
                if (++index == plan.size()) state = "succeeded";
            }
        } catch (RuntimeException failure) { fail(failure.getMessage()); }
    }
    void stop(String reason) { if (active()) { state = "cancelled"; error = reason; } }
    void fail(String reason) { state = "failed"; error = reason; }
    JsonObject status() {
        JsonObject out = new JsonObject();
        out.addProperty("jobId", jobId); out.addProperty("state", state); out.addProperty("error", error);
        out.addProperty("step", index); out.addProperty("steps", plan.size()); out.addProperty("stepTicks", elapsed);
        return out;
    }
    static String text(JsonObject p, String key) {
        JsonElement v = p.get(key);
        if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString() || v.getAsString().isBlank())
            throw new IllegalArgumentException(key + " must be a nonempty string");
        return v.getAsString();
    }
    static double number(JsonObject p, String key) {
        JsonElement v = p.get(key);
        if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber() || !Double.isFinite(v.getAsDouble()))
            throw new IllegalArgumentException(key + " must be a finite number");
        return v.getAsDouble();
    }
    static int integer(JsonObject p, String key, int fallback, int min, int max) {
        double v = p.has(key) ? number(p, key) : fallback;
        if (v != Math.rint(v) || v < min || v > max) throw new IllegalArgumentException(key + " must be " + min + ".." + max);
        return (int) v;
    }
    static boolean bool(JsonObject p, String key) {
        JsonElement v = p.get(key);
        if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException(key + " must be boolean");
        return v.getAsBoolean();
    }
    static void position(JsonObject p) {
        if (Math.abs(number(p, "x")) > 29999984 || Math.abs(number(p, "z")) > 29999984 || Math.abs(number(p, "y")) > 20000000)
            throw new IllegalArgumentException("Position outside Minecraft bounds");
    }
}
