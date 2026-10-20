package com.replaymod.agent;

import com.google.gson.*;
import java.util.*;

/** Explicit cinematic motion in output milliseconds, independent of packet arrival time. */
public final class ExportEntityTrack {
    public record Pose(double time, double x, double y, double z, double yaw, double pitch) {}
    private final int entityId;
    private final List<Pose> poses;
    private ExportEntityTrack(int entityId, List<Pose> poses) {
        this.entityId = entityId;
        this.poses = List.copyOf(poses);
    }
    public int entityId() { return entityId; }
    public static List<ExportEntityTrack> parse(JsonArray input) {
        if (input == null) return List.of();
        if (input.size() > 16) throw new IllegalArgumentException("At most 16 entity tracks");
        List<ExportEntityTrack> tracks = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        int total = 0;
        for (JsonElement entry : input) {
            JsonObject track = entry.getAsJsonObject();
            fields(track, Set.of("entityId", "poses"));
            double id = finite(track, "entityId");
            if (id != Math.rint(id) || id < Integer.MIN_VALUE || id > Integer.MAX_VALUE || !ids.add((int) id))
                throw new IllegalArgumentException("Unique integer entityId required");
            JsonArray samples = track.getAsJsonArray("poses");
            if (samples == null || samples.size() < 2 || (total += samples.size()) > 512)
                throw new IllegalArgumentException("2 or more poses per track; at most 512 total");
            List<Pose> poses = new ArrayList<>();
            double last = -1;
            for (JsonElement sample : samples) {
                JsonObject p = sample.getAsJsonObject();
                fields(p, Set.of("time", "x", "y", "z", "yaw", "pitch"));
                Pose pose = new Pose(finite(p, "time"), finite(p, "x"), finite(p, "y"),
                        finite(p, "z"), finite(p, "yaw"), finite(p, "pitch"));
                if (pose.time < 0 || pose.time > 86400000 || pose.time <= last ||
                        Math.abs(pose.x) > 29999984 || Math.abs(pose.z) > 29999984 ||
                        Math.abs(pose.y) > 20000000 || Math.abs(pose.yaw) > 1000000 || Math.abs(pose.pitch) > 90)
                    throw new IllegalArgumentException("Invalid pose bounds or non-increasing time");
                poses.add(pose); last = pose.time;
            }
            tracks.add(new ExportEntityTrack((int) id, poses));
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
                a.yaw+turn*f, lerp(a.pitch,b.pitch,f));
    }
    private static double lerp(double a, double b, double f) { return a+(b-a)*f; }
    private static void fields(JsonObject p, Set<String> allowed) {
        if (!p.keySet().equals(allowed)) throw new IllegalArgumentException("Expected fields: " + allowed);
    }
    private static double finite(JsonObject p, String key) {
        JsonElement v = p.get(key);
        if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber() || !Double.isFinite(v.getAsDouble()))
            throw new IllegalArgumentException(key + " must be a finite number");
        return v.getAsDouble();
    }
}
