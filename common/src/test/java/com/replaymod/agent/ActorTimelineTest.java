package com.replaymod.agent;

import com.google.gson.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ActorTimelineTest {
    private static JsonArray steps(String json) { return JsonParser.parseString(json).getAsJsonArray(); }
    private static final class Port implements ActorTimeline.Port {
        double x, from;
        int swings;
        public void begin(ActorTimeline.Step step) { from = x; if (step.action().equals("swing")) swings++; }
        public void apply(ActorTimeline.Step step, double progress) {
            if (step.action().equals("move")) x = from + (step.params().get("x").getAsDouble() - from) * progress;
        }
    }
    @Test public void independentConcurrentCharactersCompleteOnTheirOwnTicks() {
        Port alice = new Port(), bob = new Port();
        ActorTimeline a = new ActorTimeline(alice), b = new ActorTimeline(bob);
        a.start(ActorTimeline.parse(steps("[{\"action\":\"move\",\"x\":8,\"y\":0,\"z\":0,\"ticks\":4}]")));
        b.start(ActorTimeline.parse(steps("[{\"action\":\"move\",\"x\":-3,\"y\":0,\"z\":0,\"ticks\":3},{\"action\":\"swing\"}]")));
        a.tick(); b.tick(); assertEquals(2, alice.x, 0); assertEquals(-1, bob.x, 0);
        a.stop("Only Alice stopped");
        for (int i = 0; i < 3; i++) { a.tick(); b.tick(); }
        assertEquals(2, alice.x, 0); assertEquals(-3, bob.x, 0); assertEquals(1, bob.swings);
        assertEquals("cancelled", a.status().get("state").getAsString());
        assertEquals("succeeded", b.status().get("state").getAsString());
    }
    @Test public void noExtraTickBetweenStepsAndNoRepeatedInstantActions() {
        Port port = new Port(); ActorTimeline timeline = new ActorTimeline(port);
        timeline.start(ActorTimeline.parse(steps("[{\"action\":\"wait\",\"ticks\":2},{\"action\":\"swing\"},{\"action\":\"swing\"}]")));
        timeline.tick(); timeline.tick(); assertEquals(0, port.swings);
        timeline.tick(); assertEquals(1, port.swings);
        timeline.tick(); timeline.tick(); assertEquals(2, port.swings); assertFalse(timeline.active());
    }
    @Test public void parserCopiesInputAndValidatesWholePlanBeforeStart() {
        JsonArray input = steps("[{\"action\":\"move\",\"x\":4,\"y\":0,\"z\":0}]");
        var plan = ActorTimeline.parse(input); input.get(0).getAsJsonObject().addProperty("x", 999);
        assertEquals(4, plan.get(0).params().get("x").getAsDouble(), 0);
        reject("[{\"action\":\"swing\"},{\"action\":\"spawn\"}]");
    }
    @Test public void rejectsMalformedOrUnboundedScripts() {
        for (String invalid : List.of("[]", "[1]", "[{\"action\":\"wait\",\"ticks\":0}]",
                "[{\"action\":\"wait\",\"ticks\":1201}]", "[{\"action\":\"wait\",\"ticks\":1.5}]",
                "[{\"action\":\"look\",\"yaw\":0,\"pitch\":91}]", "[{\"action\":\"wait\",\"actorId\":\"bob\"}]",
                "[{\"action\":\"pose\",\"pose\":\"invalid\"}]", "[{\"action\":\"swing\",\"ticks\":2}]",
                "[{\"action\":\"equip\",\"slot\":\"head\",\"item\":\"minecraft:air\",\"count\":0}]",
                "[{\"action\":\"move\",\"x\":30000000,\"y\":0,\"z\":0}]")) reject(invalid);
        JsonArray longPlan = new JsonArray();
        for (int i = 0; i < 11; i++) longPlan.add(JsonParser.parseString("{\"action\":\"wait\",\"ticks\":1200}"));
        try { ActorTimeline.parse(longPlan); fail(); } catch (IllegalArgumentException expected) { }
    }
    @Test public void failureIsLocalAndDoesNotPreventOtherActorsFromTicking() {
        ActorTimeline broken = new ActorTimeline(new ActorTimeline.Port() {
            public void begin(ActorTimeline.Step step) { throw new IllegalStateException("Actor disappeared"); }
            public void apply(ActorTimeline.Step step, double progress) { fail(); }
        });
        Port good = new Port(); ActorTimeline healthy = new ActorTimeline(good);
        var plan = ActorTimeline.parse(steps("[{\"action\":\"swing\"}]"));
        broken.start(plan); healthy.start(plan); broken.tick(); healthy.tick();
        assertEquals("failed", broken.status().get("state").getAsString()); assertEquals(1, good.swings);
    }
    @Test public void refusesReplacingActivePlanAndAllowsRestartAfterCompletion() {
        ActorTimeline timeline = new ActorTimeline(new Port());
        var plan = ActorTimeline.parse(steps("[{\"action\":\"wait\"}]")); timeline.start(plan);
        String first = timeline.status().get("jobId").getAsString();
        try { timeline.start(plan); fail(); } catch (IllegalStateException expected) { }
        timeline.tick(); timeline.start(plan); assertNotEquals(first, timeline.status().get("jobId").getAsString());
    }
    private static void reject(String json) {
        try { ActorTimeline.parse(steps(json)); fail("Accepted " + json); } catch (IllegalArgumentException expected) { }
    }
}
