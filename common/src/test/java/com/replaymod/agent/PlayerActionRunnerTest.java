package com.replaymod.agent;

import com.google.gson.*;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class PlayerActionRunnerTest {
    private static class FakePort implements PlayerActionRunner.Port {
        List<String> events = new ArrayList<>();
        boolean held, valid = true, matched, failBegin, paused;
        public void guard() { if (!valid) throw new IllegalStateException("World changed"); }
        public boolean paused() { return paused; }
        public void begin(PlayerActionPlan.Step s) { events.add("begin:" + s.action()); if (failBegin) throw new IllegalStateException("Native failure"); }
        public void apply(PlayerActionPlan.Step s, int tick) { events.add(s.action() + ":" + tick); if (s.action().equals("input")) held = true; }
        public boolean matches(JsonObject condition) { return matched; }
        public void release() { held = false; events.add("release"); }
    }
    private List<PlayerActionPlan.Step> plan(String json) { return PlayerActionPlan.parse(JsonParser.parseString(json).getAsJsonArray()); }
    private String state(PlayerActionRunner runner) { return runner.status().get("state").getAsString(); }

    @Test public void screenRecoveryRunsWhilePauseStateIsStillSet() {
        var port = new FakePort(); port.paused = true;
        var runner = new PlayerActionRunner(port, () -> 0L);
        runner.start(plan("[{\"action\":\"close_screen\"}]"));
        runner.tick(); runner.tick();
        assertEquals("succeeded", state(runner));
        assertEquals(List.of("begin:close_screen", "close_screen:1", "release"), port.events);
    }
    @Test public void pausedClientRejectsEveryOtherActionBeforeQueueing() {
        for (String step : List.of(
                "{\"action\":\"input\",\"keys\":{\"forward\":true}}",
                "{\"action\":\"look\",\"yaw\":0,\"pitch\":0}",
                "{\"action\":\"select\",\"slot\":0}",
                "{\"action\":\"use\"}", "{\"action\":\"attack\"}",
                "{\"action\":\"fly\",\"flying\":true}", "{\"action\":\"dismount\"}",
                "{\"action\":\"click_slot\",\"syncId\":1,\"slotId\":0}",
                "{\"action\":\"wait\"}", "{\"action\":\"assert\",\"until\":{\"screen\":\"none\"}}")) {
            var port = new FakePort(); port.paused = true;
            var runner = new PlayerActionRunner(port, () -> 0L);
            try { runner.start(plan("[" + step + "]")); fail("Accepted: " + step); }
            catch (IllegalStateException expected) { assertEquals("Client is paused", expected.getMessage()); }
            assertEquals("idle", state(runner)); assertTrue(port.events.isEmpty());
        }
    }
    @Test public void pauseDuringGameplayReleasesInputs() {
        var port = new FakePort(); var runner = new PlayerActionRunner(port, () -> 0L);
        runner.start(plan("[{\"action\":\"input\",\"keys\":{\"forward\":true},\"ticks\":3}]"));
        runner.tick(); assertTrue(port.held);
        port.paused = true; runner.tick();
        assertFalse(port.held); assertEquals("failed", state(runner));
        assertEquals("Client is paused", runner.status().get("error").getAsString());
        assertFalse(port.events.contains("input:2"));
    }
    @Test public void recoveryDoesNotBypassPauseOnNextSequenceStep() {
        var port = new FakePort(); port.paused = true;
        var runner = new PlayerActionRunner(port, () -> 0L);
        runner.start(plan("[{\"action\":\"close_screen\"},{\"action\":\"attack\"}]"));
        runner.tick(); runner.tick();
        assertEquals("failed", state(runner)); assertFalse(port.events.contains("begin:attack"));
        assertEquals("Client is paused", runner.status().get("error").getAsString());
    }
    @Test public void gameplayCanFollowRecoveryOnceClientUnpauses() {
        var port = new FakePort(); port.paused = true;
        var runner = new PlayerActionRunner(port, () -> 0L);
        runner.start(plan("[{\"action\":\"close_screen\"},{\"action\":\"attack\"}]"));
        runner.tick(); port.paused = false; runner.tick(); runner.tick();
        assertEquals("succeeded", state(runner)); assertTrue(port.events.contains("begin:attack"));
    }
    @Test public void recoveryStillRequiresTheOriginalLiveContext() {
        var port = new FakePort(); port.paused = true;
        var runner = new PlayerActionRunner(port, () -> 0L);
        runner.start(plan("[{\"action\":\"close_screen\"}]"));
        port.valid = false; runner.tick();
        assertEquals("failed", state(runner)); assertFalse(port.events.contains("begin:close_screen"));
        assertEquals("World changed", runner.status().get("error").getAsString());
    }

    @Test public void exactInputLeaseAndAutomaticRelease() {
        var port = new FakePort(); var runner = new PlayerActionRunner(port, () -> 0L);
        runner.start(plan("[{\"action\":\"input\",\"keys\":{\"forward\":true},\"ticks\":3}]"));
        assertEquals("queued", state(runner)); assertFalse(port.held);
        for (int i = 0; i < 3; i++) { runner.tick(); assertTrue(port.held); }
        runner.tick(); assertFalse(port.held); assertEquals("succeeded", state(runner));
        assertEquals(List.of("begin:input", "input:1", "input:2", "input:3", "release"), port.events);
        runner.tick(); assertEquals(5, port.events.size());
    }
    @Test public void transitionsReleaseBeforeNextStep() {
        var port = new FakePort(); var runner = new PlayerActionRunner(port, () -> 0L);
        runner.start(plan("[{\"action\":\"input\",\"keys\":{\"sneak\":true},\"ticks\":1},{\"action\":\"use\"}]"));
        runner.tick(); runner.tick(); runner.tick();
        assertEquals(List.of("begin:input", "input:1", "release", "begin:use", "use:1", "release"), port.events);
    }
    @Test public void stopAndPhysicalTakeoverReleaseAndRetainJob() {
        var port = new FakePort(); var runner = new PlayerActionRunner(port, () -> 0L);
        String id = runner.start(plan("[{\"action\":\"input\",\"keys\":{\"attack\":true}}]")).get("jobId").getAsString();
        runner.tick(); runner.stop("Physical input took control"); runner.tick();
        assertFalse(port.held); assertEquals("cancelled", state(runner));
        assertEquals(id, runner.status().get("jobId").getAsString());
        assertEquals("Physical input took control", runner.status().get("error").getAsString());
        int count = port.events.size(); runner.stop("Again"); assertEquals(count, port.events.size());
    }
    @Test public void contextLossReleasesWithoutExecutingNextAction() {
        var port = new FakePort(); var runner = new PlayerActionRunner(port, () -> 0L);
        runner.start(plan("[{\"action\":\"input\",\"keys\":{\"use\":true}},{\"action\":\"attack\"}]"));
        runner.tick(); port.valid = false; runner.tick();
        assertFalse(port.held); assertEquals("failed", state(runner)); assertFalse(port.events.contains("begin:attack"));
    }
    @Test public void nativeFailureReleasesAndRetainsError() {
        var port = new FakePort(); var runner = new PlayerActionRunner(port, () -> 0L);
        runner.start(plan("[{\"action\":\"use\"}]")); port.failBegin = true; runner.tick();
        assertEquals("failed", state(runner)); assertEquals("Native failure", runner.status().get("error").getAsString());
        assertEquals(List.of("begin:use", "release"), port.events);
    }
    @Test public void conditionalWaitDoesNotAdvanceUntilServerStateMatches() {
        var port = new FakePort(); var runner = new PlayerActionRunner(port, () -> 0L);
        runner.start(plan("[{\"action\":\"wait\",\"ticks\":5,\"until\":{\"riding\":true}},{\"action\":\"use\"}]"));
        runner.tick(); runner.tick(); assertFalse(port.events.contains("begin:use"));
        port.matched = true; runner.tick(); assertTrue(port.events.contains("begin:use")); runner.tick();
        assertEquals("succeeded", state(runner));
    }
    @Test public void conditionTimeoutFailsInsteadOfHidingFailedBoarding() {
        var port = new FakePort(); var runner = new PlayerActionRunner(port, () -> 0L);
        runner.start(plan("[{\"action\":\"wait\",\"ticks\":2,\"until\":{\"riding\":true}},{\"action\":\"use\"}]"));
        runner.tick(); runner.tick(); runner.tick();
        assertEquals("failed", state(runner)); assertFalse(port.events.contains("begin:use"));
        assertTrue(runner.status().get("error").getAsString().contains("timed out"));
    }
    @Test public void wallDeadlineBoundsStalledClient() {
        var port = new FakePort(); var clock = new AtomicLong(); var runner = new PlayerActionRunner(port, clock::get);
        runner.start(plan("[{\"action\":\"input\",\"keys\":{\"forward\":true},\"ticks\":1}]"));
        runner.tick(); clock.set(30101L); runner.tick();
        assertFalse(port.held); assertEquals("failed", state(runner));
    }
    @Test public void concurrentStartDoesNotReplaceRunningPlan() {
        var port = new FakePort(); var runner = new PlayerActionRunner(port, () -> 0L);
        String id = runner.start(plan("[{\"action\":\"wait\"}]")).get("jobId").getAsString();
        try { runner.start(plan("[{\"action\":\"attack\"}]")); fail(); } catch (IllegalStateException expected) {}
        assertEquals(id, runner.status().get("jobId").getAsString());
        runner.tick(); assertFalse(port.events.contains("begin:attack"));
    }
    @Test public void queuedJobCanBeCancelledBeforeItExecutes() {
        var port = new FakePort(); var runner = new PlayerActionRunner(port, () -> 0L);
        runner.start(plan("[{\"action\":\"attack\"}]")); runner.stop("cancelled"); runner.tick();
        assertEquals(List.of("release"), port.events);
    }
    @Test public void validatesAllStepsBeforeMutationAndRejectsBadTypes() {
        for (String bad : List.of(
            "[]", "[1]", "[{\"action\":\"teleport\"}]",
            "[{\"action\":\"input\",\"keys\":{\"forwad\":true}}]",
            "[{\"action\":\"input\",\"keys\":{\"forward\":\"true\"}}]",
            "[{\"action\":\"input\",\"keys\":{}}]",
            "[{\"action\":\"input\",\"keys\":{\"forward\":true},\"ticks\":0}]",
            "[{\"action\":\"wait\",\"ticks\":1201}]",
            "[{\"action\":\"wait\",\"ticks\":1.5}]",
            "[{\"action\":\"select\",\"slot\":9}]",
            "[{\"action\":\"select\",\"slot\":1,\"ticks\":2}]",
            "[{\"action\":\"look\",\"yaw\":0,\"pitch\":91}]",
            "[{\"action\":\"look\",\"yaw\":1e309,\"pitch\":0}]",
            "[{\"action\":\"fly\",\"flying\":1}]",
            "[{\"action\":\"use\",\"address\":\"wrong\"}]",
            "[{\"action\":\"click_slot\",\"slotId\":0}]",
            "[{\"action\":\"click_slot\",\"syncId\":1,\"slotId\":0,\"clickAction\":\"CLONE\"}]",
            "[{\"action\":\"click_slot\",\"syncId\":1,\"slotId\":0,\"button\":2}]",
            "[{\"action\":\"assert\"}]",
            "[{\"action\":\"wait\",\"until\":{}}]",
            "[{\"action\":\"wait\",\"until\":{\"riding\":\"true\"}}]",
            "[{\"action\":\"wait\",\"until\":{\"position\":{\"x\":0,\"y\":0,\"z\":0,\"tolerance\":-1}}}]",
            "[{\"action\":\"use\"},{\"action\":\"select\",\"slot\":-1}]")) {
            try { plan(bad); fail("Accepted: " + bad); } catch (IllegalArgumentException expected) {}
        }
    }
    @Test public void validatesCountAndAggregateTickBudget() {
        JsonArray array = new JsonArray();
        for (int i = 0; i < 129; i++) { JsonObject step = new JsonObject(); step.addProperty("action", "use"); array.add(step); }
        try { PlayerActionPlan.parse(array); fail(); } catch (IllegalArgumentException expected) {}
        array = new JsonArray();
        for (int i = 0; i < 11; i++) { JsonObject step = new JsonObject(); step.addProperty("action", "wait"); step.addProperty("ticks", 1200); array.add(step); }
        try { PlayerActionPlan.parse(array); fail(); } catch (IllegalArgumentException expected) {}
    }
    @Test public void highCoordinatesRemainExactAndRequestMutationDoesNotChangePlan() {
        var request = JsonParser.parseString("[{\"action\":\"wait\",\"until\":{\"position\":{\"x\":80.5,\"y\":1000000.125,\"z\":80.5}}}]").getAsJsonArray();
        var parsed = PlayerActionPlan.parse(request);
        request.get(0).getAsJsonObject().getAsJsonObject("until").getAsJsonObject("position").addProperty("y", 0);
        assertEquals(1000000.125, parsed.get(0).params().getAsJsonObject("until").getAsJsonObject("position").get("y").getAsDouble(), 0);
    }
}
