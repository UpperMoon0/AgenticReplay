package com.replaymod.agent;

import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.LongSupplier;

/** Client-tick runner. No sleeps or blocking RPC calls; native inputs have a finite lease. */
final class PlayerActionRunner {
    interface Port {
        void guard();
        boolean paused();
        void begin(PlayerActionPlan.Step step);
        void apply(PlayerActionPlan.Step step, int tick);
        boolean matches(JsonObject condition);
        void release();
    }
    private final Port port;
    private final LongSupplier clock;
    private List<PlayerActionPlan.Step> steps = List.of();
    private String jobId, state = "idle", error;
    private int index, elapsed;
    private long deadline;
    PlayerActionRunner(Port port, LongSupplier clock) { this.port = port; this.clock = clock; }

    JsonObject start(List<PlayerActionPlan.Step> plan) {
        if (active()) throw new IllegalStateException("Player action active; stop it before starting another");
        guard(plan.get(0));
        steps = plan; index = elapsed = 0; error = null;
        jobId = UUID.randomUUID().toString(); state = "queued";
        // Monotonic wall deadline also bounds slow ticks and unexpected client stalls.
        deadline = clock.getAsLong() + 30000L + plan.stream().mapToLong(s -> s.ticks() * 100L).sum();
        return status();
    }
    boolean active() { return state.equals("queued") || state.equals("running"); }
    private void guard(PlayerActionPlan.Step step) {
        port.guard();
        // Screen recovery must work even when a singleplayer menu pauses the client.
        if (port.paused() && !step.action().equals("close_screen"))
            throw new IllegalStateException("Client is paused");
    }
    void tick() {
        if (!active()) return;
        try {
            guard(steps.get(index));
            if (clock.getAsLong() > deadline) throw new IllegalStateException("Player action wall-time limit exceeded");
            state = "running";
            var step = steps.get(index);
            if (elapsed != 0) {
                boolean conditional = step.action().equals("wait") && step.params().has("until");
                boolean satisfied = conditional && port.matches(step.params().getAsJsonObject("until"));
                if (elapsed >= step.ticks() || satisfied) {
                    if (conditional && !satisfied) throw new IllegalStateException("Condition timed out at step " + index);
                    port.release();
                    index++; elapsed = 0;
                    if (index == steps.size()) { state = "succeeded"; return; }
                    step = steps.get(index);
                    guard(step);
                }
            }
            if (elapsed == 0) port.begin(step);
            port.apply(step, ++elapsed);
        } catch (RuntimeException failure) { finish("failed", failure.getMessage()); }
    }
    void stop(String reason) { if (active()) finish("cancelled", reason); }
    private void finish(String terminal, String reason) {
        state = terminal; error = reason;
        port.release();
    }
    JsonObject status() {
        JsonObject out = new JsonObject(); out.addProperty("jobId", jobId); out.addProperty("state", state);
        out.addProperty("step", index); out.addProperty("steps", steps.size()); out.addProperty("stepTicks", elapsed);
        out.addProperty("error", error); return out;
    }
}
