package com.replaymod.agent;

import com.google.gson.*;
import com.replaymod.core.mixin.KeyBindingAccessor;
import com.replaymod.replay.ReplayModReplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.*;
import net.minecraft.util.Mth;
import java.util.*;

/** Normal client input, not server teleports or fabricated interaction packets. */
final class LivePlayerControls implements PlayerActionRunner.Port {
    private final Minecraft mc;
    private final PlayerActionRunner runner;
    private final Set<KeyMapping> owned = new HashSet<>();
    private LocalPlayer actor;
    private ClientLevel world;
    private float startYaw, startPitch;

    LivePlayerControls(Minecraft mc) {
        this.mc = mc;
        this.runner = new PlayerActionRunner(this, () -> System.nanoTime() / 1000000L);
    }
    void tick() { runner.tick(); }
    void stop(String reason) { runner.stop(reason); }
    boolean holdingAttack() {
        return runner.active() && owned.contains(mc.options.keyAttack) && mc.options.keyAttack.isDown()
                && mc.screen == null && mc.getOverlay() == null && mc.player == actor && mc.getCameraEntity() == actor;
    }
    JsonObject job() { return runner.status(); }
    JsonObject job(String id) {
        var status = job();
        if (!status.has("jobId") || status.get("jobId").isJsonNull() || !status.get("jobId").getAsString().equals(id))
            throw new IllegalArgumentException("Unknown player job");
        return status;
    }
    JsonObject start(JsonArray steps) {
        var plan = PlayerActionPlan.parse(steps);
        if (runner.active()) throw new IllegalStateException("Player action active; stop it before starting another");
        requireLive(); actor = mc.player; world = mc.level;
        return runner.start(plan);
    }
    JsonObject single(String action, JsonObject params) {
        JsonObject step = params.deepCopy(); step.addProperty("action", action);
        JsonArray steps = new JsonArray(); steps.add(step); return start(steps);
    }
    private void requireLive() {
        if (ReplayModReplay.instance.getReplayHandler() != null) throw new IllegalStateException("Live player controls are unavailable during replay");
        if (mc.player == null || mc.level == null || mc.gameMode == null) throw new IllegalStateException("Join a live world first");
    }
    @Override public void guard() {
        requireLive();
        if (mc.player != actor || mc.level != world) throw new IllegalStateException("Player or world changed");
        if (!actor.isAlive()) throw new IllegalStateException("Player is dead");
    }
    @Override public boolean paused() { return mc.isPaused(); }
    private void requireGameplay() {
        if (mc.screen != null) throw new IllegalStateException("Close the screen before controlling gameplay");
        if (mc.getOverlay() != null) throw new IllegalStateException("Wait for the loading overlay to finish");
        if (mc.getCameraEntity() != actor) throw new IllegalStateException("Live player is not the active camera");
    }
    @Override public void begin(PlayerActionPlan.Step step) {
        JsonObject p = step.params();
        switch (step.action()) {
            case "look": requireGameplay(); startYaw = actor.getYRot(); startPitch = actor.getXRot(); break;
            case "input": case "use": case "attack": case "dismount": requireGameplay(); break;
            case "select": requireGameplay(); actor.getInventory().setSelectedSlot(p.get("slot").getAsInt()); break;
            case "fly":
                requireGameplay();
                if (!actor.getAbilities().mayfly) throw new IllegalStateException("Server has not allowed flight");
                if (p.get("flying").getAsBoolean() && actor.onGround() && !actor.isSpectator())
                    throw new IllegalStateException("Jump before enabling flight; vanilla cancels flight on the ground");
                actor.getAbilities().flying = p.get("flying").getAsBoolean(); actor.onUpdateAbilities(); break;
            case "close_screen":
                if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) actor.closeContainer();
                else if (mc.screen != null) mc.setScreen(null);
                break;
            case "click_slot": {
                var handler = actor.containerMenu;
                if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) || handler.containerId != p.get("syncId").getAsInt())
                    throw new IllegalStateException("Container changed; inspect player.state before clicking");
                int slot = p.get("slotId").getAsInt();
                if (slot >= handler.slots.size()) throw new IllegalArgumentException("slotId outside current container");
                mc.gameMode.handleContainerInput(handler.containerId, slot, p.has("button") ? p.get("button").getAsInt() : 0,
                    ContainerInput.valueOf(p.get("clickAction").getAsString()), actor);
                break;
            }
            case "assert":
                if (!matches(p.getAsJsonObject("until"))) throw new IllegalStateException("Player assertion failed");
                break;
            case "wait": break;
            default: throw new IllegalArgumentException("Unknown action");
        }
    }
    @Override public void apply(PlayerActionPlan.Step step, int tick) {
        JsonObject p = step.params();
        switch (step.action()) {
            case "look": {
                requireGameplay();
                float progress = (float) tick / step.ticks();
                // Shortest arc, including yaw wrapping; retain doubles for world coordinates elsewhere.
                float targetYaw = (float) (p.get("yaw").getAsDouble() % 360.0);
                actor.setYRot(startYaw + Mth.wrapDegrees(targetYaw - startYaw) * progress);
                actor.setXRot(startPitch + (p.get("pitch").getAsFloat() - startPitch) * progress);
                break;
            }
            case "input":
                requireGameplay();
                for (var entry : p.getAsJsonObject("keys").entrySet()) hold(entry.getKey(), entry.getValue().getAsBoolean(), tick == 1);
                break;
            case "use": requireGameplay(); hold("use", true, tick == 1); break;
            case "attack": requireGameplay(); hold("attack", true, tick == 1); break;
            case "dismount": requireGameplay(); hold("sneak", true, tick == 1); break;
            default: break;
        }
        if (step.action().equals("look") || owned.contains(mc.options.keyAttack) || owned.contains(mc.options.keyUse))
            ((com.replaymod.core.mixin.MinecraftAccessor) mc).agenticPick(1.0f);
    }
    private KeyMapping binding(String key) {
        return switch (key) {
            case "forward" -> mc.options.keyUp; case "back" -> mc.options.keyDown;
            case "left" -> mc.options.keyLeft; case "right" -> mc.options.keyRight;
            case "jump" -> mc.options.keyJump; case "sneak" -> mc.options.keyShift;
            case "sprint" -> mc.options.keySprint; case "attack" -> mc.options.keyAttack; case "use" -> mc.options.keyUse;
            default -> throw new IllegalArgumentException("Unknown input");
        };
    }
    private void hold(String key, boolean pressed, boolean first) {
        KeyMapping binding = binding(key); owned.add(binding);
        var accessor = (KeyBindingAccessor) binding;
        // ToggleKeyMapping.setPressed toggles sneak/sprint. Set the held state explicitly instead.
        accessor.agenticSetPressed(pressed);
        if (first && pressed && (key.equals("attack") || key.equals("use")))
            accessor.setPressTime(accessor.getPressTime() + 1);
    }
    @Override public void release() {
        boolean breaking = owned.contains(mc.options.keyAttack);
        for (KeyMapping binding : owned) {
            var accessor = (KeyBindingAccessor) binding;
            accessor.agenticSetPressed(false); accessor.setPressTime(0);
        }
        owned.clear();
        if (breaking && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
    }
    @Override public boolean matches(JsonObject p) {
        for (var entry : p.entrySet()) {
            JsonElement value = entry.getValue();
            boolean match = switch (entry.getKey()) {
                case "riding" -> actor.isPassenger() == value.getAsBoolean();
                case "onGround" -> actor.onGround() == value.getAsBoolean();
                case "flying" -> actor.getAbilities().flying == value.getAsBoolean();
                case "slot" -> actor.getInventory().getSelectedSlot() == value.getAsInt();
                case "screen" -> screenName().equals(value.getAsString());
                case "dimension" -> world.dimension().identifier().toString().equals(value.getAsString());
                case "position" -> {
                    var pos = value.getAsJsonObject(); double tolerance = pos.has("tolerance") ? pos.get("tolerance").getAsDouble() : 0.5;
                    yield actor.distanceToSqr(pos.get("x").getAsDouble(), pos.get("y").getAsDouble(), pos.get("z").getAsDouble()) <= tolerance * tolerance;
                }
                default -> false;
            };
            if (!match) return false;
        }
        return true;
    }
    private String screenName() { return mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(); }
    JsonObject state() {
        JsonObject out = new JsonObject();
        boolean live = mc.player != null && mc.level != null && ReplayModReplay.instance.getReplayHandler() == null;
        out.addProperty("live", live); out.addProperty("screen", screenName()); out.add("action", job());
        out.addProperty("paused", mc.isPaused()); out.addProperty("windowFocused", mc.isWindowActive());
        // Minecraft's focus field starts true before its first event poll.
        // Report GLFW's actual attributes separately for startup diagnostics.
        if (mc.getWindow() != null && mc.getWindow().handle() != 0) {
            long window = mc.getWindow().handle();
            out.addProperty("glfwFocused", org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window, org.lwjgl.glfw.GLFW.GLFW_FOCUSED) != 0);
            out.addProperty("windowVisible", org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window, org.lwjgl.glfw.GLFW.GLFW_VISIBLE) != 0);
        }
        out.addProperty("loading", mc.getOverlay() != null);
        out.addProperty("background", !mc.options.pauseOnLostFocus);
        out.addProperty("cursorLocked", mc.mouseHandler.isMouseGrabbed());
        if (!live) return out;
        var player = mc.player;
        out.addProperty("x", player.getX()); out.addProperty("y", player.getY()); out.addProperty("z", player.getZ());
        out.addProperty("yaw", player.getYRot()); out.addProperty("pitch", player.getXRot());
        out.addProperty("dimension", mc.level.dimension().identifier().toString());
        out.addProperty("alive", player.isAlive()); out.addProperty("onGround", player.onGround());
        out.addProperty("riding", player.isPassenger()); out.addProperty("flying", player.getAbilities().flying);
        out.addProperty("allowFlying", player.getAbilities().mayfly); out.addProperty("slot", player.getInventory().getSelectedSlot());
        out.addProperty("vehicleId", player.getVehicle() == null ? -1 : player.getVehicle().getId());
        JsonObject inputs = new JsonObject();
        for (String key : PlayerActionPlan.KEYS) inputs.addProperty(key, binding(key).isDown());
        out.add("inputs", inputs);
        JsonObject movement = new JsonObject(); movement.addProperty("forward", player.input.getMoveVector().y);
        movement.addProperty("sideways", player.input.getMoveVector().x); movement.addProperty("jump", player.input.keyPresses.jump());
        movement.addProperty("sneak", player.input.keyPresses.shift()); out.add("movement", movement);
        JsonObject velocity = new JsonObject(); velocity.addProperty("x", player.getDeltaMovement().x);
        velocity.addProperty("y", player.getDeltaMovement().y); velocity.addProperty("z", player.getDeltaMovement().z); out.add("velocity", velocity);
        var handler = player.containerMenu; out.addProperty("syncId", handler.containerId);
        JsonArray slots = new JsonArray();
        for (var slot : handler.slots) {
            JsonObject item = new JsonObject(); item.addProperty("slotId", slot.index);
            item.addProperty("item", BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString()); item.addProperty("count", slot.getItem().getCount());
            slots.add(item);
        }
        out.add("slots", slots);
        JsonObject cursor = new JsonObject(); cursor.addProperty("item", BuiltInRegistries.ITEM.getKey(handler.getCarried().getItem()).toString());
        cursor.addProperty("count", handler.getCarried().getCount()); out.add("cursor", cursor);
        var hit = mc.hitResult;
        if (hit != null) {
            JsonObject target = new JsonObject(); target.addProperty("type", hit.getType().name());
            if (hit instanceof BlockHitResult block) {
                var pos = block.getBlockPos(); target.addProperty("x", pos.getX()); target.addProperty("y", pos.getY()); target.addProperty("z", pos.getZ());
                target.addProperty("face", block.getDirection().name()); target.addProperty("block", BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString());
            } else if (hit instanceof EntityHitResult entity) target.addProperty("entityId", entity.getEntity().getId());
            out.add("target", target);
        }
        return out;
    }
}
