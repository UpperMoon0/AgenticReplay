package com.replaymod.agent;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.util.DefaultSkinHelper;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class ActorDefaultSkinTest {
    @Test public void twoIndependentActorsCopyRealDefaultAvatarWithoutChangingSourceProfile() {
        GameProfile source = new GameProfile(UUID.randomUUID(), "Source");
        GameProfile alice = ActorDefaultSkin.character(UUID.randomUUID(), "Alice", source);
        GameProfile bob = ActorDefaultSkin.character(UUID.randomUUID(), "Bob", source);
        assertNotEquals(alice.getId(), bob.getId()); assertNotEquals(source.getId(), alice.getId());
        assertEquals(DefaultSkinHelper.getTexture(source.getId()), ActorDefaultSkin.textures(alice).texture());
        assertEquals(DefaultSkinHelper.getModel(source.getId()), ActorDefaultSkin.textures(bob).model());
        assertTrue(source.getProperties().isEmpty());
    }
    @Test public void emptySignedTexturePayloadKeepsSourceDefaultAvatar() {
        GameProfile source = new GameProfile(UUID.randomUUID(), "DefaultSource");
        String value = java.util.Base64.getEncoder().encodeToString(
                "{\"textures\":{}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        source.getProperties().put("textures", new Property("textures", value, "signature"));
        GameProfile actor = ActorDefaultSkin.character(UUID.randomUUID(), "Actor", source);
        assertEquals(DefaultSkinHelper.getTexture(source.getId()), ActorDefaultSkin.textures(actor).texture());
        assertEquals(DefaultSkinHelper.getModel(source.getId()), ActorDefaultSkin.textures(actor).model());
        assertTrue(actor.getProperties().containsKey("textures"));
    }
    @Test public void signedSkinsAndOrdinaryPlayersUseNormalSkinProvider() {
        GameProfile source = new GameProfile(UUID.randomUUID(), "Source");
        assertNull(ActorDefaultSkin.textures(source));
        source.getProperties().put("textures", new Property("textures", "value", "signature"));
        GameProfile actor = ActorDefaultSkin.character(UUID.randomUUID(), "Actor", source);
        assertNull(ActorDefaultSkin.textures(actor));
        assertFalse(actor.getProperties().containsKey(ActorDefaultSkin.SOURCE));
        actor.getProperties().removeAll("textures");
        actor.getProperties().put(ActorDefaultSkin.SOURCE, new Property(ActorDefaultSkin.SOURCE, "invalid"));
        assertNull(ActorDefaultSkin.textures(actor));
    }
}
