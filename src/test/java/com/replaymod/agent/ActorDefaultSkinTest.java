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
        assertEquals(DefaultSkinHelper.getSkinTextures(source).texture(), ActorDefaultSkin.textures(alice).texture());
        assertEquals(DefaultSkinHelper.getSkinTextures(source).model(), ActorDefaultSkin.textures(bob).model());
        assertTrue(source.getProperties().isEmpty());
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
