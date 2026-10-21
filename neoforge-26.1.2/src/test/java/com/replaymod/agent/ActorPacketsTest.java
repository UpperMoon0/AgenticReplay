package com.replaymod.agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.*;
import io.netty.buffer.Unpooled;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class ActorPacketsTest {
    private ClientboundPlayerInfoUpdatePacket roundTrip(GameProfile profile) {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.encode(buf, ActorPackets.addProfile(profile, RegistryAccess.EMPTY));
            return ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(buf);
        } finally { buf.release(); }
    }
    @Test public void independentActorsRetainIdentityAndSignedSkins() {
        var skin = new Property("textures", "signed-payload", "signature");
        for (String name : List.of("Alice", "Bob")) {
            var p = ActorDefaultSkinTest.with(new GameProfile(UUID.randomUUID(), name), "textures", skin);
            var packet = roundTrip(p);
            assertEquals(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER), packet.actions());
            var entry = packet.newEntries().getFirst();
            assertEquals(p, entry.profile()); assertFalse(entry.listed());
            assertEquals(skin, entry.profile().properties().get("textures").iterator().next());
        }
    }
    @Test public void replayStudioUnderstandsModernProfilePacket() throws Exception {
        var p = ActorDefaultSkinTest.with(new GameProfile(UUID.randomUUID(), "RecordedActor"), "textures", new Property("textures", "skin", "signature"));
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.encode(buf, ActorPackets.addProfile(p, RegistryAccess.EMPTY));
            byte[] bytes = new byte[buf.readableBytes()]; buf.readBytes(bytes);
            var registry = com.replaymod.replaystudio.protocol.PacketTypeRegistry.get(com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion.getProtocol(775), com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State.PLAY);
            var packet = new com.replaymod.replaystudio.protocol.Packet(registry, com.replaymod.replaystudio.protocol.PacketType.PlayerListEntry, io.netty.buffer.Unpooled.wrappedBuffer(bytes));
            try {
                var entries = com.replaymod.replaystudio.protocol.packets.PacketPlayerListEntry.read(packet);
                assertEquals(p.id(), entries.getFirst().getUuid()); assertEquals(p.name(), entries.getFirst().getName());
            } finally { packet.release(); }
        } finally { buf.release(); }
    }
    @Test public void defaultAvatarMarkerSurvivesProfileSerialization() {
        var p = ActorDefaultSkin.character(UUID.randomUUID(), "Actor", new GameProfile(UUID.randomUUID(), "Source"));
        assertEquals(ActorDefaultSkin.textures(p), ActorDefaultSkin.textures(roundTrip(p).newEntries().getFirst().profile()));
    }
    @Test public void negativeEntityIdsAndHurtAnimationSurviveCodecs() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var removal = new ClientboundRemoveEntitiesPacket(-1000000000, -1000000001);
            ClientboundRemoveEntitiesPacket.STREAM_CODEC.encode(buf, removal);
            assertEquals(removal.getEntityIds(), ClientboundRemoveEntitiesPacket.STREAM_CODEC.decode(buf).getEntityIds());
            buf.clear();
            ClientboundHurtAnimationPacket.STREAM_CODEC.encode(buf, new ClientboundHurtAnimationPacket(-1000000000, 45));
            var hurt = ClientboundHurtAnimationPacket.STREAM_CODEC.decode(buf);
            assertEquals(-1000000000, hurt.id()); assertEquals(45, hurt.yaw(), 0);
        } finally { buf.release(); }
    }
}
