package com.replaymod.agent;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.resources.DefaultPlayerSkin;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class ActorDefaultSkinTest {
    static GameProfile with(GameProfile p, String key, Property value) {
        var properties = com.google.common.collect.ArrayListMultimap.<String, Property>create();
        properties.putAll(p.properties()); properties.put(key, value);
        return new GameProfile(p.id(), p.name(), new com.mojang.authlib.properties.PropertyMap(properties));
    }
    @Test public void twoIndependentActorsCopyRealDefaultAvatarWithoutChangingSourceProfile() {
        GameProfile source = new GameProfile(UUID.randomUUID(), "Channel");
        GameProfile alice = ActorDefaultSkin.character(UUID.randomUUID(), "Alice", source);
        GameProfile bob = ActorDefaultSkin.character(UUID.randomUUID(), "Bob", source);
        assertNotEquals(alice.id(), bob.id()); assertNotEquals(source.id(), alice.id());
        assertEquals(DefaultPlayerSkin.get(source.id()).body().texturePath(), ActorDefaultSkin.textures(alice).texture());
        assertEquals(DefaultPlayerSkin.get(source.id()).model().getSerializedName(), ActorDefaultSkin.textures(bob).model());
        assertTrue(source.properties().isEmpty());
    }
    @Test public void emptySignedTexturePayloadKeepsSourceDefaultAvatar() {
        GameProfile source = new GameProfile(UUID.randomUUID(), "DefaultSource");
        String value = java.util.Base64.getEncoder().encodeToString(
                "{\"textures\":{}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        source = with(source, "textures", new Property("textures", value, "signature"));
        GameProfile actor = ActorDefaultSkin.character(UUID.randomUUID(), "Actor", source);
        assertEquals(DefaultPlayerSkin.get(source.id()).body().texturePath(), ActorDefaultSkin.textures(actor).texture());
        assertEquals(DefaultPlayerSkin.get(source.id()).model().getSerializedName(), ActorDefaultSkin.textures(actor).model());
        assertTrue(actor.properties().containsKey("textures"));
    }
    @Test public void signedSkinsAndOrdinaryPlayersUseNormalSkinProvider() {
        GameProfile source = new GameProfile(UUID.randomUUID(), "Channel");
        assertNull(ActorDefaultSkin.textures(source));
        source = with(source, "textures", new Property("textures", "value", "signature"));
        GameProfile actor = ActorDefaultSkin.character(UUID.randomUUID(), "Actor", source);
        assertNull(ActorDefaultSkin.textures(actor));
        assertFalse(actor.properties().containsKey(ActorDefaultSkin.SOURCE));
        actor = with(new GameProfile(actor.id(), actor.name()), ActorDefaultSkin.SOURCE, new Property(ActorDefaultSkin.SOURCE, "invalid"));
        assertNull(ActorDefaultSkin.textures(actor));
    }
}
