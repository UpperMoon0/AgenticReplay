package com.agenticreplay.legacy;

import com.google.gson.JsonArray;
import com.replaymod.agent.ApiJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.replaymod.core.ReplayMod;
import com.replaymod.core.SettingsRegistry;
import com.replaymod.core.utils.ModCompat;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.recording.ReplayModRecording;
import com.replaymod.recording.Setting;
import com.replaymod.recording.gui.GuiRecordingControls;
import com.replaymod.recording.handler.ConnectionEventHandler;
import com.replaymod.recording.packet.PacketListener;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.FullReplaySender;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.camera.CameraEntity;
import com.replaymod.replay.gui.overlay.GuiMarkerTimeline;
import com.replaymod.replaystudio.data.Marker;
import com.replaymod.replaystudio.replay.ReplayFile;
import com.replaymod.replaystudio.replay.ReplayMetaData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client-thread facade for the pinned official ReplayMod 1.7.10-2.5.2 runtime.
 * The caller dispatches on render ticks without holding a task-queue lock,
 * including while replay time is paused. Mutations are client-thread only.
 */
public final class LegacyReplayBridge {
    private static final String[] METHODS = {
            "status", "replay.list", "replay.open", "replay.close", "replay.rename", "replay.delete",
            "recording.set", "recording.marker", "playback.set", "playback.seek",
            "camera.set", "camera.options", "camera.spectate", "entities.list", "markers.list", "markers.set"
    };
    private final Minecraft mc = Minecraft.getMinecraft();
    private String openedName;
    private ReplayHandler openedHandler;
    private ReplayHandler settingsOwner;
    private Float previousFov;
    private Boolean previousHideHud;

    public LegacyReplayBridge() {
        // Fail at initialization rather than halfway through a recording mutation.
        requireField(ConnectionEventHandler.class, "guiControls", GuiRecordingControls.class);
        requireField(GuiRecordingControls.class, "buttonPauseResume", GuiButton.class);
        requireField(GuiRecordingControls.class, "buttonStartStop", GuiButton.class);
        requireField(GuiMarkerTimeline.class, "markers", Set.class);
    }

    public void prepareDisconnect() {
        if (connection() != null && connection().getPacketListener() != null) {
            core().getSettingsRegistry().set(Setting.RENAME_DIALOG, false);
        }
    }

    public static String[] methods() { return METHODS.clone(); }
    public boolean supports(String method) { return Arrays.asList(METHODS).contains(method); }

    public JsonElement call(String method, JsonObject params) throws Exception {
        JsonObject p = params == null ? new JsonObject() : params;
        restoreClosedReplayOptions();
        switch (method) {
            case "status": return status();
            case "replay.list": return listReplays();
            case "replay.open": return openReplay(p);
            case "replay.close": replay().endReplay(); restoreClosedReplayOptions(); return status();
            case "replay.rename": return renameReplay(p);
            case "replay.delete": return deleteReplay(p);
            case "recording.set": return setRecording(p);
            case "recording.marker": {
                String name = text(p, "name");
                if (name.length() > 256) throw new IllegalArgumentException("Marker name is limited to 256 characters");
                if (name.startsWith("_RM_")) throw new IllegalArgumentException("Reserved recording control marker name");
                packetListener().addMarker(name);
                return status();
            }
            case "playback.set": {
                double speed = number(p, "speed", 0);
                if (speed < 0 || speed > 16) throw new IllegalArgumentException("speed must be 0..16");
                replay().getReplaySender().setReplaySpeed(speed);
                return status();
            }
            case "playback.seek": {
                ReplayHandler handler = replay();
                int time = integer(p, "time", 0);
                boolean retain = bool(p, "retainCamera", true);
                if (time < 0 || time > handler.getReplayDuration()) throw new IllegalArgumentException("time outside replay");
                requireSeekIdle(handler);
                handler.doJump(time, retain);
                JsonObject result = status();
                result.addProperty("requestedTime", time);
                result.addProperty("seekMayStillBeRunning", ((FullReplaySender) handler.getReplaySender()).isHurrying());
                return result;
            }
            case "camera.set": return setCamera(p);
            case "camera.options": return cameraOptions(p);
            case "camera.spectate": return spectate(p);
            case "entities.list": return entities();
            case "markers.list": return markers();
            case "markers.set": return setMarkers(p);
            default: throw new UnsupportedOperationException("Method unavailable on the legacy ReplayMod bridge: " + method);
        }
    }

    /** Arms capture before connection; it cannot reconstruct already-missed login packets. */
    public void enableRecording(boolean enabled) {
        SettingsRegistry settings = core().getSettingsRegistry();
        settings.set(Setting.RECORD_SERVER, enabled);
        settings.set(Setting.RECORD_SINGLEPLAYER, enabled);
        if (enabled) {
            settings.set(Setting.AUTO_START_RECORDING, true);
            // Session-only: deliberately do not call SettingsRegistry.save().
            settings.set(Setting.RENAME_DIALOG, false);
        }
    }

    public JsonObject status() throws Exception {
        restoreClosedReplayOptions();
        JsonObject out = new JsonObject();
        ReplayHandler handler = currentReplay();
        out.addProperty("replayOpen", handler != null);
        out.addProperty("live", mc.theWorld != null && mc.thePlayer != null && handler == null);
        out.addProperty("screen", mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName());
        out.addProperty("replayModVersion", core().getVersion());
        out.add("renderCompatibility", LegacyRenderCompatibility.current());
        out.addProperty("minimalMode", ReplayMod.isMinimalMode());
        out.addProperty("recordServer", core().getSettingsRegistry().get(Setting.RECORD_SERVER));
        out.addProperty("recordSingleplayer", core().getSettingsRegistry().get(Setting.RECORD_SINGLEPLAYER));
        ConnectionEventHandler connection = connection();
        PacketListener listener = connection == null ? null : connection.getPacketListener();
        out.addProperty("recording", listener != null);
        if (listener != null) {
            out.addProperty("recordingDuration", listener.getCurrentDuration());
            GuiRecordingControls controls = recordingControls(connection);
            out.addProperty("recordingPaused", controls.isPaused());
            out.addProperty("recordingStopped", controls.isStopped());
        }
        if (handler != null) {
            if (openedName != null) out.addProperty("name", openedName);
            out.addProperty("duration", handler.getReplayDuration());
            out.addProperty("time", handler.getReplaySender().currentTimeStamp());
            out.addProperty("seekInProgress", ((FullReplaySender) handler.getReplaySender()).isHurrying());
            out.addProperty("speed", handler.getReplaySender().getReplaySpeed());
            out.addProperty("paused", handler.getReplaySender().paused());
            out.addProperty("suppressMovement", handler.shouldSuppressCameraMovements());
            out.addProperty("overlay", handler.getOverlay().isVisible());
            out.addProperty("cameraReady", handler.getCameraEntity() != null);
            CameraEntity camera = handler.getCameraEntity();
            if (camera != null) {
                JsonObject position = position(camera);
                position.addProperty("roll", camera.roll);
                position.addProperty("fov", mc.gameSettings.fovSetting);
                position.addProperty("freeCamera", handler.isCameraView());
                out.add("camera", position);
            }
        }
        return out;
    }

    private JsonArray listReplays() throws IOException {
        Path root = replayRoot();
        List<Path> paths = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "*.mcpr")) {
            for (Path path : stream) if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) paths.add(path);
        }
        Collections.sort(paths);
        JsonArray result = new JsonArray();
        for (Path path : paths) {
            JsonObject item = new JsonObject();
            item.addProperty("name", path.getFileName().toString());
            item.addProperty("bytes", Files.size(path));
            item.addProperty("modified", Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis());
            result.add(item);
        }
        return result;
    }

    private JsonObject openReplay(JsonObject p) throws Exception {
        if (mc.currentScreen instanceof GuiConnecting || mc.currentScreen instanceof GuiDownloadTerrain)
            throw new IllegalStateException("Finish or cancel the pending server connection before opening a replay");
        if (mc.theWorld != null && currentReplay() == null) throw new IllegalStateException("Disconnect before opening a replay");
        if (currentReplay() != null) throw new IllegalStateException("Close the current replay first");
        String name = text(p, "name");
        boolean allowMismatch = bool(p, "allowModMismatch", false);
        Path path = replayPath(name, true);
        ReplayFile file = core().openReplay(path);
        try {
            ReplayMetaData metadata = file.getMetaData();
            if (!ReplayMod.isCompatible(metadata.getFileFormatVersion(), metadata.getRawProtocolVersionOr0())) {
                throw new IllegalArgumentException("Replay protocol or file format is incompatible with Minecraft 1.7.10");
            }
            if (!allowMismatch) {
                ModCompat.ModInfoDifference diff = new ModCompat.ModInfoDifference(file.getModInfo());
                if (!diff.getMissing().isEmpty() || !diff.getDiffering().isEmpty()) {
                    throw new IllegalArgumentException("Replay mod mismatch; pass allowModMismatch:true explicitly to proceed");
                }
            }
            ReplayModReplay.instance.startReplay(file, false, true);
            openedName = name;
            openedHandler = currentReplay();
        } catch (Exception failure) {
            try { file.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
        return status();
    }

    private JsonObject renameReplay(JsonObject p) throws IOException {
        requireFilesIdle();
        Path source = replayPath(text(p, "name"), true);
        Path target = replayPath(text(p, "newName"), false);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("Target replay already exists");
        Files.move(source, target);
        JsonObject out = new JsonObject();
        out.addProperty("name", target.getFileName().toString());
        return out;
    }

    private JsonObject deleteReplay(JsonObject p) throws IOException {
        requireFilesIdle();
        Path source = replayPath(text(p, "name"), true);
        // Recoverable deletion: the endpoint never permanently removes a replay.
        Path trash = replayRoot().resolve("agenticreplay-trash");
        if (Files.isSymbolicLink(trash)) throw new IllegalArgumentException("Replay trash must not be a symbolic link");
        Files.createDirectories(trash);
        String name = source.getFileName().toString();
        Path target = trash.resolve(name);
        for (int i = 1; Files.exists(target, LinkOption.NOFOLLOW_LINKS); i++) target = trash.resolve(i + "-" + name);
        Files.move(source, target);
        JsonObject out = new JsonObject();
        out.addProperty("deleted", true);
        out.addProperty("recoverable", true);
        out.addProperty("trashName", target.getFileName().toString());
        return out;
    }

    private JsonObject setRecording(JsonObject p) throws Exception {
        if (currentReplay() != null) throw new IllegalStateException("Recording controls require a live client, not replay playback");
        String state = text(p, "state");
        if (!Arrays.asList("start", "pause", "resume", "stop").contains(state)) {
            throw new IllegalArgumentException("state must be start, pause, resume, stop");
        }
        ConnectionEventHandler connection = connection();
        if (connection == null || connection.getPacketListener() == null) {
            if (mc.theWorld != null) throw new IllegalStateException("Recording was not enabled before joining; reconnect to capture a complete replay");
            if (!state.equals("start") && !state.equals("stop")) throw new IllegalStateException("No recording is active");
            enableRecording(state.equals("start"));
            return status();
        }
        if (ReplayMod.isMinimalMode()) throw new UnsupportedOperationException("Pause/stop recording controls are unavailable in ReplayMod minimal mode");
        GuiRecordingControls controls = recordingControls(connection);
        GuiButton startStop = privateField(controls, "buttonStartStop", GuiButton.class);
        GuiButton pauseResume = privateField(controls, "buttonPauseResume", GuiButton.class);
        // Resolve both private fields before mutating anything. Reuse the original
        // UI action so cut/split markers, overlay state, and manual controls agree.
        core().getSettingsRegistry().set(Setting.RENAME_DIALOG, false);
        if (state.equals("start")) {
            if (controls.isStopped()) startStop.onClick();
            if (controls.isPaused()) pauseResume.onClick();
        } else if (state.equals("stop")) {
            if (!controls.isStopped()) startStop.onClick();
        } else {
            if (controls.isStopped()) throw new IllegalStateException("Recording is stopped; use start before pause/resume");
            boolean pause = state.equals("pause");
            if (controls.isPaused() != pause) pauseResume.onClick();
        }
        return status();
    }

    private JsonObject setCamera(JsonObject p) throws Exception {
        ReplayHandler handler = replay();
        requireSeekIdle(handler);
        CameraEntity camera = camera();
        if (handler.getRestrictions().isOnlyFirstPerson() || handler.getRestrictions().isOnlyRecordingPlayer()) {
            throw new IllegalStateException("The recording restricts free-camera movement");
        }
        double x = number(p, "x", camera.posX), y = number(p, "y", camera.posY), z = number(p, "z", camera.posZ);
        float yaw = angle(p, "yaw", camera.rotationYaw), pitch = angle(p, "pitch", camera.rotationPitch);
        float roll = angle(p, "roll", camera.roll);
        float fov = (float) number(p, "fov", mc.gameSettings.fovSetting);
        if (Math.abs(x) > 30000000 || Math.abs(z) > 30000000 || Math.abs(y) > 30000000) throw new IllegalArgumentException("Camera coordinates exceed world bounds");
        if (pitch < -90 || pitch > 90) throw new IllegalArgumentException("pitch must be -90..90");
        if (fov < 30 || fov > 110) throw new IllegalArgumentException("fov must be 30..110");
        if (handler.getRestrictions().isNoNoclip() || handler.getRestrictions().isNoXray()) {
            throw new UnsupportedOperationException("Absolute camera positioning is unavailable in restricted recordings");
        }
        if (p.has("fov")) {
            ownOptions(handler);
            if (previousFov == null) previousFov = mc.gameSettings.fovSetting;
            mc.gameSettings.fovSetting = fov;
        }
        handler.spectateCamera();
        camera.setCameraPosition(x, y, z);
        camera.setCameraRotation(yaw, pitch, roll);
        return status();
    }

    private JsonObject cameraOptions(JsonObject p) throws Exception {
        ReplayHandler handler = replay();
        if (p.has("viewDistance")) throw new UnsupportedOperationException("Replay viewDistance override is unavailable in this legacy adapter");
        boolean suppress = bool(p, "suppressMovement", handler.shouldSuppressCameraMovements());
        boolean overlay = bool(p, "overlay", handler.getOverlay().isVisible());
        boolean hide = bool(p, "hideHud", mc.gameSettings.hideGUI);
        if (p.has("hideHud")) {
            ownOptions(handler);
            if (previousHideHud == null) previousHideHud = mc.gameSettings.hideGUI;
            mc.gameSettings.hideGUI = hide;
        }
        handler.setSuppressCameraMovements(suppress);
        handler.getOverlay().setVisible(overlay);
        return status();
    }

    private JsonObject spectate(JsonObject p) throws Exception {
        ReplayHandler handler = replay();
        requireSeekIdle(handler);
        CameraEntity camera = camera();
        int id = integer(p, "entityId", -1);
        if (id == -1) {
            if (handler.getRestrictions().isOnlyRecordingPlayer() || handler.getRestrictions().isOnlyFirstPerson()) {
                throw new IllegalStateException("The recording restricts free-camera use");
            }
            handler.spectateCamera();
        } else {
            if (handler.getRestrictions().isOnlyRecordingPlayer()) throw new IllegalStateException("The recording restricts spectator targets");
            Entity entity = mc.theWorld.getEntityByID(id);
            if (!(entity instanceof EntityLivingBase) || !camera.canSpectate(entity)) {
                throw new IllegalArgumentException("Entity cannot be spectated; Minecraft 1.7.10 supports visible players only");
            }
            handler.spectateEntity((EntityLivingBase) entity);
        }
        return status();
    }

    private JsonArray entities() {
        replay();
        if (mc.theWorld == null) throw new IllegalStateException("Replay world is still loading");
        JsonArray result = new JsonArray();
        CameraEntity camera = currentReplay().getCameraEntity();
        for (Object value : mc.theWorld.loadedEntityList) {
            Entity entity = (Entity) value;
            JsonObject item = position(entity);
            item.addProperty("id", entity.getEntityId());
            item.addProperty("uuid", entity.getUniqueID().toString());
            item.addProperty("name", entity.getCommandSenderName());
            item.addProperty("type", entity.getClass().getSimpleName());
            item.addProperty("spectatable", camera != null && camera.canSpectate(entity));
            result.add(item);
        }
        return result;
    }

    private JsonArray markers() throws IOException {
        JsonArray out = new JsonArray();
        for (Marker marker : replay().getReplayFile().getMarkers().or(new HashSet<Marker>())) {
            JsonObject item = new JsonObject();
            item.addProperty("name", marker.getName()); item.addProperty("time", marker.getTime());
            item.addProperty("x", marker.getX()); item.addProperty("y", marker.getY()); item.addProperty("z", marker.getZ());
            item.addProperty("yaw", marker.getYaw()); item.addProperty("pitch", marker.getPitch()); item.addProperty("roll", marker.getRoll());
            out.add(item);
        }
        return out;
    }

    private JsonObject setMarkers(JsonObject p) throws Exception {
        ReplayHandler handler = replay();
        if (!p.has("markers") || !p.get("markers").isJsonArray() || p.getAsJsonArray("markers").size() > 10000) {
            throw new IllegalArgumentException("markers array required, maximum 10000");
        }
        Set<Marker> updated = new HashSet<Marker>();
        for (JsonElement element : p.getAsJsonArray("markers")) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Each marker must be an object");
            JsonObject value = element.getAsJsonObject();
            Marker marker = new Marker();
            String name = value.has("name") && !value.get("name").isJsonNull() ? text(value, "name") : null;
            if (name != null && name.length() > 256) throw new IllegalArgumentException("Marker name is limited to 256 characters");
            marker.setName(name);
            int time = integer(value, "time", 0);
            if (time < 0 || time > handler.getReplayDuration()) throw new IllegalArgumentException("marker time outside replay");
            marker.setTime(time);
            marker.setX(number(value, "x", 0)); marker.setY(number(value, "y", 0)); marker.setZ(number(value, "z", 0));
            marker.setYaw(angle(value, "yaw", 0)); marker.setPitch(angle(value, "pitch", 0)); marker.setRoll(angle(value, "roll", 0));
            updated.add(marker);
        }
        GuiMarkerTimeline timeline = handler.getOverlay().timeline;
        @SuppressWarnings("unchecked")
        Set<Marker> displayed = privateField(timeline, "markers", Set.class);
        handler.getReplayFile().writeMarkers(updated);
        displayed.clear();
        displayed.addAll(updated);
        timeline.setSelectedMarker(null);
        JsonObject out = new JsonObject();
        out.addProperty("count", updated.size());
        out.addProperty("savedOnReplayClose", true);
        return out;
    }

    /** Called from client render ticks, including after a replay closes via its UI. */
    public void restoreClosedReplayOptions() {
        if (settingsOwner != null && settingsOwner != currentReplay()) {
            if (previousFov != null) mc.gameSettings.fovSetting = previousFov;
            if (previousHideHud != null) mc.gameSettings.hideGUI = previousHideHud;
            previousFov = null; previousHideHud = null; settingsOwner = null;
        }
        if (currentReplay() != openedHandler) { openedName = null; openedHandler = null; }
    }

    private void ownOptions(ReplayHandler handler) {
        restoreClosedReplayOptions();
        settingsOwner = handler;
    }

    private static JsonObject position(Entity entity) {
        JsonObject out = new JsonObject();
        out.addProperty("x", entity.posX); out.addProperty("y", entity.posY); out.addProperty("z", entity.posZ);
        out.addProperty("yaw", entity.rotationYaw); out.addProperty("pitch", entity.rotationPitch);
        return out;
    }

    private static ReplayMod core() {
        if (ReplayMod.instance == null) throw new IllegalStateException("ReplayMod has not initialized");
        return ReplayMod.instance;
    }
    private static ReplayHandler currentReplay() {
        return ReplayModReplay.instance == null ? null : ReplayModReplay.instance.getReplayHandler();
    }
    private static ReplayHandler replay() {
        ReplayHandler result = currentReplay();
        if (result == null) throw new IllegalStateException("No replay is open");
        return result;
    }
    private static void requireSeekIdle(ReplayHandler handler) {
        if (((FullReplaySender) handler.getReplaySender()).isHurrying())
            throw new IllegalStateException("A replay seek is in progress; wait for status.seekInProgress=false");
    }
    private static CameraEntity camera() {
        CameraEntity result = replay().getCameraEntity();
        if (result == null) throw new IllegalStateException("Replay camera is still loading");
        return result;
    }
    private static ConnectionEventHandler connection() {
        return ReplayModRecording.instance == null ? null : ReplayModRecording.instance.getConnectionEventHandler();
    }
    private static PacketListener packetListener() {
        ConnectionEventHandler connection = connection();
        if (connection == null || connection.getPacketListener() == null) throw new IllegalStateException("No recording is active");
        return connection.getPacketListener();
    }
    private static GuiRecordingControls recordingControls(ConnectionEventHandler connection) {
        GuiRecordingControls result = privateField(connection, "guiControls", GuiRecordingControls.class);
        if (result == null) throw new IllegalStateException("Recording controls have not initialized");
        return result;
    }
    private static <T> T privateField(Object owner, String name, Class<T> type) {
        try {
            Class<?> declaring = owner.getClass();
            Field field = null;
            while (declaring != null && field == null) {
                try { field = declaring.getDeclaredField(name); }
                catch (NoSuchFieldException absentOnSubclass) { declaring = declaring.getSuperclass(); }
            }
            if (field == null) throw new NoSuchFieldException(name);
            field.setAccessible(true);
            return type.cast(field.get(owner));
        } catch (ReflectiveOperationException failure) {
            throw new UnsupportedOperationException("ReplayMod 1.7.10-2.5.2 compatibility field is unavailable: " + name, failure);
        }
    }
    private static Path replayRoot() throws IOException { return core().getReplayFolder().toRealPath(); }
    private static Path replayPath(String name, boolean existing) throws IOException {
        return LegacyReplayFiles.resolve(replayRoot(), name, existing);
    }
    private void requireFilesIdle() {
        if (currentReplay() != null || mc.theWorld != null || (connection() != null && connection().getPacketListener() != null)) {
            throw new IllegalStateException("Disconnect and close replay playback before changing replay files");
        }
        // Incomplete captures remain under ReplayMod's recording/ subdirectory;
        // replayPath can address only finalized files in the top-level directory.
    }
    private static void requireField(Class<?> owner, String name, Class<?> type) {
        try {
            Field field = owner.getDeclaredField(name);
            if (!type.isAssignableFrom(field.getType())) throw new NoSuchFieldException(name);
            field.setAccessible(true);
        } catch (ReflectiveOperationException failure) {
            throw new UnsupportedOperationException("Incompatible ReplayMod runtime: " + owner.getName() + "." + name, failure);
        }
    }
    private static String text(JsonObject p, String key) { return ApiJson.text(p, key); }
    private static boolean bool(JsonObject p, String key, boolean fallback) { return ApiJson.bool(p, key, fallback); }
    private static double number(JsonObject p, String key, double fallback) { return ApiJson.number(p, key, fallback); }
    private static int integer(JsonObject p, String key, int fallback) { return ApiJson.integer(p, key, fallback, Integer.MIN_VALUE, Integer.MAX_VALUE); }
    private static float angle(JsonObject p, String key, float fallback) {
        double value = number(p, key, fallback);
        if (Math.abs(value) > 360000) throw new IllegalArgumentException(key + " is outside the supported angle range");
        return (float) value;
    }
}
