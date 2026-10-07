package com.replaymod.agent;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import java.util.UUID;

/** Preserve the source's built-in avatar even though every character needs a different entity UUID. */
public final class ActorDefaultSkin {
    public static final String SOURCE = "agenticreplay.default_skin";
    private ActorDefaultSkin() { }
    static GameProfile character(UUID uuid, String name, GameProfile source) {
        GameProfile actor = new GameProfile(uuid, name);
        actor.getProperties().putAll(source.getProperties());
        if (!source.getProperties().containsKey("textures"))
            actor.getProperties().put(SOURCE, new Property(SOURCE, source.getId().toString()));
        return actor;
    }
    public static SkinTextures textures(GameProfile profile) {
        if (profile.getProperties().containsKey("textures")) return null;
        var values = profile.getProperties().get(SOURCE);
        if (values.size() != 1) return null;
        try { return DefaultSkinHelper.getSkinTextures(UUID.fromString(values.iterator().next().value())); }
        catch (IllegalArgumentException invalid) { return null; }
    }
}
