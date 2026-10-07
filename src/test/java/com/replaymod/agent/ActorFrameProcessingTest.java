package com.replaymod.agent;

import com.replaymod.replaystudio.PacketData;
import com.replaymod.replaystudio.filter.DimensionTracker;
import com.replaymod.replaystudio.filter.SquashFilter;
import com.replaymod.replaystudio.protocol.*;
import com.replaymod.replaystudio.protocol.packets.PacketDestroyEntities;
import com.replaymod.replaystudio.rar.RandomAccessReplay;
import com.replaymod.replaystudio.replay.*;
import com.replaymod.replaystudio.stream.IteratorStream;
import com.replaymod.replaystudio.filter.StreamFilter;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.util.*;
import static org.junit.Assert.*;

public class ActorFrameProcessingTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private final PacketTypeRegistry registry = PacketTypeRegistry.get(ProtocolVersion.v1_20, State.PLAY);
    private final UUID uuid = UUID.randomUUID();
    private ActorReplayMovement.Frame frame(int id, UUID identity, double x, boolean teleport) {
        return new ActorReplayMovement.Frame(id, identity,
                new ActorReplayMovement.Pose(x, 64, 0, 91.234f, 0, 93.456f, 90), 0, 0, 0, teleport);
    }
    private void feed(StreamFilter filter, long time, Packet packet) throws Exception {
        PacketData data = new PacketData(time, packet);
        try { filter.onPacket(null, data); } finally { data.release(); }
    }
    private List<ActorReplayMovement.Frame> finish(StreamFilter filter) throws Exception {
        List<PacketData> packets = new ArrayList<>();
        filter.onEnd(new IteratorStream(packets.listIterator(), (StreamFilter) null), 5000);
        List<ActorReplayMovement.Frame> frames = new ArrayList<>();
        for (PacketData packet : packets) {
            try {
                var frame = ActorFramePackets.read(packet.getPacket());
                if (frame != null) frames.add(frame);
            } finally { packet.release(); }
        }
        return frames;
    }
    @Test public void actualSquashDependencyRetainsHistoryButActorWrapperKeepsOnlyLatest() throws Exception {
        SquashFilter baseline = new SquashFilter(new DimensionTracker());
        ActorFrameSquashFilter fixed = new ActorFrameSquashFilter(new DimensionTracker());
        for (int i = 0; i < 100; i++) {
            var frame = frame(-1000000000, uuid, i, false);
            feed(baseline, i * 50, ActorFramePackets.write(registry, frame));
            feed(fixed, i * 50, ActorFramePackets.write(registry, frame));
        }
        assertEquals(100, finish(baseline).size());
        var result = finish(fixed);
        assertEquals(1, result.size()); assertEquals(99, result.get(0).pose().x(), 0);
        assertTrue(result.get(0).teleport());
        baseline.release(); fixed.release();
    }
    @Test public void splitCopiesHaveIndependentLatestStateAndBoundedActorCount() throws Exception {
        ActorFrameSquashFilter original = new ActorFrameSquashFilter(new DimensionTracker());
        for (int tick = 0; tick < 100; tick++)
            for (int actor = 0; actor < 32; actor++)
                feed(original, tick * 50, ActorFramePackets.write(registry,
                        frame(-1000000000 - actor, new UUID(0, actor), tick, false)));
        ActorFrameSquashFilter split = original.copy();
        feed(original, 5000, ActorFramePackets.write(registry, frame(-1000000000, new UUID(0, 0), 200, false)));
        var prefix = finish(split); var next = finish(original);
        assertEquals(32, prefix.size()); assertEquals(32, next.size());
        assertEquals(99, prefix.get(0).pose().x(), 0); assertEquals(200, next.get(0).pose().x(), 0);
        split.release(); original.release();
    }
    @Test public void despawnRemovesBoundaryFrameAndReusedIdKeepsNewIdentity() throws Exception {
        ActorFrameSquashFilter filter = new ActorFrameSquashFilter(new DimensionTracker());
        feed(filter, 0, ActorFramePackets.write(registry, frame(-10, uuid, 1, false)));
        feed(filter, 50, PacketDestroyEntities.write(registry, -10));
        assertTrue(finish(filter.copy()).isEmpty());
        UUID replacement = UUID.randomUUID();
        feed(filter, 100, ActorFramePackets.write(registry, frame(-10, replacement, 8, false)));
        assertEquals(replacement, finish(filter).get(0).uuid()); filter.release();
    }
    @Test public void savedReplayIndexRestoresFramesOmittedByActualQuickCache() throws Exception {
        var file = temp.newFolder().toPath().resolve("quick.mcpr").toFile();
        var studio = new com.replaymod.replaystudio.studio.ReplayStudio();
        var metadata = new ReplayMetaData(); metadata.setProtocolVersion(763);
        metadata.setFileFormatVersion(ReplayMetaData.CURRENT_FILE_FORMAT_VERSION); metadata.setDuration(200);
        try (var replay = new ZipReplayFile(studio, file)) {
            replay.writeMetaData(registry, metadata);
            try (var output = replay.writePacketData()) {
                output.write(0, ActorFramePackets.write(registry, frame(-10, uuid, 0, true)));
                output.write(50, ActorFramePackets.write(registry, frame(-10, uuid, 2, false)));
                output.write(100, ActorFramePackets.write(registry, frame(-10, uuid, 100, true)));
            }
            replay.save();
        }
        try (var replay = new ZipReplayFile(studio, file)) {
            List<ActorReplayMovement.Frame> dispatched = new ArrayList<>();
            RandomAccessReplay quick = new RandomAccessReplay(replay, registry) {
                protected void dispatch(Packet packet) {
                    var frame = ActorFramePackets.read(packet); if (frame != null) dispatched.add(frame);
                }
            };
            try {
                quick.load(ignored -> {}); quick.seek(100); quick.seek(50);
                assertTrue("Dependency cache omits the extension", dispatched.isEmpty());
                ActorFrameIndex index = ActorFrameIndex.load(replay, registry);
                List<ActorFrameIndex.Selection> result = new ArrayList<>();
                index.seek(100, result::add);
                assertEquals(100, result.get(0).previous().x(), 0);
                result.clear(); index.seek(75, result::add);
                assertEquals(2, result.get(0).frame().pose().x(), 0);
                assertEquals(0, result.get(0).previous().x(), 0);
                result.clear(); index.seek(-1, result::add); assertTrue(result.isEmpty());
                result.clear(); index.seek(200, result::add);
                assertEquals(100, result.get(0).frame().pose().x(), 0);
            } finally { quick.release(); }
        }
    }
    @Test public void sparseIdleFramesSettleAndActorsSeekIndependently() {
        ActorFrameIndex index = new ActorFrameIndex();
        index.add(0, frame(-10, uuid, 0, true)); index.add(50, frame(-10, uuid, 2, false));
        index.add(75, frame(-10, uuid, 2, false)); // Older recordings may include redundant idle snapshots.
        UUID other = UUID.randomUUID(); index.add(75, frame(-11, other, 8, true));
        List<ActorFrameIndex.Selection> result = new ArrayList<>(); index.seek(99, result::add);
        assertEquals(2, result.size()); assertEquals(0, result.get(0).previous().x(), 0);
        result.clear(); index.seek(100, result::add); assertEquals(2, result.get(0).previous().x(), 0);
        assertEquals(other, result.get(1).frame().uuid());
    }
    @Test public void savedCatchUpMovesAtSameTimestampKeepTheImmediatelyPreviousPose() throws Exception {
        assertSavedCatchUpInterval(2, 4, false);
    }
    @Test public void savedTeleportThenMoveAtSameTimestampKeepsTheTeleportDestination() throws Exception {
        assertSavedCatchUpInterval(100, 101, true);
    }
    private void assertSavedCatchUpInterval(double first, double last, boolean teleport) throws Exception {
        var file = temp.newFolder().toPath().resolve("catch-up.mcpr").toFile();
        var studio = new com.replaymod.replaystudio.studio.ReplayStudio();
        var metadata = new ReplayMetaData(); metadata.setProtocolVersion(763);
        metadata.setFileFormatVersion(ReplayMetaData.CURRENT_FILE_FORMAT_VERSION); metadata.setDuration(200);
        UUID other = UUID.randomUUID();
        try (var replay = new ZipReplayFile(studio, file)) {
            replay.writeMetaData(registry, metadata);
            try (var output = replay.writePacketData()) {
                output.write(0, ActorFramePackets.write(registry, frame(-10, uuid, 0, true)));
                output.write(50, ActorFramePackets.write(registry, frame(-10, uuid, first, teleport)));
                output.write(50, ActorFramePackets.write(registry, frame(-11, other, 8, true)));
                output.write(50, ActorFramePackets.write(registry, frame(-10, uuid, last, false)));
                output.write(150, ActorFramePackets.write(registry, frame(-10, uuid, last + 1, false)));
            }
            replay.save();
        }
        try (var replay = new ZipReplayFile(studio, file)) {
            ActorFrameIndex index = ActorFrameIndex.load(replay, registry);
            for (long time : new long[]{50, 75, 200, 150, 50, 0}) {
                List<ActorFrameIndex.Selection> result = new ArrayList<>(); index.seek(time, result::add);
                var selection = result.get(0);
                assertEquals(uuid, selection.frame().uuid());
                double expectedCurrent = time == 0 ? 0 : time >= 150 ? last + 1 : last;
                double expectedPrevious = time == 0 ? 0 : time == 200 ? last + 1 : time == 150 ? last : first;
                assertEquals(expectedCurrent, selection.frame().pose().x(), 0);
                assertEquals("Authored interval at " + time, expectedPrevious, selection.previous().x(), 0);
                if (time != 0) {
                    assertEquals(2, result.size()); assertEquals(other, result.get(1).frame().uuid());
                    assertEquals(8, result.get(1).previous().x(), 0);
                }
            }
        }
    }
    @Test public void otherAndMalformedPluginPacketsRemainWithTheVanillaFilter() throws Exception {
        ActorFrameSquashFilter filter = new ActorFrameSquashFilter(new DimensionTracker());
        net.minecraft.network.PacketByteBuf bytes = new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        byte[] encoded;
        try {
            bytes.writeIdentifier(new net.minecraft.util.Identifier("example", "message")); bytes.writeByte(42);
            encoded = new byte[bytes.readableBytes()]; bytes.readBytes(encoded);
        } finally { bytes.release(); }
        feed(filter, 0, new Packet(registry, PacketType.PluginMessage,
                com.github.steveice10.netty.buffer.Unpooled.wrappedBuffer(encoded)));
        feed(filter, 50, new Packet(registry, PacketType.PluginMessage,
                com.github.steveice10.netty.buffer.Unpooled.wrappedBuffer(new byte[]{-1, -1, -1, -1, 7})));
        List<PacketData> packets = new ArrayList<>();
        filter.onEnd(new IteratorStream(packets.listIterator(), (StreamFilter) null), 100);
        assertEquals(2, packets.size());
        for (PacketData packet : packets) { assertNull(ActorFramePackets.read(packet.getPacket())); packet.release(); }
        filter.release();
    }
    @Test public void idleSuppressionStillRecordsFirstFrameRotationVelocityAndEveryTeleport() {
        var idle = frame(-10, uuid, 0, false);
        assertTrue(ActorReplayMovement.changed(null, idle)); assertFalse(ActorReplayMovement.changed(idle, idle));
        assertTrue(ActorReplayMovement.changed(idle, frame(-10, uuid, 0, true)));
        assertTrue(ActorReplayMovement.changed(idle, new ActorReplayMovement.Frame(-10, uuid, idle.pose(), .1, 0, 0, false)));
        var rotated = new ActorReplayMovement.Frame(-10, uuid,
                new ActorReplayMovement.Pose(0, 64, 0, 92, 0, 94, 90), 0, 0, 0, false);
        assertTrue(ActorReplayMovement.changed(idle, rotated));
        assertFalse(ActorReplayMovement.changed(frame(-10, uuid, 0, true), idle));
    }
}
