package com.replaymod.agent;

import com.google.gson.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ActorSceneTest {
    private static ActorTimeline timeline() { return new ActorTimeline(new ActorTimeline.Port() {
        public void begin(ActorTimeline.Step step) { }
        public void apply(ActorTimeline.Step step, double progress) { }
    }); }
    private static JsonObject scripts(String json) { return JsonParser.parseString(json).getAsJsonObject(); }
    @Test public void invalidLaterCharacterDoesNotStartEarlierCharacter() {
        ActorTimeline a = timeline(), b = timeline();
        var actors = Map.of("a", new ActorScene.Target(a, plan -> {}), "b", new ActorScene.Target(b, plan -> {}));
        for (String invalid : List.of("{\"a\":[{\"action\":\"wait\"}],\"b\":[{\"action\":\"bad\"}]}",
                "{\"a\":[{\"action\":\"wait\"}],\"missing\":[{\"action\":\"wait\"}]}")) {
            try { ActorScene.start(scripts(invalid), actors); fail(); } catch (IllegalArgumentException expected) { }
            assertFalse(a.active()); assertFalse(b.active());
        }
    }
    @Test public void resourceValidationAndBusyActorRejectWholeScene() {
        ActorTimeline a = timeline(), b = timeline();
        JsonObject scene = scripts("{\"a\":[{\"action\":\"wait\"}],\"b\":[{\"action\":\"wait\"}]}");
        try { ActorScene.start(scene, Map.of("a", new ActorScene.Target(a, plan -> {}),
                "b", new ActorScene.Target(b, plan -> { throw new IllegalArgumentException("Unknown item"); }))); fail();
        } catch (IllegalArgumentException expected) { }
        assertFalse(a.active()); assertFalse(b.active());
        b.start(ActorTimeline.parse(scene.getAsJsonArray("b")));
        try { ActorScene.start(scene, Map.of("a", new ActorScene.Target(a, plan -> {}), "b", new ActorScene.Target(b, plan -> {}))); fail();
        } catch (IllegalStateException expected) { }
        assertFalse(a.active()); assertEquals("queued", b.status().get("state").getAsString());
    }
    @Test public void validCharactersShareStartTickButKeepSeparateJobIds() {
        ActorTimeline a = timeline(), b = timeline();
        ActorScene.start(scripts("{\"a\":[{\"action\":\"wait\",\"ticks\":2}],\"b\":[{\"action\":\"wait\",\"ticks\":1}]}"),
                Map.of("a", new ActorScene.Target(a, plan -> {}), "b", new ActorScene.Target(b, plan -> {})));
        assertEquals("queued", a.status().get("state").getAsString()); assertEquals("queued", b.status().get("state").getAsString());
        assertNotEquals(a.status().get("jobId"), b.status().get("jobId"));
        a.tick(); b.tick(); assertTrue(a.active()); assertFalse(b.active());
    }
}
