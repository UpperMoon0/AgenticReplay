package com.replaymod.agent;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import java.util.EnumSet;

/** Use Minecraft's packet decoder so profile properties, including skin signatures, survive replays. */
final class ActorPackets {
    static PlayerListS2CPacket addProfile(GameProfile profile) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            buf.writeEnumSet(EnumSet.of(PlayerListS2CPacket.Action.ADD_PLAYER), PlayerListS2CPacket.Action.class);
            buf.writeVarInt(1);
            buf.writeUuid(profile.getId());
            buf.writeString(profile.getName(), 16);
            buf.writePropertyMap(profile.getProperties());
            return new PlayerListS2CPacket(buf);
        } finally { buf.release(); }
    }
}
