package com.replaymod.agent;

import com.google.gson.*;
import com.replaymod.core.mixin.KeyBindingAccessor;
import com.replaymod.replay.ReplayModReplay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.MathHelper;
import java.util.*;

/** Normal client input, not server teleports or fabricated interaction packets. */
final class LivePlayerControls implements PlayerActionRunner.Port {
    private final MinecraftClient mc;
    private final PlayerActionRunner runner;
    private final Set<KeyBinding> owned = new HashSet<>();
    private ClientPlayerEntity actor;
    private ClientWorld world;
    private float startYaw, startPitch;

    LivePlayerControls(MinecraftClient mc) {
        this.mc = mc;
        this.runner = new PlayerActionRunner(this, () -> System.nanoTime() / 1000000L);
    }
    void tick() { runner.tick(); }
    void stop(String reason) { runner.stop(reason); }
    boolean holdingAttack() {
        return runner.active() && owned.contains(mc.options.attackKey) && mc.options.attackKey.isPressed()
                && mc.currentScreen == null && mc.getOverlay() == null && mc.player == actor && mc.getCameraEntity() == actor;
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
        requireLive(); actor = mc.player; world = mc.world;
        return runner.start(plan);
    }
    JsonObject single(String action, JsonObject params) {
        JsonObject step = params.deepCopy(); step.addProperty("action", action);
        JsonArray steps = new JsonArray(); steps.add(step); return start(steps);
    }
    private void requireLive() {
        if (ReplayModReplay.instance.getReplayHandler() != null) throw new IllegalStateException("Live player controls are unavailable during replay");
        if (mc.player == null || mc.world == null || mc.interactionManager == null) throw new IllegalStateException("Join a live world first");
    }
    @Override public void guard() {
        requireLive();
        if (mc.player != actor || mc.world != world) throw new IllegalStateException("Player or world changed");
        if (!actor.isAlive()) throw new IllegalStateException("Player is dead");
        if (mc.isPaused()) throw new IllegalStateException("Client is paused");
    }
    private void requireGameplay() {
        if (mc.currentScreen != null) throw new IllegalStateException("Close the screen before controlling gameplay");
        if (mc.getOverlay() != null) throw new IllegalStateException("Wait for the loading overlay to finish");
        if (mc.getCameraEntity() != actor) throw new IllegalStateException("Live player is not the active camera");
    }
    @Override public void begin(PlayerActionPlan.Step step) {
        JsonObject p = step.params();
        switch (step.action()) {
            case "look": requireGameplay(); startYaw = actor.getYaw(); startPitch = actor.getPitch(); break;
            case "input": case "use": case "attack": case "dismount": requireGameplay(); break;
            case "select": requireGameplay(); actor.getInventory().selectedSlot = p.get("slot").getAsInt(); break;
            case "fly":
                requireGameplay();
                if (!actor.getAbilities().allowFlying) throw new IllegalStateException("Server has not allowed flight");
                if (p.get("flying").getAsBoolean() && actor.isOnGround() && !actor.isSpectator())
                    throw new IllegalStateException("Jump before enabling flight; vanilla cancels flight on the ground");
                actor.getAbilities().flying = p.get("flying").getAsBoolean(); actor.sendAbilitiesUpdate(); break;
            case "close_screen":
                if (mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>) actor.closeHandledScreen();
                else if (mc.currentScreen != null) mc.setScreen(null);
                break;
            case "click_slot": {
                var handler = actor.currentScreenHandler;
                if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>) || handler.syncId != p.get("syncId").getAsInt())
                    throw new IllegalStateException("Container changed; inspect player.state before clicking");
                int slot = p.get("slotId").getAsInt();
                if (slot >= handler.slots.size()) throw new IllegalArgumentException("slotId outside current container");
                mc.interactionManager.clickSlot(handler.syncId, slot, p.has("button") ? p.get("button").getAsInt() : 0,
                    SlotActionType.valueOf(p.get("clickAction").getAsString()), actor);
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
                actor.setYaw(startYaw + MathHelper.wrapDegrees(targetYaw - startYaw) * progress);
                actor.setPitch(startPitch + (p.get("pitch").getAsFloat() - startPitch) * progress);
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
        if (step.action().equals("look") || owned.contains(mc.options.attackKey) || owned.contains(mc.options.useKey))
            mc.gameRenderer.updateTargetedEntity(1.0f);
    }
    private KeyBinding binding(String key) {
        return switch (key) {
            case "forward" -> mc.options.forwardKey; case "back" -> mc.options.backKey;
            case "left" -> mc.options.leftKey; case "right" -> mc.options.rightKey;
            case "jump" -> mc.options.jumpKey; case "sneak" -> mc.options.sneakKey;
            case "sprint" -> mc.options.sprintKey; case "attack" -> mc.options.attackKey; case "use" -> mc.options.useKey;
            default -> throw new IllegalArgumentException("Unknown input");
        };
    }
    private void hold(String key, boolean pressed, boolean first) {
        KeyBinding binding = binding(key); owned.add(binding);
        var accessor = (KeyBindingAccessor) binding;
        // StickyKeyBinding.setPressed toggles sneak/sprint. Set the held state explicitly instead.
        accessor.agenticSetPressed(pressed);
        if (first && pressed && (key.equals("attack") || key.equals("use")))
            accessor.setPressTime(accessor.getPressTime() + 1);
    }
    @Override public void release() {
        boolean breaking = owned.contains(mc.options.attackKey);
        for (KeyBinding binding : owned) {
            var accessor = (KeyBindingAccessor) binding;
            accessor.agenticSetPressed(false); accessor.setPressTime(0);
        }
        owned.clear();
        if (breaking && mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
    }
    @Override public boolean matches(JsonObject p) {
        for (var entry : p.entrySet()) {
            JsonElement value = entry.getValue();
            boolean match = switch (entry.getKey()) {
                case "riding" -> actor.hasVehicle() == value.getAsBoolean();
                case "onGround" -> actor.isOnGround() == value.getAsBoolean();
                case "flying" -> actor.getAbilities().flying == value.getAsBoolean();
                case "slot" -> actor.getInventory().selectedSlot == value.getAsInt();
                case "screen" -> screenName().equals(value.getAsString());
                case "dimension" -> world.getRegistryKey().getValue().toString().equals(value.getAsString());
                case "position" -> {
                    var pos = value.getAsJsonObject(); double tolerance = pos.has("tolerance") ? pos.get("tolerance").getAsDouble() : 0.5;
                    yield actor.squaredDistanceTo(pos.get("x").getAsDouble(), pos.get("y").getAsDouble(), pos.get("z").getAsDouble()) <= tolerance * tolerance;
                }
                default -> false;
            };
            if (!match) return false;
        }
        return true;
    }
    private String screenName() { return mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName(); }
    JsonObject state() {
        JsonObject out = new JsonObject();
        boolean live = mc.player != null && mc.world != null && ReplayModReplay.instance.getReplayHandler() == null;
        out.addProperty("live", live); out.addProperty("screen", screenName()); out.add("action", job());
        out.addProperty("paused", mc.isPaused()); out.addProperty("windowFocused", mc.isWindowFocused());
        out.addProperty("loading", mc.getOverlay() != null);
        out.addProperty("background", !mc.options.pauseOnLostFocus);
        out.addProperty("cursorLocked", mc.mouse.isCursorLocked());
        if (!live) return out;
        var player = mc.player;
        out.addProperty("x", player.getX()); out.addProperty("y", player.getY()); out.addProperty("z", player.getZ());
        out.addProperty("yaw", player.getYaw()); out.addProperty("pitch", player.getPitch());
        out.addProperty("dimension", mc.world.getRegistryKey().getValue().toString());
        out.addProperty("alive", player.isAlive()); out.addProperty("onGround", player.isOnGround());
        out.addProperty("riding", player.hasVehicle()); out.addProperty("flying", player.getAbilities().flying);
        out.addProperty("allowFlying", player.getAbilities().allowFlying); out.addProperty("slot", player.getInventory().selectedSlot);
        out.addProperty("vehicleId", player.getVehicle() == null ? -1 : player.getVehicle().getId());
        JsonObject inputs = new JsonObject();
        for (String key : PlayerActionPlan.KEYS) inputs.addProperty(key, binding(key).isPressed());
        out.add("inputs", inputs);
        JsonObject movement = new JsonObject(); movement.addProperty("forward", player.input.movementForward);
        movement.addProperty("sideways", player.input.movementSideways); movement.addProperty("jump", player.input.jumping);
        movement.addProperty("sneak", player.input.sneaking); out.add("movement", movement);
        JsonObject velocity = new JsonObject(); velocity.addProperty("x", player.getVelocity().x);
        velocity.addProperty("y", player.getVelocity().y); velocity.addProperty("z", player.getVelocity().z); out.add("velocity", velocity);
        var handler = player.currentScreenHandler; out.addProperty("syncId", handler.syncId);
        JsonArray slots = new JsonArray();
        for (var slot : handler.slots) {
            JsonObject item = new JsonObject(); item.addProperty("slotId", slot.id);
            item.addProperty("item", Registries.ITEM.getId(slot.getStack().getItem()).toString()); item.addProperty("count", slot.getStack().getCount());
            slots.add(item);
        }
        out.add("slots", slots);
        JsonObject cursor = new JsonObject(); cursor.addProperty("item", Registries.ITEM.getId(handler.getCursorStack().getItem()).toString());
        cursor.addProperty("count", handler.getCursorStack().getCount()); out.add("cursor", cursor);
        var hit = mc.crosshairTarget;
        if (hit != null) {
            JsonObject target = new JsonObject(); target.addProperty("type", hit.getType().name());
            if (hit instanceof BlockHitResult block) {
                var pos = block.getBlockPos(); target.addProperty("x", pos.getX()); target.addProperty("y", pos.getY()); target.addProperty("z", pos.getZ());
                target.addProperty("face", block.getSide().name()); target.addProperty("block", Registries.BLOCK.getId(mc.world.getBlockState(pos).getBlock()).toString());
            } else if (hit instanceof EntityHitResult entity) target.addProperty("entityId", entity.getEntity().getId());
            out.add("target", target);
        }
        return out;
    }
}
