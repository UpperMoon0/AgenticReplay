package com.replaymod.agent;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import java.util.UUID;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonParser;

/** Preserve the source's built-in avatar even though every character needs a different entity UUID. */
public final class ActorDefaultSkin {
    public static final String SOURCE = "agenticreplay.default_skin";
    private ActorDefaultSkin() { }
    static GameProfile character(UUID uuid, String name, GameProfile source) {
        var properties = com.google.common.collect.ArrayListMultimap.<String, Property>create();
        properties.putAll(source.properties());
        properties.put(ActorReplayMovement.PROFILE_MARKER, new Property(ActorReplayMovement.PROFILE_MARKER, "1"));
        if (!hasCustomSkin(source)) properties.put(SOURCE, new Property(SOURCE, source.id().toString()));
        return new GameProfile(uuid, name, new com.mojang.authlib.properties.PropertyMap(properties));
    }
    private static boolean hasCustomSkin(GameProfile profile) {
        var textures = profile.properties().get("textures");
        if (textures.isEmpty()) return false;
        try {
            var json = JsonParser.parseString(new String(Base64.getDecoder().decode(
                    textures.iterator().next().value()), StandardCharsets.UTF_8)).getAsJsonObject();
            var values = json.getAsJsonObject("textures");
            return values == null || values.has("SKIN");
        } catch (RuntimeException malformed) {
            return true; // Malformed properties stay with vanilla's validation path.
        }
    }
    public record Avatar(Identifier texture, String model) {}
    public static Avatar textures(GameProfile profile) {
        if (hasCustomSkin(profile)) return null;
        var values = profile.properties().get(SOURCE);
        if (values.size() != 1) return null;
        try {
            UUID source = UUID.fromString(values.iterator().next().value());
            return new Avatar(DefaultPlayerSkin.get(source).body().texturePath(), DefaultPlayerSkin.get(source).model().getSerializedName());
        }
        catch (IllegalArgumentException invalid) { return null; }
    }
}
