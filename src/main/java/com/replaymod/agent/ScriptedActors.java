package com.replaymod.agent;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import com.replaymod.core.mixin.ActorPlayerAccessor;
import com.replaymod.core.mixin.ActorEntityAccessor;
import com.replaymod.recording.ReplayModRecording;
import com.replaymod.recording.packet.PacketListener;
import com.replaymod.replay.ReplayModReplay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.*;
import net.minecraft.util.math.MathHelper;
import java.util.*;
import java.util.concurrent.*;

/** Client-side film cast. Vanilla packets make characters persist in .mcpr and offline exports. */
final class ScriptedActors {
    private final MinecraftClient mc;
    private final Map<String, Actor> actors = new LinkedHashMap<>();
    private int nextEntityId = -1000000000;
    ScriptedActors(MinecraftClient mc) { this.mc = mc; }

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
        Actor actor = new Actor(id, name, mc.world, p.get("x").getAsDouble(), p.get("y").getAsDouble(), p.get("z").getAsDouble(), yaw, pitch);
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
            if (mc.world != actor.world || mc.getNetworkHandler() != actor.connection || mc.player == null || ReplayModReplay.instance.getReplayHandler() != null) {
                despawn(actor.id); continue;
            }
            if (mc.isPaused()) continue; // Script time and the recorded world share the same pause boundary.
            try {
                if (actor.state.equals("loading") && actor.profile.isDone()) {
                    GameProfile profile = actor.profile.join();
                    if (actor.textures == null) {
                        actor.textures = new CompletableFuture<>();
                        if (ActorDefaultSkin.textures(profile) != null) actor.textures.complete(null);
                        else mc.getSkinProvider().loadSkin(profile, (type, texture, value) -> {
                            if (type == com.mojang.authlib.minecraft.MinecraftProfileTexture.Type.SKIN)
                                actor.textures.complete(null);
                        }, true);
                        actor.textures.orTimeout(30, TimeUnit.SECONDS);
                    }
                    if (!actor.textures.isDone()) continue;
                    actor.textures.join();
                    var add = ActorPackets.addProfile(profile);
                    mc.getNetworkHandler().onPlayerList(add);
                    actor.entity = new OtherClientPlayerEntity(actor.world, profile);
                    while (actor.world.getEntityById(nextEntityId) != null) nextEntityId--;
                    actor.entity.setId(nextEntityId--);
                    actor.entity.refreshPositionAndAngles(actor.x, actor.y, actor.z, actor.yaw, actor.pitch);
                    actor.entity.setHeadYaw(actor.yaw);
                    actor.entity.getDataTracker().set(ActorPlayerAccessor.actorModelParts(), (byte) 127);
                    // Populate profile info before adding the entity; skin cache and replay spawn both rely on it.
                    actor.world.addPlayer(actor.entity.getId(), actor.entity);
                    actor.state = "ready";
                }
                if (!actor.state.equals("ready")) continue;
                if (actor.world.getEntityById(actor.entity.getId()) != actor.entity) {
                    throw new IllegalStateException("Actor entity was removed");
                }
                PacketListener recorder = recorder();
                if (recorder != actor.recorder) {
                    actor.recorder = recorder;
                    if (recorder != null) actor.recordSpawn();
                }
                actor.timeline.tick();
                actor.entity.setPose(actor.pose);
                actor.entity.setSneaking(actor.pose == EntityPose.CROUCHING);
                actor.entity.setSwimming(actor.pose == EntityPose.SWIMMING);
                ((ActorEntityAccessor) actor.entity).actorSetFlag(7, actor.pose == EntityPose.FALL_FLYING);
                actor.entity.setSprinting(actor.sprinting);
                actor.entity.bodyYaw = actor.entity.getYaw();
                if (!actor.timeline.active()) actor.entity.setVelocity(0, 0, 0);
                actor.entity.limbAnimator.updateLimbs((float) Math.min(1, actor.entity.getVelocity().horizontalLength() * 4), 0.4f);
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
                actor.record(new EntitiesDestroyS2CPacket(actor.entity.getId()));
                actor.record(new PlayerRemoveS2CPacket(List.of(actor.uuid)));
            }
            // A world transition must clean the old cast without touching profiles in the new world.
            actor.world.removeEntity(actor.entity.getId(), Entity.RemovalReason.DISCARDED);
            if (mc.getNetworkHandler() == actor.connection)
                actor.connection.onPlayerRemove(new PlayerRemoveS2CPacket(List.of(actor.uuid)));
            actor.entity = null;
        }
        if (actor.profile != null) actor.profile.cancel(false);
    }
    private Actor actor(String id) {
        Actor actor = actors.get(id); if (actor == null) throw new IllegalArgumentException("Unknown actor: " + id); return actor;
    }
    private Actor ready(String id) {
        requireLive(); Actor actor = actor(id);
        if (actor.world != mc.world || actor.connection != mc.getNetworkHandler() || !actor.state.equals("ready"))
            throw new IllegalStateException("Actor is not ready: " + id);
        return actor;
    }
    private void requireLive() {
        if (mc.world == null || mc.player == null || mc.getNetworkHandler() == null || ReplayModReplay.instance.getReplayHandler() != null)
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
        final ClientWorld world;
        final net.minecraft.client.network.ClientPlayNetworkHandler connection;
        final ActorTimeline timeline = new ActorTimeline(this);
        final double x, y, z;
        final float yaw, pitch;
        CompletableFuture<GameProfile> profile;
        CompletableFuture<Void> textures;
        OtherClientPlayerEntity entity;
        PacketListener recorder;
        String state = "loading", error;
        EntityPose pose = EntityPose.STANDING;
        boolean sprinting;
        double startX, startY, startZ;
        float startYaw, startPitch;
        Actor(String id, String name, ClientWorld world, double x, double y, double z, float yaw, float pitch) {
            this.id = id; this.name = name; this.world = world; this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
            this.connection = mc.getNetworkHandler();
        }
        void validate(List<ActorTimeline.Step> plan) {
            for (var step : plan) if (step.action().equals("equip")) item(step.params());
        }
        @Override public void begin(ActorTimeline.Step step) {
            JsonObject p = step.params();
            startX = entity.getX(); startY = entity.getY(); startZ = entity.getZ();
            startYaw = entity.getYaw(); startPitch = entity.getPitch();
            switch (step.action()) {
                case "teleport": entity.refreshPositionAndAngles(p.get("x").getAsDouble(), p.get("y").getAsDouble(), p.get("z").getAsDouble(), entity.getYaw(), entity.getPitch()); break;
                case "pose":
                    pose = EntityPose.valueOf(ActorTimeline.text(p, "pose").toUpperCase(Locale.ROOT));
                    if (p.has("sprinting")) sprinting = ActorTimeline.bool(p, "sprinting");
                    break;
                case "equip": entity.equipStack(EquipmentSlot.byName(ActorTimeline.text(p, "slot")), item(p)); break;
                case "swing":
                    boolean offhand = p.has("hand") && p.get("hand").getAsString().equals("offhand");
                    entity.swingHand(offhand ? Hand.OFF_HAND : Hand.MAIN_HAND);
                    record(new EntityAnimationS2CPacket(entity, offhand ? 3 : 0)); break;
                case "hurt":
                    entity.animateDamage(0); record(new DamageTiltS2CPacket(entity.getId(), 0)); break;
            }
        }
        @Override public void apply(ActorTimeline.Step step, double progress) {
            JsonObject p = step.params();
            if (step.action().equals("move")) {
                double nextX = MathHelper.lerp(progress, startX, p.get("x").getAsDouble());
                double nextY = MathHelper.lerp(progress, startY, p.get("y").getAsDouble());
                double nextZ = MathHelper.lerp(progress, startZ, p.get("z").getAsDouble());
                entity.setVelocity(nextX - entity.getX(), nextY - entity.getY(), nextZ - entity.getZ());
                // Runs after world ticks: vanilla has retained the previous position for render interpolation.
                entity.setPosition(nextX, nextY, nextZ);
                if (p.has("yaw")) {
                    entity.setYaw(startYaw + MathHelper.wrapDegrees(p.get("yaw").getAsFloat() - startYaw) * (float) progress);
                    entity.setHeadYaw(entity.getYaw());
                }
                if (p.has("pitch")) entity.setPitch(MathHelper.lerp((float) progress, startPitch, p.get("pitch").getAsFloat()));
            } else {
                entity.setVelocity(0, 0, 0);
                if (step.action().equals("look")) {
                    entity.setYaw(startYaw + MathHelper.wrapDegrees(p.get("yaw").getAsFloat() - startYaw) * (float) progress);
                    entity.setPitch(MathHelper.lerp((float) progress, startPitch, p.get("pitch").getAsFloat()));
                    entity.setHeadYaw(entity.getYaw());
                    entity.bodyYaw = entity.getYaw();
                }
            }
        }
        void record(Packet<?> packet) { if (recorder != null) recorder.save(packet); }
        void recordSpawn() {
            record(ActorPackets.addProfile(entity.getGameProfile()));
            record(new PlayerSpawnS2CPacket(entity));
            recorder.addRecordedPlayer(uuid);
            var tracked = entity.getDataTracker().getChangedEntries();
            if (tracked != null) record(new EntityTrackerUpdateS2CPacket(entity.getId(), tracked));
            recordEquipment();
        }
        void recordSnapshot() {
            if (recorder == null) return;
            record(new EntityPositionS2CPacket(entity));
            record(new EntitySetHeadYawS2CPacket(entity, (byte) (entity.headYaw * 256 / 360)));
            record(new EntityVelocityUpdateS2CPacket(entity.getId(), entity.getVelocity()));
            var tracked = entity.getDataTracker().getDirtyEntries();
            if (tracked != null) record(new EntityTrackerUpdateS2CPacket(entity.getId(), tracked));
            recordEquipment();
        }
        void recordEquipment() {
            List<Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
            for (EquipmentSlot slot : EquipmentSlot.values()) equipment.add(Pair.of(slot, entity.getEquippedStack(slot).copy()));
            record(new EntityEquipmentUpdateS2CPacket(entity.getId(), equipment));
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
                out.addProperty("yaw", entity.getYaw()); out.addProperty("pitch", entity.getPitch());
                out.addProperty("pose", entity.getPose().name().toLowerCase(Locale.ROOT));
            }
            return out;
        }
    }
    private static ItemStack item(JsonObject p) {
        Identifier id = Identifier.tryParse(ActorTimeline.text(p, "item"));
        if (id == null || !Registries.ITEM.containsId(id)) throw new IllegalArgumentException("Unknown item: " + p.get("item"));
        ItemStack stack = new ItemStack(Registries.ITEM.get(id), ActorTimeline.integer(p, "count", 1, 1, 64));
        if (p.has("nbt")) try { stack.setNbt(StringNbtReader.parse(ActorTimeline.text(p, "nbt"))); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) { throw new IllegalArgumentException("Invalid equipment NBT", failure); }
        return stack;
    }
}
