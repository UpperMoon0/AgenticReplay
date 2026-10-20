package com.replaymod.agent;

import com.google.gson.*;
import java.util.*;

/** Explicit cinematic motion in output milliseconds, independent of packet arrival time. */
public final class ExportEntityTrack {
    public record Pose(double time, double x, double y, double z, double yaw, double pitch, String pose) {}
    private final int entityId;
    private final boolean elytra;
    private final List<Pose> poses;
    private ExportEntityTrack(int entityId, List<Pose> poses, boolean elytra) {
        this.elytra = elytra;
        this.entityId = entityId;
        this.poses = List.copyOf(poses);
    }
    public int entityId() { return entityId; }
    public boolean elytra() { return elytra; }
    public int flightTicks(double time) {
        if (!"fall_flying".equals(sample(time).pose())) return 0;
        double start = poses.get(0).time();
        String previous = null;
        for (Pose p : poses) {
            if (p.time() > Math.max(time, poses.get(0).time())) break;
            if ("fall_flying".equals(p.pose()) && !"fall_flying".equals(previous)) start = p.time();
            previous = p.pose();
        }
        return (int)Math.min(1728001, Math.max(0, (time-start)/50) + 1);
    }
    public static List<ExportEntityTrack> parse(JsonArray input) {
        if (input == null) return List.of();
        if (input.size() > 16) throw new IllegalArgumentException("At most 16 entity tracks");
        List<ExportEntityTrack> tracks = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        int total = 0;
        for (JsonElement entry : input) {
            JsonObject track = entry.getAsJsonObject();
            fields(track, Set.of("entityId", "poses"), Set.of("elytra"));
            boolean elytra = false;
            if (track.has("elytra")) {
                var v = track.get("elytra");
                if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("elytra must be boolean");
                elytra = v.getAsBoolean();
            }
            double id = finite(track, "entityId");
            if (id != Math.rint(id) || id < Integer.MIN_VALUE || id > Integer.MAX_VALUE || !ids.add((int) id))
                throw new IllegalArgumentException("Unique integer entityId required");
            JsonArray samples = track.getAsJsonArray("poses");
            if (samples == null || samples.size() < 2 || (total += samples.size()) > 512)
                throw new IllegalArgumentException("2 or more poses per track; at most 512 total");
            List<Pose> poses = new ArrayList<>();
            double last = -1;
            String authoredPose = null;
            for (JsonElement sample : samples) {
                JsonObject p = sample.getAsJsonObject();
                fields(p, Set.of("time", "x", "y", "z", "yaw", "pitch"), Set.of("pose"));
                if (p.has("pose")) {
                    var v = p.get("pose");
                    if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString() || !Set.of("standing", "fall_flying").contains(v.getAsString()))
                        throw new IllegalArgumentException("pose must be standing or fall_flying");
                    authoredPose = v.getAsString();
                }
                Pose pose = new Pose(finite(p, "time"), finite(p, "x"), finite(p, "y"),
                        finite(p, "z"), finite(p, "yaw"), finite(p, "pitch"), authoredPose);
                if (pose.time < 0 || pose.time > 86400000 || pose.time <= last ||
                        Math.abs(pose.x) > 29999984 || Math.abs(pose.z) > 29999984 ||
                        Math.abs(pose.y) > 20000000 || Math.abs(pose.yaw) > 1000000 || Math.abs(pose.pitch) > 90)
                    throw new IllegalArgumentException("Invalid pose bounds or non-increasing time");
                poses.add(pose); last = pose.time;
            }
            tracks.add(new ExportEntityTrack((int) id, poses, elytra));
        }
        return List.copyOf(tracks);
    }
    public Pose sample(double time) {
        if (!Double.isFinite(time)) throw new IllegalArgumentException("Finite output time required");
        if (time <= poses.get(0).time) return poses.get(0);
        if (time >= poses.get(poses.size()-1).time) return poses.get(poses.size()-1);
        int lo = 0, hi = poses.size()-1;
        while (hi-lo > 1) { int mid = (lo+hi)/2; if (poses.get(mid).time <= time) lo = mid; else hi = mid; }
        Pose a = poses.get(lo), b = poses.get(hi);
        double f = (time-a.time)/(b.time-a.time);
        double turn = ((b.yaw-a.yaw) % 360 + 540) % 360 - 180;
        return new Pose(time, lerp(a.x,b.x,f), lerp(a.y,b.y,f), lerp(a.z,b.z,f),
                a.yaw+turn*f, lerp(a.pitch,b.pitch,f), a.pose);
    }
    private static double lerp(double a, double b, double f) { return a+(b-a)*f; }
    private static void fields(JsonObject p, Set<String> required, Set<String> optional) {
        Set<String> allowed = new HashSet<>(required); allowed.addAll(optional);
        if (!p.keySet().containsAll(required) || !allowed.containsAll(p.keySet())) throw new IllegalArgumentException("Expected fields: " + allowed);
    }
    private static double finite(JsonObject p, String key) {
        JsonElement v = p.get(key);
        if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber() || !Double.isFinite(v.getAsDouble()))
            throw new IllegalArgumentException(key + " must be a finite number");
        return v.getAsDouble();
    }
}
