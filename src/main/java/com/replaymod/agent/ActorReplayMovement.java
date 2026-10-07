package com.replaymod.agent;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.util.Identifier;
import java.util.UUID;
import java.util.function.Consumer;

/** Exact actor frames supplement vanilla packets, which remain available to replay entity indexing. */
public final class ActorReplayMovement {
    public static final Identifier CHANNEL = new Identifier("agenticreplay", "actor_frame");
    public static final String PROFILE_MARKER = "agenticreplay.actor";
    private ActorReplayMovement() { }
    public record Pose(double x, double y, double z, float yaw, float pitch, float headYaw, float bodyYaw) { }
    public record Frame(int entityId, UUID uuid, Pose pose, double vx, double vy, double vz, boolean teleport) { }
    public interface Port {
        int actorEntityId();
        UUID actorUuid();
        Pose actorPose();
        void actorApply(Frame frame);
        void actorPrevious(Pose pose);
        void actorLimbs(float speed);
    }
    public interface Target extends Port { boolean actorReplayAccept(Frame frame); }

    public static void record(Frame frame, Consumer<Packet<?>> sink) {
        PacketByteBuf data = new PacketByteBuf(Unpooled.buffer());
        try {
            write(frame, data);
            sink.accept(new CustomPayloadS2CPacket(CHANNEL, data));
        } finally { data.release(); }
    }
    public static void write(Frame frame, PacketByteBuf data) {
        data.writeVarInt(1); data.writeVarInt(frame.entityId()); data.writeUuid(frame.uuid());
        Pose p = frame.pose();
        data.writeDouble(p.x()); data.writeDouble(p.y()); data.writeDouble(p.z());
        data.writeFloat(p.yaw()); data.writeFloat(p.pitch()); data.writeFloat(p.headYaw()); data.writeFloat(p.bodyYaw());
        data.writeDouble(frame.vx()); data.writeDouble(frame.vy()); data.writeDouble(frame.vz());
        data.writeBoolean(frame.teleport());
    }
    public static Frame read(PacketByteBuf data) {
        if (data.readableBytes() > 96 || data.readVarInt() != 1) throw new IllegalArgumentException("Invalid actor frame version/size");
        Frame frame = new Frame(data.readVarInt(), data.readUuid(),
                new Pose(data.readDouble(), data.readDouble(), data.readDouble(), data.readFloat(), data.readFloat(),
                        data.readFloat(), data.readFloat()), data.readDouble(), data.readDouble(), data.readDouble(), data.readBoolean());
        Pose p = frame.pose();
        for (double value : new double[]{p.x(), p.y(), p.z(), p.yaw(), p.pitch(), p.headYaw(), p.bodyYaw(), frame.vx(), frame.vy(), frame.vz()})
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite actor frame");
        if (data.isReadable()) throw new IllegalArgumentException("Trailing actor frame data");
        return frame;
    }

    /** Apply before subsequent animation packets; retain the authored render interval through vanilla's next tick. */
    public static final class State {
        private Frame pending;
        private Pose previous;
        public boolean accept(Port port, Frame frame) {
            if (port.actorEntityId() != frame.entityId() || !port.actorUuid().equals(frame.uuid())) return false;
            previous = frame.teleport() ? frame.pose() : port.actorPose();
            pending = frame;
            port.actorApply(frame);
            port.actorPrevious(previous);
            return true;
        }
        public void afterTick(Port port) {
            if (pending == null) return;
            port.actorApply(pending);
            port.actorPrevious(previous);
            port.actorLimbs((float) Math.min(1, Math.hypot(pending.vx(), pending.vz()) * 4));
            pending = null;
        }
    }
}
