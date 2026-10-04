package com.replaymod.agent;

import com.google.gson.*;
import com.google.common.util.concurrent.ListenableFuture;
import com.replaymod.core.ReplayMod;
import com.replaymod.core.SettingsRegistry;
import com.replaymod.core.utils.ModCompat;
import com.replaymod.pathing.player.RealtimeTimelinePlayer;
import com.replaymod.recording.ReplayModRecording;
import com.replaymod.recording.gui.GuiRecordingControls;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.ReplayModRender;
import com.replaymod.render.rendering.VideoRenderer;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.camera.CameraEntity;
import com.replaymod.replaystudio.data.Marker;
import com.replaymod.replaystudio.pathing.path.Timeline;
import com.replaymod.replaystudio.pathing.serialize.TimelineSerialization;
import com.replaymod.simplepathing.*;
import net.minecraft.entity.Entity;

import java.nio.file.*;
import java.util.*;

/** Public versioned JSON facade, independent of keyboard bindings and button callbacks.
 * All calls must run on the Minecraft client thread.
 */
public final class AgentReplayApi {
    public static final int API_VERSION = 1;
    public static final List<String> METHODS = List.of(
        "capabilities", "status", "replay.list", "replay.open", "replay.close", "replay.rename", "replay.delete",
        "playback.set", "playback.seek", "camera.set", "camera.spectate", "entities.list",
        "recording.set", "recording.marker", "markers.list", "markers.set",
        "path.get", "path.keyframe", "path.remove", "path.move", "path.clear", "path.interpolation",
        "path.undo", "path.redo", "path.save", "path.load", "path.play", "path.stop",
        "settings.get", "settings.set", "render.start", "render.status", "render.pause", "render.cancel");
    private final ReplayMod core;
    private final Gson gson = new Gson();
    private Path activeFile;
    private VideoRenderer renderer;
    private String jobId;
    private String jobState = "idle";
    private String jobError;
    private boolean renderCancelled;
    private boolean renderPaused;
    private RealtimeTimelinePlayer pathPlayer;
    private ListenableFuture<Void> pathFuture;

    public AgentReplayApi(ReplayMod core) { this.core = core; }

    public JsonElement call(String method, JsonObject p) throws Exception {
        if (!core.getMinecraft().isOnThread()) throw new IllegalStateException("Client thread required");
        if (!METHODS.contains(method)) throw new IllegalArgumentException("Unknown method: " + method);
        boolean rendering = jobState.equals("queued") || jobState.equals("running");
        if (rendering && !List.of("capabilities", "status", "render.status", "render.pause", "render.cancel").contains(method)) {
            throw new IllegalStateException("Render in progress; cancel or wait before changing the replay");
        }
        switch (method) {
            case "capabilities": return gson.toJsonTree(Map.of("apiVersion", API_VERSION, "methods", METHODS,
                    "recordingSemantics", "pause/stop use cut/split markers; disconnect finalizes capture",
                    "requiresClient", true));
            case "status": return status();
            case "replay.list": {
                JsonArray files = new JsonArray();
                try (var stream = Files.list(core.folders.getReplayFolder())) {
                    for (Path path : stream.filter(f -> f.getFileName().toString().endsWith(".mcpr")).sorted().toList()) {
                        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                        JsonObject file = new JsonObject(); file.addProperty("name", path.getFileName().toString());
                        file.addProperty("bytes", Files.size(path)); files.add(file);
                    }
                }
                return files;
            }
            case "replay.open": {
                if (core.getMinecraft().world != null && ReplayModReplay.instance.getReplayHandler() == null)
                    throw new IllegalStateException("Disconnect from the live world before opening a replay");
                Path path = replayPath(text(p, "name"));
                if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Replay not found");
                stopPath();
                var file = core.files.open(path);
                try {
                    if (!bool(p, "allowModMismatch", false)) {
                        var difference = new ModCompat.ModInfoDifference(file.getModInfo());
                        if (!difference.getMissing().isEmpty() || !difference.getDiffering().isEmpty())
                            throw new IllegalArgumentException("Replay mod mismatch; set allowModMismatch explicitly");
                    }
                    ReplayModReplay.instance.startReplay(file, false, true);
                    activeFile = path;
                } catch (Exception failure) { file.close(); throw failure; }
                return status();
            }
            case "replay.close": stopPath(); replay().endReplay(); activeFile = null; return status();
            case "replay.rename": {
                requireClosed();
                Path source = replayPath(text(p, "name"));
                Path target = replayPath(text(p, "newName"));
                Files.move(source, target); return gson.toJsonTree(Map.of("name", target.getFileName().toString()));
            }
            case "replay.delete": requireClosed(); Files.delete(replayPath(text(p, "name"))); return success();
            case "playback.set": {
                requireNoPath();
                double speed = number(p, "speed", 0);
                if (speed < 0 || speed > 16) throw new IllegalArgumentException("speed must be 0..16; use seek to rewind");
                replay().getReplaySender().setReplaySpeed(speed); return status();
            }
            case "playback.seek": {
                requireNoPath();
                int time = integer(p, "time", 0);
                if (time < 0 || time > replay().getReplayDuration()) throw new IllegalArgumentException("time outside replay");
                replay().doJump(time, bool(p, "retainCamera", true)); return status();
            }
            case "camera.set": {
                requireNoPath();
                var camera = camera();
                replay().spectateCamera();
                camera.setCameraPosition(number(p, "x", camera.getX()), number(p, "y", camera.getY()), number(p, "z", camera.getZ()));
                camera.setCameraRotation((float) number(p, "yaw", camera.getYaw()), (float) number(p, "pitch", camera.getPitch()),
                        (float) number(p, "roll", camera.roll));
                if (p.has("fov")) {
                    int fov = integer(p, "fov", 70);
                    if (fov < 30 || fov > 110) throw new IllegalArgumentException("fov must be 30..110");
                    core.getMinecraft().options.getFov().setValue(fov);
                }
                return status();
            }
            case "camera.spectate": {
                requireNoPath();
                int id = integer(p, "entityId", -1);
                if (id == -1) replay().spectateCamera();
                else {
                    Entity entity = core.getMinecraft().world.getEntityById(id);
                    if (entity == null || !camera().canSpectate(entity)) throw new IllegalArgumentException("Entity cannot be spectated");
                    replay().spectateEntity(entity);
                }
                return status();
            }
            case "entities.list": {
                replay(); JsonArray result = new JsonArray();
                for (Entity entity : core.getMinecraft().world.getEntities()) {
                    JsonObject item = new JsonObject(); item.addProperty("id", entity.getId());
                    item.addProperty("uuid", entity.getUuidAsString()); item.addProperty("name", entity.getName().getString());
                    result.add(item);
                }
                return result;
            }
            case "recording.set": {
                var controls = recording();
                String state = text(p, "state");
                switch (state) {
                    case "start": controls.setStopped(false); controls.setPaused(false); break;
                    case "pause": controls.setPaused(true); break;
                    case "resume": controls.setPaused(false); break;
                    case "stop": controls.setStopped(true); break;
                    default: throw new IllegalArgumentException("state must be start, pause, resume, stop");
                }
                return status();
            }
            case "recording.marker": {
                recording();
                ReplayModRecording.instance.getConnectionEventHandler().getPacketListener().addMarker(text(p, "name"));
                return status();
            }
            case "markers.list": return gson.toJsonTree(replay().getReplayFile().getMarkers().or(HashSet::new));
            case "markers.set": {
                var array = p.getAsJsonArray("markers");
                if (array == null || array.size() > 10000) throw new IllegalArgumentException("markers array required, maximum 10000");
                Set<Marker> markers = new HashSet<>();
                for (JsonElement element : array) {
                    Marker marker = gson.fromJson(element, Marker.class);
                    if (marker.getTime() < 0 || marker.getTime() > replay().getReplayDuration())
                        throw new IllegalArgumentException("marker time outside replay");
                    markers.add(marker);
                }
                synchronized (replay().getReplayFile()) { replay().getReplayFile().writeMarkers(markers); }
                return success();
            }
            case "path.get": return pathJson();
            case "path.clear": requireNoPath(); replay(); pathing().clearCurrentTimeline(); return pathJson();
            case "path.keyframe": {
                requireNoPath();
                SPTimeline timeline = timeline(); long time = pathTime(p, "time");
                if (text(p, "type").equals("time")) {
                    int replayTime = integer(p, "replayTime", 0);
                    if (replayTime < 0 || replayTime > replay().getReplayDuration()) throw new IllegalArgumentException("replayTime outside replay");
                    if (timeline.isTimeKeyframe(time)) timeline.getTimeline().pushChange(timeline.updateTimeKeyframe(time, replayTime));
                    else timeline.addTimeKeyframe(time, replayTime);
                } else if (text(p, "type").equals("position")) {
                    var c = camera(); double x = number(p, "x", c.getX()), y = number(p, "y", c.getY()), z = number(p, "z", c.getZ());
                    float yaw = (float) number(p, "yaw", c.getYaw()), pitch = (float) number(p, "pitch", c.getPitch()),
                            roll = (float) number(p, "roll", c.roll);
                    int entity = integer(p, "entityId", -1);
                    if (entity != -1) throw new IllegalArgumentException("Use camera position keyframes; spectator path tracking is not yet exposed");
                    if (timeline.isPositionKeyframe(time)) timeline.getTimeline().pushChange(timeline.updatePositionKeyframe(time, x, y, z, yaw, pitch, roll));
                    else timeline.addPositionKeyframe(time, x, y, z, yaw, pitch, roll, -1);
                } else throw new IllegalArgumentException("type must be position or time");
                return pathJson();
            }
            case "path.remove": {
                requireNoPath(); long time = pathTime(p, "time"); var timeline = timeline();
                SPTimeline.SPPath type = pathType(p);
                if (timeline.getKeyframe(type, time) == null) throw new IllegalArgumentException("Keyframe not found");
                if (type == SPTimeline.SPPath.TIME) timeline.removeTimeKeyframe(time); else timeline.removePositionKeyframe(time);
                return pathJson();
            }
            case "path.move": {
                requireNoPath(); var t = timeline();
                t.getTimeline().pushChange(t.moveKeyframe(pathType(p), pathTime(p, "time"), pathTime(p, "newTime")));
                return pathJson();
            }
            case "path.interpolation": {
                requireNoPath(); var t = timeline();
                InterpolatorType type = InterpolatorType.valueOf(text(p, "interpolation").toUpperCase(Locale.ROOT));
                if (type == InterpolatorType.DEFAULT) t.getTimeline().pushChange(t.setInterpolatorToDefault(pathTime(p, "time")));
                else t.getTimeline().pushChange(t.setInterpolator(pathTime(p, "time"), type.newInstance()));
                return pathJson();
            }
            case "path.undo": requireNoPath(); if (timeline().getTimeline().peekUndoStack() != null) timeline().getTimeline().undoLastChange(); return pathJson();
            case "path.redo": requireNoPath(); if (timeline().getTimeline().peekRedoStack() != null) timeline().getTimeline().redoLastChange(); return pathJson();
            case "path.save": {
                var file = replay().getReplayFile();
                String name = text(p, "name");
                synchronized (file) {
                    Map<String, Timeline> paths = file.getTimelines(new SPTimeline());
                    paths.put(name, timeline().getTimeline()); file.writeTimelines(new SPTimeline(), paths);
                }
                return success();
            }
            case "path.load": {
                requireNoPath(); var file = replay().getReplayFile(); Timeline loaded;
                synchronized (file) { loaded = file.getTimelines(new SPTimeline()).get(text(p, "name")); }
                if (loaded == null) throw new IllegalArgumentException("Saved path not found");
                pathing().setCurrentTimeline(new SPTimeline(loaded)); return pathJson();
            }
            case "path.play": {
                requireNoPath(); validateTimeline();
                pathPlayer = new RealtimeTimelinePlayer(replay());
                pathFuture = pathPlayer.start(timeline().getTimeline(), pathTime(p, "from"));
                return status();
            }
            case "path.stop": stopPath(); return status();
            case "settings.get": {
                JsonObject out = new JsonObject();
                for (var key : core.getSettingsRegistry().getSettings())
                    out.add(key.getCategory() + "." + key.getKey(), gson.toJsonTree(core.getSettingsRegistry().get(key)));
                return out;
            }
            case "settings.set": {
                String name = text(p, "name");
                if (name.endsWith("Path") || name.endsWith(".path")) throw new IllegalArgumentException("Filesystem settings must be configured locally");
                for (var key : core.getSettingsRegistry().getSettings()) {
                    if ((key.getCategory() + "." + key.getKey()).equals(name)) {
                        setSetting(key, p.get("value")); core.getSettingsRegistry().save(); return success();
                    }
                }
                throw new IllegalArgumentException("Unknown setting");
            }
            case "render.start": return startRender(p);
            case "render.status": requireJob(p); return renderStatus();
            case "render.pause": requireJob(p); requireRunning(); renderPaused = bool(p, "paused", true); if (renderer != null) renderer.setPaused(renderPaused); return renderStatus();
            case "render.cancel": {
                requireJob(p); requireRunning(); renderCancelled = true;
                if (renderer != null) renderer.cancel();
                return renderStatus();
            }
            default: throw new IllegalArgumentException("Unknown method");
        }
    }

    private JsonElement startRender(JsonObject p) throws Exception {
        requireNoPath(); validateTimeline();
        var method = RenderSettings.RenderMethod.valueOf(string(p, "renderMethod", "DEFAULT").toUpperCase(Locale.ROOT));
        var preset = RenderSettings.EncodingPreset.valueOf(string(p, "preset", "MP4_CUSTOM").toUpperCase(Locale.ROOT));
        if (!method.isSupported() || !preset.isSupported()) throw new IllegalArgumentException("Unsupported render format");
        int width = integer(p, "width", 1920), height = integer(p, "height", 1080), fps = integer(p, "fps", 60);
        if (width < 16 || height < 16 || width > 7680 || height > 7680 || fps < 1 || fps > 240 ||
            (preset.isYuv420() && ((width & 1) != 0 || (height & 1) != 0))) throw new IllegalArgumentException("Invalid dimensions or frame rate");
        int bitrate = integer(p, "bitrate", 20 << 20);
        if (bitrate < 1 || bitrate > 1000000000) throw new IllegalArgumentException("Invalid bitrate");
        Path output = ApiPaths.resolve(ReplayModRender.instance.getVideoFolder().toPath(), text(p, "name"), "." + preset.getFileExtension());
        if (Files.exists(output)) throw new IllegalArgumentException("Output already exists");
        RenderSettings settings = new RenderSettings(method, preset, width, height, fps, bitrate, output.toFile(),
                bool(p, "nameTags", true), bool(p, "alpha", false), bool(p, "stabilizeYaw", false),
                bool(p, "stabilizePitch", false), bool(p, "stabilizeRoll", false), null,
                integer(p, "fovX", 360), integer(p, "fovY", 180), bool(p, "sphericalMetadata", false),
                bool(p, "depthMap", false), bool(p, "cameraPathExport", false),
                RenderSettings.AntiAliasing.valueOf(string(p, "antiAliasing", "NONE").toUpperCase(Locale.ROOT)),
                "", preset.getValue(), false);
        String[] issue = VideoRenderer.checkCompat(settings);
        if (issue != null) throw new IllegalArgumentException(String.join(" ", issue));
        ReplayHandler handler = replay();
        Timeline renderTimeline = timeline().getTimeline();
        renderer = null; renderPaused = false; jobId = UUID.randomUUID().toString(); jobState = "queued"; jobError = null; renderCancelled = false;
        core.getMinecraft().send(() -> {
            if (renderCancelled) { jobState = "cancelled"; return; }
            jobState = "running";
            try {
                renderer = new VideoRenderer(settings, handler, renderTimeline, false);
                renderer.setPaused(renderPaused);
                jobState = renderer.renderVideo() ? "succeeded" : "cancelled";
            }
            catch (Throwable failure) { jobError = failure.toString(); jobState = "failed"; }
        });
        return renderStatus();
    }

    private void validateTimeline() {
        var t = timeline();
        if (t.getPositionPath().getKeyframes().size() < 2 || t.getTimePath().getKeyframes().size() < 2)
            throw new IllegalArgumentException("At least two position and two time keyframes are required");
        if (t.getPositionPath().getKeyframe(0) == null || t.getTimePath().getKeyframe(0) == null)
            throw new IllegalArgumentException("Both paths must start at time zero");
    }
    private void requireJob(JsonObject p) {
        if (jobId == null || !jobId.equals(text(p, "jobId"))) throw new IllegalArgumentException("Unknown render job");
    }
    private void requireRunning() {
        if (!jobState.equals("running") && !jobState.equals("queued")) throw new IllegalStateException("Render job is already finished");
    }
    private JsonObject renderStatus() {
        JsonObject result = new JsonObject(); result.addProperty("jobId", jobId); result.addProperty("state", jobState);
        result.addProperty("error", jobError); result.addProperty("cancelRequested", renderCancelled);
        result.addProperty("framesDone", renderer == null ? 0 : renderer.getFramesDone());
        result.addProperty("totalFrames", renderer == null ? 0 : renderer.getTotalFrames());
        result.addProperty("paused", renderer != null && renderer.isPaused()); return result;
    }
    private JsonObject status() {
        JsonObject result = new JsonObject(); result.addProperty("apiVersion", API_VERSION);
        var r = ReplayModReplay.instance.getReplayHandler();
        result.addProperty("replayOpen", r != null); result.addProperty("pathPlaying", pathFuture != null && !pathFuture.isDone());
        if (r != null) {
            result.addProperty("duration", r.getReplayDuration()); result.addProperty("time", r.getReplaySender().currentTimeStamp());
            result.addProperty("speed", r.getReplaySender().getReplaySpeed());
            var c = r.getCameraEntity();
            if (c != null) result.add("camera", gson.toJsonTree(Map.of("x", c.getX(), "y", c.getY(), "z", c.getZ(),
                "yaw", c.getYaw(), "pitch", c.getPitch(), "roll", c.roll)));
        }
        var controls = ReplayModRecording.instance.getConnectionEventHandler().getGuiControls();
        result.addProperty("recordingAvailable", controls != null);
        if (controls != null) { result.addProperty("recordingStopped", controls.isStopped()); result.addProperty("recordingPaused", controls.isPaused()); }
        result.add("render", renderStatus()); return result;
    }
    private ReplayHandler replay() {
        var replay = ReplayModReplay.instance.getReplayHandler();
        if (replay == null) throw new IllegalStateException("Open a replay first"); return replay;
    }
    private CameraEntity camera() {
        var camera = replay().getCameraEntity(); if (camera == null) throw new IllegalStateException("Camera not ready"); return camera;
    }
    private GuiRecordingControls recording() {
        var controls = ReplayModRecording.instance.getConnectionEventHandler().getGuiControls();
        if (controls == null) throw new IllegalStateException("No packet capture; enable recording before joining the world");
        return controls;
    }
    private ReplayModSimplePathing pathing() { replay(); return ReplayModSimplePathing.instance; }
    private SPTimeline timeline() { return pathing().getCurrentTimeline(); }
    private JsonElement pathJson() throws java.io.IOException {
        String json = new TimelineSerialization(new SPTimeline(), null).serialize(Collections.singletonMap("", timeline().getTimeline()));
        return JsonParser.parseString(json);
    }
    private void requireClosed() {
        if (ReplayModReplay.instance.getReplayHandler() != null) throw new IllegalStateException("Close the replay before managing files");
    }
    private void requireNoPath() {
        if (pathFuture != null && !pathFuture.isDone()) throw new IllegalStateException("Stop camera path playback first");
    }
    private void stopPath() {
        if (pathFuture != null && !pathFuture.isDone()) { pathFuture.cancel(false); pathPlayer.onTick(); }
        pathFuture = null; pathPlayer = null;
    }
    private Path replayPath(String name) throws java.io.IOException { return ApiPaths.resolve(core.folders.getReplayFolder(), name, ".mcpr"); }
    private SPTimeline.SPPath pathType(JsonObject p) { return SPTimeline.SPPath.valueOf(text(p, "type").toUpperCase(Locale.ROOT)); }
    private static long pathTime(JsonObject p, String key) {
        long time = p.has(key) ? p.get(key).getAsLong() : 0;
        if (time < 0 || time > 86400000) throw new IllegalArgumentException("Path time must be 0..86400000 milliseconds"); return time;
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void setSetting(SettingsRegistry.SettingKey key, JsonElement value) {
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("value required");
        Object converted = gson.fromJson(value, key.getDefault().getClass());
        if (key instanceof SettingsRegistry.MultipleChoiceSettingKey choices && !choices.getChoices().contains(converted))
            throw new IllegalArgumentException("Unsupported setting choice");
        core.getSettingsRegistry().set(key, converted);
    }
    private static JsonObject success() { JsonObject out = new JsonObject(); out.addProperty("updated", true); return out; }
    private static String text(JsonObject p, String key) {
        if (!p.has(key) || !p.get(key).isJsonPrimitive()) throw new IllegalArgumentException(key + " required"); return p.get(key).getAsString();
    }
    private static String string(JsonObject p, String key, String fallback) { return p.has(key) ? text(p, key) : fallback; }
    private static boolean bool(JsonObject p, String key, boolean fallback) { return p.has(key) ? p.get(key).getAsBoolean() : fallback; }
    private static int integer(JsonObject p, String key, int fallback) {
        double value = number(p, key, fallback);
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new IllegalArgumentException(key + " must be an integer");
        return (int) value;
    }
    private static double number(JsonObject p, String key, double fallback) {
        double value = p.has(key) ? p.get(key).getAsDouble() : fallback;
        if (!Double.isFinite(value)) throw new IllegalArgumentException(key + " must be finite"); return value;
    }
}
