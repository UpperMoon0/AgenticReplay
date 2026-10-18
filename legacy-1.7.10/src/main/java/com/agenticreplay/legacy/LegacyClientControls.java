package com.agenticreplay.legacy;

import com.google.gson.*;
import com.replaymod.replay.ReplayModReplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.entity.player.EntityPlayer;
import java.util.Arrays;

/** Live-world controls using 1.7.10 MCP stable-12 contracts. */
public final class LegacyClientControls {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final LegacyReplayBridge replay;
    public static final String[] METHODS = {"client.connect", "client.disconnect", "client.background", "player.state", "player.look", "player.select", "player.screen.close"};
    public LegacyClientControls(LegacyReplayBridge replay) { this.replay = replay; }
    public JsonElement call(String method, JsonObject p) throws Exception {
        if (!Arrays.asList(METHODS).contains(method)) throw new UnsupportedOperationException("Unsupported legacy method: " + method);
        if ("player.state".equals(method)) return state();
        if ("client.connect".equals(method)) {
            if (mc.theWorld != null || ReplayModReplay.instance.getReplayHandler() != null || mc.currentScreen instanceof GuiConnecting)
                throw new IllegalStateException("Disconnect before connecting to another server");
            String address = string(p, "address");
            if (address.length() > 255 || address.trim().isEmpty() || address.matches(".*[\\s/\\\\].*"))
                throw new IllegalArgumentException("Invalid server address");
            replay.enableRecording(!p.has("recording") || bool(p, "recording"));
            mc.displayGuiScreen(new GuiConnecting(new GuiMainMenu(), mc, new ServerData("AgenticReplay", address)));
            return state();
        }
        if ("client.disconnect".equals(method)) {
            if (mc.currentScreen instanceof GuiConnecting)
                throw new IllegalStateException("Connection in progress; cancel it in the Minecraft UI before disconnecting");
            if (ReplayModReplay.instance.getReplayHandler() != null) return replay.call("replay.close", new JsonObject());
            replay.prepareDisconnect();
            if (mc.theWorld != null) mc.theWorld.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
            mc.displayGuiScreen(new GuiMainMenu());
            return state();
        }
        if ("client.background".equals(method)) {
            if (p.has("hidden")) throw new UnsupportedOperationException("Window hiding is unavailable on the legacy LWJGL2 client");
            if (p.has("enabled")) mc.gameSettings.pauseOnLostFocus = !bool(p, "enabled");
            else mc.gameSettings.pauseOnLostFocus = bool(p, "pauseOnLostFocus");
            return state();
        }
        requireLivePlayer();
        if (!"player.screen.close".equals(method) && (mc.isGamePaused() || mc.thePlayer.isDead || mc.thePlayer.getHealth() <= 0))
            throw new IllegalStateException("Live control requires an unpaused, living player");
        if ("player.look".equals(method)) {
            if (mc.currentScreen != null) throw new IllegalStateException("Close the screen before controlling the player");
            if (p.has("ticks") && number(p, "ticks") != 1)
                throw new IllegalArgumentException("Legacy look supports only an immediate one-tick rotation");
            double yaw = number(p, "yaw"), pitch = number(p, "pitch");
            if (pitch < -90 || pitch > 90) throw new IllegalArgumentException("pitch must be between -90 and 90");
            mc.thePlayer.rotationYaw = (float) (yaw % 360);
            mc.thePlayer.rotationPitch = (float) pitch;
        } else if ("player.select".equals(method)) {
            double slot = number(p, "slot");
            if (slot < 0 || slot > 8 || slot != Math.rint(slot)) throw new IllegalArgumentException("slot must be an integer 0..8");
            mc.thePlayer.inventory.currentItem = (int) slot;
        } else if ("player.screen.close".equals(method)) {
            mc.thePlayer.closeScreen();
        } else throw new IllegalArgumentException("Unsupported legacy method: " + method);
        return state();
    }
    public JsonObject state() {
        JsonObject out = new JsonObject();
        EntityPlayer p = mc.thePlayer;
        out.addProperty("connected", p != null && mc.theWorld != null);
        out.addProperty("replay", ReplayModReplay.instance.getReplayHandler() != null);
        out.addProperty("live", p != null && mc.theWorld != null && ReplayModReplay.instance.getReplayHandler() == null);
        out.addProperty("paused", mc.isGamePaused());
        out.addProperty("connecting", mc.currentScreen instanceof GuiConnecting);
        out.addProperty("screen", mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName());
        out.addProperty("pauseOnLostFocus", mc.gameSettings.pauseOnLostFocus);
        if (p != null) {
            out.addProperty("name", p.getCommandSenderName());
            out.addProperty("x", p.posX); out.addProperty("y", p.posY); out.addProperty("z", p.posZ);
            out.addProperty("yaw", p.rotationYaw); out.addProperty("pitch", p.rotationPitch);
            out.addProperty("dimension", p.dimension); out.addProperty("health", p.getHealth());
            out.addProperty("slot", p.inventory.currentItem); out.addProperty("flying", p.capabilities.isFlying);
        }
        return out;
    }
    private void requireLivePlayer() {
        if (mc.thePlayer == null || mc.theWorld == null || ReplayModReplay.instance.getReplayHandler() != null)
            throw new IllegalStateException("A live player world is required");
    }
    private static boolean bool(JsonObject p, String key) {
        if (!p.has(key) || !p.get(key).isJsonPrimitive() || !p.get(key).getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException(key + " must be a boolean");
        return p.get(key).getAsBoolean();
    }
    private static String string(JsonObject p, String key) {
        if (!p.has(key) || !p.get(key).isJsonPrimitive() || !p.get(key).getAsJsonPrimitive().isString())
            throw new IllegalArgumentException(key + " must be a string");
        return p.get(key).getAsString();
    }
    private static double number(JsonObject p, String key) {
        if (!p.has(key) || !p.get(key).isJsonPrimitive() || !p.get(key).getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException(key + " must be a number");
        double value = p.get(key).getAsDouble();
        if (Double.isNaN(value) || Double.isInfinite(value)) throw new IllegalArgumentException(key + " must be finite");
        return value;
    }
}
