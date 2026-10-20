package com.replaymod.agent;

import com.google.gson.JsonObject;
import com.replaymod.replaystudio.PacketData;
import com.replaymod.replaystudio.Studio;
import com.replaymod.replaystudio.filter.*;
import com.replaymod.replaystudio.protocol.*;
import com.replaymod.replaystudio.protocol.packets.PacketDestroyEntities;
import com.replaymod.replaystudio.stream.PacketStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Actor frames are state, not generic plugin history: keep one per live entity at cut/split boundaries. */
public final class ActorFrameSquashFilter implements StreamFilter {
    private final SquashFilter vanilla;
    private record Saved(PacketTypeRegistry registry, ActorReplayMovement.Frame frame) { }
    private final Map<Integer, Saved> latest = new LinkedHashMap<>();
    public ActorFrameSquashFilter(DimensionTracker dimensions) { this(new SquashFilter(dimensions)); }
    private ActorFrameSquashFilter(SquashFilter vanilla) { this.vanilla = vanilla; }
    public ActorFrameSquashFilter copy() {
        ActorFrameSquashFilter copy = new ActorFrameSquashFilter(vanilla.copy());
        copy.latest.putAll(latest); return copy;
    }
    public void release() { latest.clear(); vanilla.release(); }
    @Override public String getName() { return "actor-aware-squash"; }
    @Override public void init(Studio studio, JsonObject config) { vanilla.init(studio, config); }
    @Override public void onStart(PacketStream stream) throws IOException { vanilla.onStart(stream); }
    @Override public boolean onPacket(PacketStream stream, PacketData packet) throws IOException {
        var frame = ActorFramePackets.read(packet.getPacket());
        if (frame != null) {
            latest.put(frame.entityId(), new Saved(packet.getPacket().getRegistry(), frame));
            return false;
        }
        var type = packet.getPacket().getType();
        if (type == PacketType.DestroyEntity || type == PacketType.DestroyEntities)
            for (int id : PacketDestroyEntities.getEntityIds(packet.getPacket())) latest.remove(id);
        else if (type == PacketType.JoinGame || type == PacketType.Respawn) latest.clear();
        return vanilla.onPacket(stream, packet);
    }
    @Override public void onEnd(PacketStream stream, long time) throws IOException {
        vanilla.onEnd(stream, time);
        for (var saved : latest.values()) {
            var f = saved.frame();
            var snap = new ActorReplayMovement.Frame(f.entityId(), f.uuid(), f.pose(), f.vx(), f.vy(), f.vz(), true);
            stream.insert(time, ActorFramePackets.write(saved.registry(), snap));
        }
        latest.clear();
    }
}
