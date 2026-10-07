package com.replaymod.agent;

import com.google.gson.*;
import java.util.*;

/** Validate the entire plan before any native player action is performed. */
final class PlayerActionPlan {
    static final int MAX_STEPS = 128, MAX_STEP_TICKS = 1200, MAX_TOTAL_TICKS = 12000;
    static final Set<String> KEYS = Set.of("forward", "back", "left", "right", "jump", "sneak", "sprint", "attack", "use");
    record Step(String action, int ticks, JsonObject params) {}

    static List<Step> parse(JsonArray steps) {
        if (steps == null || steps.isEmpty() || steps.size() > MAX_STEPS)
            throw new IllegalArgumentException("steps must contain 1..128 actions");
        List<Step> out = new ArrayList<>();
        int total = 0;
        for (JsonElement element : steps) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Each step must be an object");
            JsonObject p = element.getAsJsonObject().deepCopy();
            String action = text(p, "action");
            int ticks = integer(p, "ticks", action.equals("input") || action.equals("wait") ? 20 : 1, 1, MAX_STEP_TICKS);
            Set<String> fields = new HashSet<>(Set.of("action", "ticks"));
            switch (action) {
                case "input": {
                    fields.add("keys");
                    JsonObject keys = object(p, "keys");
                    if (keys.size() == 0) throw new IllegalArgumentException("keys must not be empty");
                    for (var entry : keys.entrySet()) {
                        if (!KEYS.contains(entry.getKey())) throw new IllegalArgumentException("Unknown input: " + entry.getKey());
                        bool(keys, entry.getKey());
                    }
                    break;
                }
                case "look":
                    fields.addAll(Set.of("yaw", "pitch"));
                    number(p, "yaw");
                    double pitch = number(p, "pitch");
                    if (pitch < -90 || pitch > 90) throw new IllegalArgumentException("pitch must be -90..90");
                    break;
                case "select": fields.add("slot"); integer(p, "slot", -1, 0, 8); break;
                case "fly": fields.add("flying"); bool(p, "flying"); break;
                case "use": case "attack": case "dismount": case "close_screen": break;
                case "click_slot":
                    fields.addAll(Set.of("syncId", "slotId", "button", "clickAction"));
                    integer(p, "syncId", -1, 0, Integer.MAX_VALUE);
                    integer(p, "slotId", -1, 0, 1024);
                    int button = integer(p, "button", 0, 0, 8);
                    String click = p.has("clickAction") ? text(p, "clickAction") : "PICKUP";
                    if (!Set.of("PICKUP", "QUICK_MOVE", "SWAP", "THROW").contains(click))
                        throw new IllegalArgumentException("Unsupported clickAction");
                    if (!click.equals("SWAP") && button > 1) throw new IllegalArgumentException("button must be 0 or 1");
                    p.addProperty("clickAction", click);
                    break;
                case "wait": case "assert":
                    fields.add("until");
                    if (p.has("until")) validateCondition(object(p, "until"));
                    else if (action.equals("assert")) throw new IllegalArgumentException("assert requires until");
                    break;
                default: throw new IllegalArgumentException("Unknown player action: " + action);
            }
            for (String key : p.keySet()) if (!fields.contains(key)) throw new IllegalArgumentException("Unknown " + action + " field: " + key);
            if (Set.of("select", "fly", "use", "attack", "close_screen", "click_slot", "assert").contains(action) && ticks != 1)
                throw new IllegalArgumentException(action + " takes one tick; use input for held actions");
            total += ticks;
            if (total > MAX_TOTAL_TICKS) throw new IllegalArgumentException("Plan exceeds 12000 ticks");
            out.add(new Step(action, ticks, p));
        }
        return List.copyOf(out);
    }

    private static void validateCondition(JsonObject p) {
        if (p.size() == 0) throw new IllegalArgumentException("until must not be empty");
        for (String key : p.keySet()) switch (key) {
            case "riding": case "onGround": case "flying": bool(p, key); break;
            case "slot": integer(p, key, -1, 0, 8); break;
            case "screen": case "dimension": text(p, key); break;
            case "position": {
                JsonObject pos = object(p, key);
                if (!Set.of("x", "y", "z", "tolerance").containsAll(pos.keySet())) throw new IllegalArgumentException("Unknown position field");
                number(pos, "x"); number(pos, "y"); number(pos, "z");
                double tolerance = pos.has("tolerance") ? number(pos, "tolerance") : 0.5;
                if (tolerance < 0 || tolerance > 64) throw new IllegalArgumentException("tolerance must be 0..64");
                break;
            }
            default: throw new IllegalArgumentException("Unknown condition: " + key);
        }
    }

    static String text(JsonObject p, String key) {
        JsonElement v = p.get(key);
        if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString() || v.getAsString().isBlank())
            throw new IllegalArgumentException(key + " must be a nonempty string");
        return v.getAsString();
    }
    static boolean bool(JsonObject p, String key) {
        JsonElement v = p.get(key);
        if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(key + " must be boolean");
        return v.getAsBoolean();
    }
    static double number(JsonObject p, String key) {
        JsonElement v = p.get(key);
        if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber() || !Double.isFinite(v.getAsDouble()))
            throw new IllegalArgumentException(key + " must be a finite number");
        return v.getAsDouble();
    }
    static int integer(JsonObject p, String key, int fallback, int min, int max) {
        double v = p.has(key) ? number(p, key) : fallback;
        if (v != Math.rint(v) || v < min || v > max) throw new IllegalArgumentException(key + " must be an integer in " + min + ".." + max);
        return (int) v;
    }
    static JsonObject object(JsonObject p, String key) {
        JsonElement v = p.get(key);
        if (v == null || !v.isJsonObject()) throw new IllegalArgumentException(key + " must be an object");
        return v.getAsJsonObject();
    }
}
