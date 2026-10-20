package com.replaymod.agent;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.core.RegistryAccess;
import java.util.EnumSet;
final class ActorPackets {
    static ClientboundPlayerInfoUpdatePacket addProfile(GameProfile profile, RegistryAccess registries) {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            buf.writeEnumSet(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER), ClientboundPlayerInfoUpdatePacket.Action.class);
            buf.writeVarInt(1); buf.writeUUID(profile.id());
            ByteBufCodecs.PLAYER_NAME.encode(buf, profile.name());
            ByteBufCodecs.GAME_PROFILE_PROPERTIES.encode(buf, profile.properties());
            return ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(buf);
        } finally { buf.release(); }
    }
}
