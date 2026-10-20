package com.replaymod.agent;

import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;

/** Prepare every character before starting any: a bad script cannot partially start a scene. */
final class ActorScene {
    record Target(ActorTimeline timeline, Consumer<List<ActorTimeline.Step>> validate) {}
    static void start(JsonObject scripts, Map<String, Target> actors) {
        if (scripts.size() == 0 || scripts.size() > 32) throw new IllegalArgumentException("scripts must contain 1..32 actor IDs");
        Map<Target, List<ActorTimeline.Step>> prepared = new LinkedHashMap<>();
        for (var entry : scripts.entrySet()) {
            Target target = actors.get(entry.getKey());
            if (target == null) throw new IllegalArgumentException("Unknown or unavailable actor: " + entry.getKey());
            if (target.timeline().active()) throw new IllegalStateException("Actor script active: " + entry.getKey());
            if (!entry.getValue().isJsonArray()) throw new IllegalArgumentException("Actor script must be an array");
            var plan = ActorTimeline.parse(entry.getValue().getAsJsonArray());
            target.validate().accept(plan);
            prepared.put(target, plan);
        }
        prepared.forEach((target, plan) -> target.timeline().start(plan));
    }
}
