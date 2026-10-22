package com.agenticreplay.nativecheck;

import com.google.gson.*;
import com.replaymod.agent.ApiHttpTransport;
import com.replaymod.core.ReplayMod;
import com.replaymod.recording.Setting;
import com.replaymod.replay.*;
import com.replaymod.replay.camera.CameraEntity;
import com.replaymod.simplepathing.*;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.rendering.VideoRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.io.File;
import java.nio.file.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Opt-in native fixture. Compiled separately; never included in the runtime JAR. */
@Mod("agenticreplaynativecheck")
public final class NativeProbe {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private boolean draining;

    public NativeProbe() throws Exception {
        String token = System.getProperty("agenticreplay.nativecheck.token");
        if (token == null) return;
        ApiHttpTransport transport = new ApiHttpTransport(
                Integer.getInteger("agenticreplay.nativecheck.port", 18773), token, queue::add, this::call);
        Runtime.getRuntime().addShutdownHook(new Thread(transport::close));
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post event) -> {
            if (draining) return;
            draining = true;
            try {
                Runnable task;
                while ((task = queue.poll()) != null) task.run();
            } finally { draining = false; }
        });
    }

    private ReplayHandler replay() { return ReplayModReplay.instance.getReplayHandler(); }

    private JsonElement call(String method, JsonObject p) throws Exception {
        switch (method) {
            case "status": return status();
            case "connect": {
                String address = p.get("address").getAsString();
                if (!address.startsWith("127.0.0.1:")) throw new IllegalArgumentException("Loopback fixture required");
                var settings = ReplayMod.instance.getSettingsRegistry();
                settings.set(Setting.RECORD_SERVER, true);
                settings.set(Setting.AUTO_START_RECORDING, true);
                settings.set(Setting.RENAME_DIALOG, false);
                ConnectScreen.connect(new TitleScreen(), mc, ServerAddress.parse(address),
                        new ServerInfo("Native fixture", address, ServerInfo.ServerType.OTHER), false, null);
                break;
            }
            case "disconnect": mc.disconnect(); mc.setScreen(new TitleScreen()); break;
            case "list": {
                JsonArray result = new JsonArray();
                try (var paths = Files.list(ReplayMod.instance.folders.getReplayFolder())) {
                    paths.filter(path -> path.toString().endsWith(".mcpr")).sorted().forEach(path -> result.add(path.getFileName().toString()));
                }
                return result;
            }
            case "open": {
                String name = p.get("name").getAsString();
                if (!name.equals(Path.of(name).getFileName().toString())) throw new IllegalArgumentException("Replay basename required");
                ReplayModReplay.instance.startReplay(ReplayMod.instance.folders.getReplayFolder().resolve(name).toFile());
                break;
            }
            case "seek": replay().getReplaySender().setReplaySpeed(0); replay().doJump(p.get("time").getAsInt(), false); break;
            case "play": replay().getReplaySender().setReplaySpeed(p.get("speed").getAsDouble()); break;
            case "camera": {
                CameraEntity camera = replay().getCameraEntity();
                camera.setCameraPosition(p.get("x").getAsDouble(), p.get("y").getAsDouble(), p.get("z").getAsDouble());
                camera.setCameraRotation(p.get("yaw").getAsFloat(), p.get("pitch").getAsFloat(), 0);
                break;
            }
            case "export": return export();
            case "close": replay().endReplay(); break;
            default: throw new IllegalArgumentException(method);
        }
        return status();
    }

    private JsonObject status() {
        JsonObject result = new JsonObject();
        ReplayHandler replay = replay();
        result.addProperty("live", mc.world != null && replay == null);
        result.addProperty("replayOpen", replay != null);
        result.addProperty("cameraReady", mc.player instanceof CameraEntity && mc.world != null);
        result.addProperty("screen", mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName());
        if (replay != null) {
            result.addProperty("duration", replay.getReplayDuration());
            result.addProperty("time", replay.getReplaySender().currentTimeStamp());
            result.addProperty("seeking", ((FullReplaySender) replay.getReplaySender()).isHurrying());
        }
        if (mc.player != null) {
            result.addProperty("x", mc.player.getX()); result.addProperty("y", mc.player.getY()); result.addProperty("z", mc.player.getZ());
        }
        return result;
    }

    private JsonObject export() throws Exception {
        ReplayHandler handler = replay();
        CameraEntity camera = handler.getCameraEntity();
        SPTimeline timeline = new SPTimeline();
        timeline.setDefaultInterpolatorType(InterpolatorType.LINEAR);
        int start = Math.max(0, handler.getReplayDuration() - 1500);
        for (int time : new int[] {0, 1000}) {
            timeline.addTimeKeyframe(time, start + time);
            timeline.addPositionKeyframe(time, camera.getX(), camera.getY(), camera.getZ(), camera.getYaw(), camera.getPitch(), 0, -1);
        }
        timeline.getTimePath().updateAll(); timeline.getPositionPath().updateAll();
        File output = new File("native-check/export.mp4"); output.getAbsoluteFile().getParentFile().mkdirs();
        RenderSettings settings = new RenderSettings(RenderSettings.RenderMethod.DEFAULT,
                RenderSettings.EncodingPreset.MP4_CUSTOM, 320, 180, 10, 1000000, output,
                true, false, false, false, false, null, 360, 180, false, false, false,
                RenderSettings.AntiAliasing.NONE, "/usr/bin/ffmpeg",
                "-y -f rawvideo -pix_fmt bgra -s %WIDTH%x%HEIGHT% -r %FPS% -i - %FILTERS% -an -c:v libx264 -preset ultrafast -pix_fmt yuv420p \"%FILENAME%\"", false);
        VideoRenderer renderer = new VideoRenderer(settings, handler, timeline.getTimeline());
        JsonObject result = new JsonObject();
        try { result.addProperty("success", renderer.renderVideo()); }
        catch (Throwable failure) { throw new Exception("Native export failed", failure); }
        result.addProperty("frames", renderer.getFramesDone()); result.addProperty("bytes", output.length());
        return result;
    }
}
