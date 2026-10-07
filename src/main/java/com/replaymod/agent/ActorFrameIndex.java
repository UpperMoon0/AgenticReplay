package com.replaymod.agent;

import com.replaymod.replaystudio.protocol.PacketTypeRegistry;
import com.replaymod.replaystudio.replay.ReplayFile;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/** Companion to RandomAccessReplay, whose cache intentionally omits PluginMessage packets. */
public final class ActorFrameIndex {
    public record Selection(ActorReplayMovement.Frame frame, ActorReplayMovement.Pose previous) { }
    private final Map<UUID, NavigableMap<Long, ActorReplayMovement.Frame>> actors = new LinkedHashMap<>();
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
        if (!history.isEmpty() && !ActorReplayMovement.changed(history.lastEntry().getValue(), frame)) return;
        history.put(time, frame);
    }
    public void seek(long time, Consumer<Selection> sink) {
        for (var history : actors.values()) {
            var current = history.floorEntry(time);
            if (current == null) continue;
            var frame = current.getValue();
            var previous = history.lowerEntry(current.getKey());
            var pose = frame.teleport() || previous == null || time - current.getKey() >= 50
                    ? frame.pose() : previous.getValue().pose();
            sink.accept(new Selection(frame, pose));
        }
    }
}
