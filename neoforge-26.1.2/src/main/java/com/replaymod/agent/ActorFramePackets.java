package com.replaymod.agent;

import com.replaymod.replaystudio.protocol.Packet;
import com.replaymod.replaystudio.protocol.PacketType;
import com.replaymod.replaystudio.protocol.PacketTypeRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

/** ReplayStudio's shaded buffers must not be retained by the actor-frame index/filter. */
public final class ActorFramePackets {
    private ActorFramePackets() { }
    public static ActorReplayMovement.Frame read(Packet packet) {
        if (packet.getType() != PacketType.PluginMessage) return null;
        FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.wrappedBuffer(packet.getBuf().nioBuffer()));
        try {
            if (!ActorReplayMovement.CHANNEL.equals(data.readIdentifier())) return null;
            return ActorReplayMovement.read(data);
        } catch (IllegalArgumentException | IndexOutOfBoundsException | io.netty.handler.codec.DecoderException malformed) {
            return null;
        } finally { data.release(); }
    }
    public static Packet write(PacketTypeRegistry registry, ActorReplayMovement.Frame frame) {
        FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            bytes.writeIdentifier(ActorReplayMovement.CHANNEL);
            ActorReplayMovement.write(frame, bytes);
            byte[] encoded = new byte[bytes.readableBytes()]; bytes.readBytes(encoded);
            return new Packet(registry, PacketType.PluginMessage,
                    io.netty.buffer.Unpooled.wrappedBuffer(encoded));
        } finally { bytes.release(); }
    }
}
