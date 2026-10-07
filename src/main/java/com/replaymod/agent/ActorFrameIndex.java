package com.replaymod.agent;

import com.replaymod.replaystudio.protocol.PacketTypeRegistry;
import com.replaymod.replaystudio.replay.ReplayFile;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/** Companion to RandomAccessReplay, whose cache intentionally omits PluginMessage packets. */
public final class ActorFrameIndex {
    public record Selection(ActorReplayMovement.Frame frame, ActorReplayMovement.Pose previous) { }
    private final Map<UUID, NavigableMap<Long, Selection>> actors = new LinkedHashMap<>();
    public static ActorFrameIndex load(ReplayFile replay, PacketTypeRegistry registry) throws IOException {
        ActorFrameIndex index = new ActorFrameIndex();
        try (var input = replay.getPacketData(registry)) {
            com.replaymod.replaystudio.PacketData packet;
            while ((packet = input.readPacket()) != null) {
                try {
                    var frame = ActorFramePackets.read(packet.getPacket());
                    if (frame != null) index.add(packet.getTime(), frame);
                } finally { packet.release(); }
            }
        }
        return index;
    }
    public void add(long time, ActorReplayMovement.Frame frame) {
        var history = actors.computeIfAbsent(frame.uuid(), ignored -> new TreeMap<>());
        var previous = history.isEmpty() ? null : history.lastEntry().getValue().frame();
        if (previous != null && !ActorReplayMovement.changed(previous, frame)) return;
        // Read the preceding packet before replacing a timestamp bucket: catch-up ticks may share a millisecond.
        var pose = frame.teleport() || previous == null ? frame.pose() : previous.pose();
        history.put(time, new Selection(frame, pose));
    }
    public void seek(long time, Consumer<Selection> sink) {
        for (var history : actors.values()) {
            var current = history.floorEntry(time);
            if (current == null) continue;
            var selection = current.getValue();
            var frame = selection.frame();
            var pose = time - current.getKey() >= 50 ? frame.pose() : selection.previous();
            sink.accept(new Selection(frame, pose));
        }
    }
}
