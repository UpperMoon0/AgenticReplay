package com.replaymod.agent;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.util.UUID;
import static org.junit.Assert.*;

public class ActorReplayMovementTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static final class Player implements ActorReplayMovement.Port {
        final int id;
        final UUID uuid = UUID.randomUUID();
        ActorReplayMovement.Pose pose = pose(0), previous = pose;
        double target;
        int increments;
        Player(int id) { this.id = id; }
        public int actorEntityId() { return id; }
        public UUID actorUuid() { return uuid; }
        public ActorReplayMovement.Pose actorPose() { return pose; }
        public void actorApply(ActorReplayMovement.Frame frame) { pose = frame.pose(); increments = 0; }
        public void actorPrevious(ActorReplayMovement.Pose value) { previous = value; }
        public void actorLimbs(float speed) { }
        void vanillaPosition(double x) { target = x; increments = 3; }
        void vanillaTick() {
            previous = pose;
            if (increments > 0) pose = pose(pose.x() + (target - pose.x()) / increments--);
        }
    }
    private static ActorReplayMovement.Pose pose(double x) { return new ActorReplayMovement.Pose(x, 64, 0, 0, 0, 0, 0); }
    private static ActorReplayMovement.Frame frame(Player player, double x, boolean teleport) {
        return new ActorReplayMovement.Frame(player.id, player.uuid, pose(x), .1, 0, 0, teleport);
    }
    @Test public void movingActorReachesEndpointBeforeFollowingAnimationInsteadOfLaggingThreeTicks() {
        Player actor = new Player(-1000000000), ordinary = new Player(10);
        ActorReplayMovement.State state = new ActorReplayMovement.State();
        for (int tick = 1; tick <= 4; tick++) {
            double endpoint = tick * 2;
            actor.vanillaPosition(endpoint); ordinary.vanillaPosition(endpoint);
            assertTrue(state.accept(actor, frame(actor, endpoint, false)));
            assertEquals(endpoint, actor.pose.x(), 0); // An immediately following animation sees this endpoint.
            actor.vanillaTick(); ordinary.vanillaTick(); state.afterTick(actor);
            assertEquals(endpoint - 2, actor.previous.x(), 0);
            assertEquals(endpoint - 1, (actor.previous.x() + actor.pose.x()) / 2, 0);
        }
        assertEquals(8, actor.pose.x(), 0);
        assertTrue("Vanilla's three-tick path reproduces the reported lag", ordinary.pose.x() < 8);
        assertEquals(0, actor.increments);
    }
    @Test public void teleportSnapsImmediatelyAndRetainsNoOldRenderPosition() {
        Player actor = new Player(-1000000000);
        ActorReplayMovement.State state = new ActorReplayMovement.State();
        actor.vanillaPosition(100);
        state.accept(actor, frame(actor, 100, true));
        assertEquals(100, actor.pose.x(), 0); assertEquals(100, actor.previous.x(), 0);
        actor.vanillaTick(); state.afterTick(actor);
        assertEquals(100, actor.pose.x(), 0); assertEquals(100, actor.previous.x(), 0);
        state.accept(actor, frame(actor, 101, false)); actor.vanillaTick(); state.afterTick(actor);
        assertEquals(100, actor.previous.x(), 0); assertEquals(101, actor.pose.x(), 0);
    }
    @Test public void quickSeekUsesAuthoredPreviousPoseInsteadOfTheCurrentWorldPose() {
        Player actor = new Player(-1000000000);
        actor.pose = pose(500);
        ActorReplayMovement.State state = new ActorReplayMovement.State();
        assertTrue(state.accept(actor, frame(actor, 4, false), pose(2)));
        actor.vanillaTick(); state.afterTick(actor);
        assertEquals(4, actor.pose.x(), 0); assertEquals(2, actor.previous.x(), 0);
        state.accept(actor, frame(actor, 100, true), pose(2));
        actor.vanillaTick(); state.afterTick(actor);
        assertEquals(100, actor.previous.x(), 0);
    }
    @Test public void multipleFramesBeforeOneTickUseTheLastAuthoredInterval() {
        Player actor = new Player(-1000000000);
        ActorReplayMovement.State state = new ActorReplayMovement.State();
        state.accept(actor, frame(actor, 2, false)); state.accept(actor, frame(actor, 4, false));
        actor.vanillaTick(); state.afterTick(actor);
        assertEquals(2, actor.previous.x(), 0); assertEquals(4, actor.pose.x(), 0);
        actor.vanillaTick(); state.afterTick(actor);
        assertEquals(4, actor.previous.x(), 0); // Pending frame is consumed once, not replayed every tick.
    }
    @Test public void framesCannotMoveAnotherActorOrAReusedEntityId() {
        Player alice = new Player(-1000000000), bob = new Player(-1000000001), replacement = new Player(alice.id);
        ActorReplayMovement.State a = new ActorReplayMovement.State(), b = new ActorReplayMovement.State();
        assertFalse(a.accept(alice, frame(bob, 5, false)));
        assertFalse(a.accept(replacement, frame(alice, 5, false)));
        assertTrue(a.accept(alice, frame(alice, 2, false)));
        assertTrue(b.accept(bob, frame(bob, -3, true)));
        alice.vanillaTick(); bob.vanillaTick(); a.afterTick(alice); b.afterTick(bob);
        assertEquals(2, alice.pose.x(), 0); assertEquals(-3, bob.pose.x(), 0);
    }
    @Test public void nativePacketAndSavedReplayPreserveExactPoseVelocityAndTeleport() throws Exception {
        Player actor = new Player(-1000000000);
        var original = new ActorReplayMovement.Frame(actor.id, actor.uuid,
                new ActorReplayMovement.Pose(12.123456789, 512, -43, 91.2345f, -10.567f, 92.123f, 90.5f), .12345, 0, -.05, true);
        PacketByteBuf bytes = new PacketByteBuf(Unpooled.buffer());
        ActorReplayMovement.record(original, packet -> packet.write(bytes));
        byte[] encoded = new byte[bytes.readableBytes()]; bytes.readBytes(encoded); bytes.release();
        var registry = com.replaymod.replaystudio.protocol.PacketTypeRegistry.get(
                com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion.v1_20,
                com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State.PLAY);
        var metadata = new com.replaymod.replaystudio.replay.ReplayMetaData();
        metadata.setProtocolVersion(763); metadata.setFileFormatVersion(com.replaymod.replaystudio.replay.ReplayMetaData.CURRENT_FILE_FORMAT_VERSION);
        var file = temp.newFolder().toPath().resolve("actor.mcpr").toFile();
        var studio = new com.replaymod.replaystudio.studio.ReplayStudio();
        try (var replay = new com.replaymod.replaystudio.replay.ZipReplayFile(studio, file)) {
            replay.writeMetaData(registry, metadata);
            var packet = new com.replaymod.replaystudio.protocol.Packet(registry,
                    com.replaymod.replaystudio.protocol.PacketType.PluginMessage,
                    com.github.steveice10.netty.buffer.Unpooled.wrappedBuffer(encoded));
            try (var output = replay.writePacketData()) { output.write(150, packet); }
            replay.save();
        }
        try (var replay = new com.replaymod.replaystudio.replay.ZipReplayFile(studio, file);
             var input = replay.getPacketData(registry)) {
            var saved = input.readPacket();
            assertEquals(150, saved.getTime());
            var buffer = saved.getPacket().getBuf();
            PacketByteBuf restored = new PacketByteBuf(Unpooled.wrappedBuffer(buffer.nioBuffer()));
            var packet = new CustomPayloadS2CPacket(restored);
            var payload = packet.getData();
            try {
                assertEquals(ActorReplayMovement.CHANNEL, packet.getChannel());
                assertEquals(original, ActorReplayMovement.read(payload));
            } finally { payload.release(); restored.release(); saved.getPacket().release(); }
            assertNull(input.readPacket());
        }
    }
    @Test public void unsupportedMalformedAndNonFiniteFramesAreRejected() {
        PacketByteBuf data = new PacketByteBuf(Unpooled.buffer());
        try {
            data.writeVarInt(2);
            try { ActorReplayMovement.read(data); fail(); } catch (IllegalArgumentException expected) { }
            data.clear(); data.writeVarInt(1);
            try { ActorReplayMovement.read(data); fail(); } catch (IndexOutOfBoundsException expected) { }
            data.clear(); Player player = new Player(-1000000000);
            ActorReplayMovement.write(frame(player, Double.NaN, false), data);
            try { ActorReplayMovement.read(data); fail(); } catch (IllegalArgumentException expected) { }
            data.clear(); ActorReplayMovement.write(frame(player, 1, false), data); data.writeByte(0);
            try { ActorReplayMovement.read(data); fail(); } catch (IllegalArgumentException expected) { }
        } finally { data.release(); }
    }
}
