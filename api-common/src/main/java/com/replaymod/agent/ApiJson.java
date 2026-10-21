package com.replaymod.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Strict JSON primitives shared by both runtimes; never coerce strings/numbers/booleans. */
public final class ApiJson {
    private ApiJson() {}
    public static String string(JsonObject p, String key) {
        JsonElement value = p.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException(key + " must be a string");
        return value.getAsString();
    }
    public static String text(JsonObject p, String key) {
        String value = string(p, key);
        boolean blank = true;
        for (int i = 0; i < value.length(); ) {
            int codepoint = value.codePointAt(i);
            if (!Character.isWhitespace(codepoint)) { blank = false; break; }
            i += Character.charCount(codepoint);
        }
        if (blank) throw new IllegalArgumentException(key + " must be a nonempty string");
        return value;
    }
    public static boolean bool(JsonObject p, String key) {
        JsonElement value = p.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException(key + " must be boolean");
        return value.getAsBoolean();
    }
    public static boolean bool(JsonObject p, String key, boolean fallback) { return p.has(key) ? bool(p,key) : fallback; }
    public static double number(JsonObject p, String key) {
        JsonElement value = p.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException(key + " must be a finite number");
        return finite(value.getAsDouble(), key);
    }
    public static double number(JsonObject p, String key, double fallback) { return p.has(key) ? number(p,key) : fallback; }
    public static int integer(JsonObject p, String key, int fallback, int min, int max) {
        return integer(number(p,key,fallback), key, min, max);
    }
    public static double finite(double value, String key) {
        if (Double.isNaN(value) || Double.isInfinite(value)) throw new IllegalArgumentException(key + " must be a finite number");
        return value;
    }
    public static int integer(double value, String key, int min, int max) {
        finite(value, key);
        if (value != Math.rint(value) || value < min || value > max)
            throw new IllegalArgumentException(key + " must be an integer in " + min + ".." + max);
        return (int) value;
    }
    public static JsonObject object(JsonObject p, String key) {
        JsonElement value = p.get(key);
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(key + " must be an object");
        return value.getAsJsonObject();
    }
}
