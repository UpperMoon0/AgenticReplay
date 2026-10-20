package com.replaymod.agent;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ActorPacketsTest {
    @Test public void embeddedReplayStudioReadsRecordedActorIdentityAndSkin() throws Exception {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "RecordedActor");
        profile.getProperties().put("textures", new Property("textures", "skin-payload", "skin-signature"));
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            ActorPackets.addProfile(profile).write(buf);
            byte[] bytes = new byte[buf.readableBytes()]; buf.readBytes(bytes);
            var registry = com.replaymod.replaystudio.protocol.PacketTypeRegistry.get(
                    com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion.v1_20,
                    com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State.PLAY);
            var packet = new com.replaymod.replaystudio.protocol.Packet(registry,
                    com.replaymod.replaystudio.protocol.PacketType.PlayerListEntry,
                    com.github.steveice10.netty.buffer.Unpooled.wrappedBuffer(bytes));
            try {
                var entries = com.replaymod.replaystudio.protocol.packets.PacketPlayerListEntry.read(packet);
                assertEquals(1, entries.size()); assertEquals(profile.getId(), entries.get(0).getUuid());
                assertEquals("RecordedActor", entries.get(0).getName());
                var encoded = com.replaymod.replaystudio.protocol.packets.PacketPlayerListEntry.write(registry,
                        com.replaymod.replaystudio.protocol.packets.PacketPlayerListEntry.getActions(packet), entries.get(0));
                PacketByteBuf restored = new PacketByteBuf(Unpooled.wrappedBuffer(encoded.getBuf().nioBuffer()));
                try {
                    Property texture = new PlayerListS2CPacket(restored).getPlayerAdditionEntries().get(0)
                            .profile().getProperties().get("textures").iterator().next();
                    assertEquals("skin-payload", texture.getValue()); assertEquals("skin-signature", texture.getSignature());
                } finally { restored.release(); encoded.release(); }
            } finally { packet.release(); }
        } finally { buf.release(); }
    }
    @Test public void twoCharactersKeepSeparateIdentityAndExactSignedSkinThroughPacketRoundTrip() {
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        Property skin = new Property("textures", "signed-texture-payload", "mojang-signature");
        for (GameProfile profile : List.of(new GameProfile(alice, "Alice"), new GameProfile(bob, "Bob"))) {
            profile.getProperties().put("textures", skin);
            var packet = roundTrip(ActorPackets.addProfile(profile));
            assertEquals(EnumSet.of(PlayerListS2CPacket.Action.ADD_PLAYER), packet.getActions());
            var entry = packet.getPlayerAdditionEntries().get(0);
            assertEquals(profile.getId(), entry.profile().getId());
            assertEquals(profile.getName(), entry.profile().getName());
            Property decoded = entry.profile().getProperties().get("textures").iterator().next();
            assertEquals(skin.getValue(), decoded.getValue()); assertEquals(skin.getSignature(), decoded.getSignature());
            assertFalse(entry.listed()); // Cast members do not clutter the real server tab list.
        }
        assertNotEquals(alice, bob);
    }
    @Test public void defaultSkinsCanBeRecordedWithoutTextureProperties() {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "DefaultActor");
        var entry = roundTrip(ActorPackets.addProfile(profile)).getPlayerAdditionEntries().get(0);
        assertTrue(entry.profile().getProperties().isEmpty()); assertEquals(profile.getId(), entry.profile().getId());
    }
    @Test public void copiedDefaultAvatarSourceSurvivesReplayPacketRoundTrip() {
        GameProfile source = new GameProfile(UUID.randomUUID(), "Source");
        GameProfile actor = ActorDefaultSkin.character(UUID.randomUUID(), "Actor", source);
        GameProfile decoded = roundTrip(ActorPackets.addProfile(actor)).getPlayerAdditionEntries().get(0).profile();
        assertEquals(ActorDefaultSkin.textures(actor), ActorDefaultSkin.textures(decoded));
        assertEquals(actor.getId(), decoded.getId()); assertNotEquals(source.getId(), decoded.getId());
    }
    @Test public void negativeActorEntityIdsAndProfileRemovalSurviveSerialization() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            new EntitiesDestroyS2CPacket(-1000000000, -1000000001).write(buf);
            var decoded = new EntitiesDestroyS2CPacket(buf);
            assertEquals(List.of(-1000000000, -1000000001), decoded.getEntityIds());
            buf.clear(); UUID id = UUID.randomUUID();
            new PlayerRemoveS2CPacket(List.of(id)).write(buf);
            assertEquals(List.of(id), new PlayerRemoveS2CPacket(buf).profileIds());
        } finally { buf.release(); }
    }
    @Test public void hurtAnimationUsesMinecraft1201DamageTiltPacket() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            new DamageTiltS2CPacket(-1000000000, 45).write(buf);
            var decoded = new DamageTiltS2CPacket(buf); assertEquals(-1000000000, decoded.id()); assertEquals(45, decoded.yaw(), 0);
        } finally { buf.release(); }
    }
    private static PlayerListS2CPacket roundTrip(PlayerListS2CPacket packet) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try { packet.write(buf); return new PlayerListS2CPacket(buf); } finally { buf.release(); }
    }
}
