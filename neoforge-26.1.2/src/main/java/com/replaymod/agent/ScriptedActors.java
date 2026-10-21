package com.replaymod.agent;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import com.replaymod.core.mixin.ActorPlayerAccessor;
import com.replaymod.core.mixin.ActorEntityAccessor;
import com.replaymod.recording.ReplayModRecording;
import com.replaymod.recording.packet.PacketListener;
import com.replaymod.replay.ReplayModReplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import java.util.*;
import java.util.concurrent.*;

/** Client-side film cast. Vanilla packets make characters persist in .mcpr and offline exports. */
final class ScriptedActors {
    private final Minecraft mc;
    private final Map<String, Actor> actors = new LinkedHashMap<>();
    private int nextEntityId = -1000000000;
    ScriptedActors(Minecraft mc) { this.mc = mc; }

    JsonObject spawn(JsonObject p) {
        requireLive();
        if (!Set.of("actorId", "name", "skin", "x", "y", "z", "yaw", "pitch").containsAll(p.keySet()))
            throw new IllegalArgumentException("Unknown actor.spawn field");
        String id = ActorTimeline.text(p, "actorId");
        if (!id.matches("[A-Za-z0-9_-]{1,48}")) throw new IllegalArgumentException("actorId must contain 1..48 letters, digits, underscore or hyphen");
        if (actors.containsKey(id)) throw new IllegalArgumentException("Actor already exists: " + id);
        if (actors.size() >= 32) throw new IllegalStateException("Maximum 32 actors per scene; despawn unused actors");
        String name = p.has("name") ? ActorTimeline.text(p, "name") : id;
        if (!name.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("Actor name must be a Minecraft player name (1..16 characters)");
        ActorTimeline.position(p);
        float yaw = p.has("yaw") ? (float) ActorTimeline.number(p, "yaw") : 0;
        float pitch = p.has("pitch") ? (float) ActorTimeline.number(p, "pitch") : 0;
        if (Math.abs(pitch) > 90 || !Float.isFinite(yaw)) throw new IllegalArgumentException("Invalid actor rotation");
        ActorSkinSpec skin = null;
        if (p.has("skin")) {
            if (!p.get("skin").isJsonObject()) throw new IllegalArgumentException("skin must be an object");
            skin = ActorSkinSpec.parse(p.getAsJsonObject("skin"));
        }
        Actor actor = new Actor(id, name, mc.level, p.get("x").getAsDouble(), p.get("y").getAsDouble(), p.get("z").getAsDouble(), yaw, pitch);
        actor.profile = ActorSkins.resolve(mc, actor.uuid, name, skin);
        actors.put(id, actor);
        return actor.state();
    }
    JsonArray list() { JsonArray out = new JsonArray(); actors.values().forEach(actor -> out.add(actor.state())); return out; }
    JsonObject state(String id) { return actor(id).state(); }
    JsonObject sequence(String id, JsonArray steps) {
        Actor actor = ready(id);
        var plan = ActorTimeline.parse(steps); actor.validate(plan); actor.timeline.start(plan);
        return actor.state();
    }
    JsonArray scene(JsonObject scripts) {
        requireLive();
        Map<String, ActorScene.Target> targets = new LinkedHashMap<>();
        for (String id : scripts.keySet()) {
            Actor actor = ready(id); targets.put(id, new ActorScene.Target(actor.timeline, actor::validate));
        }
        ActorScene.start(scripts, targets); return list();
    }
    JsonObject stop(String id) { Actor actor = actor(id); actor.timeline.stop("Stopped by API"); return actor.state(); }
    void stopAll(String reason) { actors.values().forEach(actor -> actor.timeline.stop(reason)); }
    JsonObject despawn(String id) {
        Actor actor = actor(id); actor.timeline.stop("Actor despawned");
        remove(actor); actors.remove(id);
        JsonObject out = new JsonObject(); out.addProperty("actorId", id); out.addProperty("state", "despawned"); return out;
    }
    void clear() { for (String id : List.copyOf(actors.keySet())) despawn(id); }
    void tick() {
        for (Actor actor : List.copyOf(actors.values())) {
            if (mc.level != actor.world || mc.getConnection() != actor.connection || mc.player == null || ReplayModReplay.instance.getReplayHandler() != null) {
                despawn(actor.id); continue;
            }
            if (mc.isPaused()) continue; // Script time and the recorded world share the same pause boundary.
            try {
                if (actor.state.equals("loading") && actor.profile.isDone()) {
                    GameProfile profile = actor.profile.join();
                    if (actor.textures == null) {
                        actor.textures = new CompletableFuture<>();
                        if (ActorDefaultSkin.textures(profile) != null) actor.textures.complete(null);
                        else actor.textures = mc.getSkinManager().get(profile).thenAccept(skin -> {
                            if (skin.isEmpty()) throw new IllegalArgumentException("Actor skin could not be loaded");
                        });
                        actor.textures.orTimeout(30, TimeUnit.SECONDS);
                    }
                    if (!actor.textures.isDone()) continue;
                    actor.textures.join();
                    var add = ActorPackets.addProfile(profile, mc.level.registryAccess());
                    mc.getConnection().handlePlayerInfoUpdate(add);
                    actor.entity = new RemotePlayer(actor.world, profile);
                    while (actor.world.getEntity(nextEntityId) != null) nextEntityId--;
                    actor.entity.setId(nextEntityId--);
                    actor.entity.snapTo(actor.x, actor.y, actor.z, actor.yaw, actor.pitch);
                    actor.entity.setYHeadRot(actor.yaw);
                    actor.entity.getEntityData().set(ActorPlayerAccessor.actorModelParts(), (byte) 127);
                    // Populate profile info before adding the entity; skin cache and replay spawn both rely on it.
                    actor.world.addEntity(actor.entity);
                    actor.state = "ready";
                }
                if (!actor.state.equals("ready")) continue;
                if (actor.world.getEntity(actor.entity.getId()) != actor.entity) {
                    throw new IllegalStateException("Actor entity was removed");
                }
                PacketListener recorder = recorder();
                if (recorder != actor.recorder) {
                    actor.recorder = recorder;
                    if (recorder != null) actor.recordSpawn();
                }
                actor.timeline.tick();
                actor.entity.setPose(actor.pose);
                actor.entity.setShiftKeyDown(actor.pose == Pose.CROUCHING);
                actor.entity.setSwimming(actor.pose == Pose.SWIMMING);
                ((ActorEntityAccessor) actor.entity).actorSetFlag(7, actor.pose == Pose.FALL_FLYING);
                actor.entity.setSprinting(actor.sprinting);
                actor.entity.yBodyRot = actor.entity.getYRot();
                if (!actor.timeline.active()) actor.entity.setDeltaMovement(0, 0, 0);
                actor.entity.walkAnimation.update((float) Math.min(1, actor.entity.getDeltaMovement().horizontalDistance() * 4), 0.4f, 1.0f);
                actor.recordSnapshot();
            } catch (RuntimeException failure) {
                actor.state = "failed";
                Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
                actor.error = cause.toString(); actor.timeline.fail(actor.error);
                remove(actor);
            }
        }
    }
    private void remove(Actor actor) {
        if (actor.entity != null) {
            if (actor.recorder != null && actor.recorder == recorder()) {
                actor.record(new ClientboundRemoveEntitiesPacket(actor.entity.getId()));
                actor.record(new ClientboundPlayerInfoRemovePacket(List.of(actor.uuid)));
            }
            // A world transition must clean the old cast without touching profiles in the new world.
            actor.world.removeEntity(actor.entity.getId(), Entity.RemovalReason.DISCARDED);
            if (mc.getConnection() == actor.connection)
                actor.connection.handlePlayerInfoRemove(new ClientboundPlayerInfoRemovePacket(List.of(actor.uuid)));
            actor.entity = null;
        }
        if (actor.profile != null) actor.profile.cancel(false);
    }
    private Actor actor(String id) {
        Actor actor = actors.get(id); if (actor == null) throw new IllegalArgumentException("Unknown actor: " + id); return actor;
    }
    private Actor ready(String id) {
        requireLive(); Actor actor = actor(id);
        if (actor.world != mc.level || actor.connection != mc.getConnection() || !actor.state.equals("ready"))
            throw new IllegalStateException("Actor is not ready: " + id);
        return actor;
    }
    private void requireLive() {
        if (mc.level == null || mc.player == null || mc.getConnection() == null || ReplayModReplay.instance.getReplayHandler() != null)
            throw new IllegalStateException("Join a live world before creating or scripting actors");
        if (mc.isPaused()) throw new IllegalStateException("Unpause the client before scripting actors");
    }
    private PacketListener recorder() {
        var recording = ReplayModRecording.instance;
        return recording == null || recording.getConnectionEventHandler() == null ? null : recording.getConnectionEventHandler().getPacketListener();
    }
    private final class Actor implements ActorTimeline.Port {
        final String id, name;
        final UUID uuid = UUID.randomUUID();
        final ClientLevel world;
        final net.minecraft.client.multiplayer.ClientPacketListener connection;
        final ActorTimeline timeline = new ActorTimeline(this);
        final double x, y, z;
        final float yaw, pitch;
        CompletableFuture<GameProfile> profile;
        CompletableFuture<Void> textures;
        RemotePlayer entity;
        PacketListener recorder;
        String state = "loading", error;
        Pose pose = Pose.STANDING;
        boolean sprinting;
        boolean teleportSnapshot;
        ActorReplayMovement.Frame recordedFrame;
        double startX, startY, startZ;
        float startYaw, startPitch;
        Actor(String id, String name, ClientLevel world, double x, double y, double z, float yaw, float pitch) {
            this.id = id; this.name = name; this.world = world; this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
            this.connection = mc.getConnection();
        }
        void validate(List<ActorTimeline.Step> plan) {
            for (var step : plan) if (step.action().equals("equip")) item(step.params());
        }
        @Override public void begin(ActorTimeline.Step step) {
            JsonObject p = step.params();
            startX = entity.getX(); startY = entity.getY(); startZ = entity.getZ();
            startYaw = entity.getYRot(); startPitch = entity.getXRot();
            switch (step.action()) {
                case "teleport":
                    entity.snapTo(p.get("x").getAsDouble(), p.get("y").getAsDouble(), p.get("z").getAsDouble(), entity.getYRot(), entity.getXRot());
                    teleportSnapshot = true; break;
                case "pose":
                    pose = Pose.valueOf(ActorTimeline.text(p, "pose").toUpperCase(Locale.ROOT));
                    if (p.has("sprinting")) sprinting = ActorTimeline.bool(p, "sprinting");
                    break;
                case "equip": entity.setItemSlot(EquipmentSlot.byName(ActorTimeline.text(p, "slot")), item(p)); break;
                case "swing":
                    boolean offhand = p.has("hand") && p.get("hand").getAsString().equals("offhand");
                    entity.swing(offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
                    record(new ClientboundAnimatePacket(entity, offhand ? 3 : 0)); break;
                case "hurt":
                    entity.animateHurt(0); record(new ClientboundHurtAnimationPacket(entity.getId(), 0)); break;
            }
        }
        @Override public void apply(ActorTimeline.Step step, double progress) {
            JsonObject p = step.params();
            if (step.action().equals("move")) {
                double nextX = Mth.lerp(progress, startX, p.get("x").getAsDouble());
                double nextY = Mth.lerp(progress, startY, p.get("y").getAsDouble());
                double nextZ = Mth.lerp(progress, startZ, p.get("z").getAsDouble());
                entity.setDeltaMovement(nextX - entity.getX(), nextY - entity.getY(), nextZ - entity.getZ());
                // Runs after world ticks: vanilla has retained the previous position for render interpolation.
                entity.setPos(nextX, nextY, nextZ);
                if (p.has("yaw")) {
                    entity.setYRot(startYaw + Mth.wrapDegrees(p.get("yaw").getAsFloat() - startYaw) * (float) progress);
                    entity.setYHeadRot(entity.getYRot());
                }
                if (p.has("pitch")) entity.setXRot(Mth.lerp((float) progress, startPitch, p.get("pitch").getAsFloat()));
            } else {
                entity.setDeltaMovement(0, 0, 0);
                if (step.action().equals("look")) {
                    entity.setYRot(startYaw + Mth.wrapDegrees(p.get("yaw").getAsFloat() - startYaw) * (float) progress);
                    entity.setXRot(Mth.lerp((float) progress, startPitch, p.get("pitch").getAsFloat()));
                    entity.setYHeadRot(entity.getYRot());
                    entity.yBodyRot = entity.getYRot();
                }
            }
        }
        void record(Packet<?> packet) { if (recorder != null) recorder.save(packet); }
        void recordSpawn() {
            recordedFrame = null;
            record(ActorPackets.addProfile(entity.getGameProfile(), world.registryAccess()));
            record(new ClientboundAddEntityPacket(entity.getId(), entity.getUUID(), entity.getX(), entity.getY(), entity.getZ(), entity.getXRot(), entity.getYRot(), EntityType.PLAYER, 0, entity.getDeltaMovement(), entity.getYHeadRot()));
            recorder.addRecordedPlayer(uuid);
            var tracked = entity.getEntityData().getNonDefaultValues();
            if (tracked != null) record(new ClientboundSetEntityDataPacket(entity.getId(), tracked));
            recordEquipment();
        }
        void recordSnapshot() {
            if (recorder == null) { teleportSnapshot = false; return; }
            var velocity = entity.getDeltaMovement();
            var frame = new ActorReplayMovement.Frame(entity.getId(), uuid,
                    new ActorReplayMovement.Pose(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot(), entity.yHeadRot, entity.yBodyRot),
                    velocity.x, velocity.y, velocity.z, teleportSnapshot);
            boolean changed = ActorReplayMovement.changed(recordedFrame, frame);
            if (changed) {
                record(ClientboundTeleportEntityPacket.teleport(entity.getId(), PositionMoveRotation.of(entity), Set.of(), entity.onGround()));
                record(new ClientboundRotateHeadPacket(entity, (byte) (entity.yHeadRot * 256 / 360)));
                record(new ClientboundSetEntityMotionPacket(entity.getId(), entity.getDeltaMovement()));
            }
            var tracked = entity.getEntityData().packDirty();
            if (tracked != null) record(new ClientboundSetEntityDataPacket(entity.getId(), tracked));
            recordEquipment();
            if (changed) {
                ActorReplayMovement.record(frame, this::record);
                recordedFrame = frame;
            }
            teleportSnapshot = false;
        }
        void recordEquipment() {
            List<Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
            for (EquipmentSlot slot : EquipmentSlot.values()) equipment.add(Pair.of(slot, entity.getItemBySlot(slot).copy()));
            record(new ClientboundSetEquipmentPacket(entity.getId(), equipment));
        }
        JsonObject state() {
            JsonObject out = new JsonObject();
            out.addProperty("actorId", id); out.addProperty("name", name); out.addProperty("uuid", uuid.toString());
            out.addProperty("state", state); out.addProperty("error", error);
            out.add("script", timeline.status());
            out.addProperty("recording", recorder != null && recorder == recorder());
            if (entity != null) {
                out.addProperty("entityId", entity.getId());
                out.addProperty("x", entity.getX()); out.addProperty("y", entity.getY()); out.addProperty("z", entity.getZ());
                out.addProperty("yaw", entity.getYRot()); out.addProperty("pitch", entity.getXRot());
                out.addProperty("pose", entity.getPose().name().toLowerCase(Locale.ROOT));
            }
            return out;
        }
    }
    private static ItemStack item(JsonObject p) {
        Identifier id = Identifier.tryParse(ActorTimeline.text(p, "item"));
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Unknown item: " + p.get("item"));
        try {
            var parser = new net.minecraft.commands.arguments.item.ItemParser(net.minecraft.client.Minecraft.getInstance().level.registryAccess());
            String extra = p.has("nbt") ? ActorTimeline.text(p, "nbt") : "";
            String input = ActorTimeline.text(p, "item") + (extra.startsWith("[") ? extra : "");
            var parsed = parser.parse(new com.mojang.brigadier.StringReader(input));
            var stack = new net.minecraft.commands.arguments.item.ItemInput(parsed.item(), parsed.components()).createItemStack(ActorTimeline.integer(p, "count", 1, 1, 64));
            if (!extra.isEmpty() && !extra.startsWith("[")) stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(TagParser.parseCompoundFully(extra)));
            return stack;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new IllegalArgumentException("Invalid actor equipment components", failure);
        }
    }
}
