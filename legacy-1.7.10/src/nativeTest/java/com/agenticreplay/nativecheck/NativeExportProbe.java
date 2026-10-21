package com.agenticreplay.nativecheck;

import com.agenticreplay.legacy.LegacyClientQueue;
import com.agenticreplay.legacy.LegacyReplayBridge;
import com.google.gson.JsonObject;
import com.replaymod.agent.ApiHttpTransport;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.rendering.VideoRenderer;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.simplepathing.SPTimeline;
import com.replaymod.simplepathing.InterpolatorType;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.io.File;

/** Separate opt-in test mod. Never included in the runtime distribution. */
@Mod(modid = "agenticreplaynativecheck", name = "AgenticReplay Native Acceptance Probe", version = "1", dependencies = "required-after:agenticreplay")
public final class NativeExportProbe {
    private final LegacyClientQueue queue = new LegacyClientQueue();
    private boolean exporting;

    @Mod.EventHandler public void initialize(FMLInitializationEvent event) throws Exception {
        String token = System.getProperty("agenticreplay.nativecheck.token", "");
        if (token.isEmpty()) return;
        FMLCommonHandler.instance().bus().register(this);
        final ApiHttpTransport transport = new ApiHttpTransport(
                Integer.getInteger("agenticreplay.nativecheck.port", 18773), token, queue::enqueue,
                (method, params) -> {
                    if ("world.create".equals(method)) {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                        if (mc.theWorld != null) throw new IllegalStateException("Start from the menu");
                        new LegacyReplayBridge().enableRecording(true);
                        String name = "agenticreplay-native-" + java.util.UUID.randomUUID();
                        net.minecraft.world.WorldSettings settings = new net.minecraft.world.WorldSettings(6L,
                                net.minecraft.world.WorldSettings.GameType.CREATIVE, false, false,
                                net.minecraft.world.WorldType.FLAT).enableCommands();
                        // Return before the synchronous integrated-server startup; its
                        // nested loading-screen renders must not dispatch more commands.
                        com.replaymod.core.ReplayMod.instance.runLater(() -> mc.launchIntegratedServer(name, name, settings));
                        JsonObject result = new JsonObject(); result.addProperty("world", name); return result;
                    }
                    if ("capture".equals(method)) {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                        File root = new File("native-check"); root.mkdirs();
                        net.minecraft.util.ScreenShotHelper.saveScreenshot(root, "scene.png", mc.displayWidth,
                                mc.displayHeight, mc.getFramebuffer());
                        JsonObject result = new JsonObject();
                        result.addProperty("file", new File(root, "screenshots/scene.png").getAbsolutePath());
                        return result;
                    }
                    if (!"export".equals(method)) throw new UnsupportedOperationException(method);
                    try { return export(); }
                    catch (Throwable failure) { throw new Exception("Native export failed", failure); }
                });
        Runtime.getRuntime().addShutdownHook(new Thread(transport::close));
    }

    @SubscribeEvent public void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (exporting) {
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                JsonObject sample = new LegacyReplayBridge().status();
                if (mc.theWorld != null) sample.addProperty("worldTime", mc.theWorld.getWorldTime());
                if (mc.renderViewEntity != null) {
                    sample.addProperty("viewX", mc.renderViewEntity.posX);
                    sample.addProperty("viewY", mc.renderViewEntity.posY);
                    sample.addProperty("viewZ", mc.renderViewEntity.posZ);
                    sample.addProperty("viewPitch", mc.renderViewEntity.rotationPitch);
                }
                Class<?> terrain = Class.forName("com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer");
                Object activeTerrain = terrain.getMethod("getInstance").invoke(null);
                sample.addProperty("visibleChunks", ((Number) terrain.getMethod("getVisibleChunkCount").invoke(activeTerrain)).intValue());
                sample.addProperty("immediateTerrain", com.replaymod.render.hooks.AngelicaReplayCompat.forceImmediateTerrain(false));
                com.replaymod.render.hooks.EntityRendererHandler handler =
                        ((com.replaymod.render.hooks.EntityRendererHandler.IEntityRenderer) mc.entityRenderer).replayModRender_getHandler();
                if (handler != null && handler.getRenderInfo().getFramesDone() == 1) {
                    int texture = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
                    net.minecraft.util.ScreenShotHelper.saveScreenshot(new File("native-check"), "export-source.png",
                            mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
                    org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, texture);
                }
                java.nio.file.Files.write(java.nio.file.Paths.get("native-check/frames.jsonl"),
                        (sample.toString() + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception failure) { throw new IllegalStateException(failure); }
        }
        queue.drain();
    }

    private JsonObject export() throws Throwable {
        ReplayHandler handler = ReplayModReplay.instance.getReplayHandler();
        if (handler == null) throw new IllegalStateException("Open a replay first");
        JsonObject position = new LegacyReplayBridge().status().getAsJsonObject("camera");
        SPTimeline timeline = new SPTimeline();
        timeline.setDefaultInterpolatorType(InterpolatorType.LINEAR);
        int start = Math.max(0, handler.getReplayDuration() - 1500);
        for (int time : new int[] {0, 1000}) {
            timeline.addTimeKeyframe(time, start + time);
            timeline.addPositionKeyframe(time, position.get("x").getAsDouble(),
                    position.get("y").getAsDouble(), position.get("z").getAsDouble(),
                    position.get("yaw").getAsFloat(), position.get("pitch").getAsFloat(), 0, -1);
        }
        timeline.getTimePath().updateAll();
        timeline.getPositionPath().updateAll();
        File output = new File("native-check/export.mp4");
        output.getAbsoluteFile().getParentFile().mkdirs();
        RenderSettings settings = new RenderSettings(RenderSettings.RenderMethod.DEFAULT,
                RenderSettings.EncodingPreset.MP4_DEFAULT, 320, 180, 10, 1000000, output,
                true, false, false, false, null, 360, 180, false, false, false,
                RenderSettings.AntiAliasing.NONE, System.getProperty("agenticreplay.nativecheck.ffmpeg"),
                "-y -f rawvideo -pix_fmt bgra -s %WIDTH%x%HEIGHT% -r %FPS% -i - %FILTERS% -an -c:v libx264 -preset ultrafast -pix_fmt yuv420p \"%FILENAME%\"", false);
        VideoRenderer renderer = new VideoRenderer(settings, handler, timeline.getTimeline());
        JsonObject result = new JsonObject();
        exporting = true;
        try { result.addProperty("success", renderer.renderVideo()); }
        finally { exporting = false; }
        result.addProperty("frames", renderer.getFramesDone());
        result.addProperty("bytes", output.length());
        result.addProperty("file", output.getAbsolutePath());
        return result;
    }
}
