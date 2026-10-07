package com.replaymod.agent;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;
import java.util.UUID;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonParser;

/** Preserve the source's built-in avatar even though every character needs a different entity UUID. */
public final class ActorDefaultSkin {
    public static final String SOURCE = "agenticreplay.default_skin";
    private ActorDefaultSkin() { }
    static GameProfile character(UUID uuid, String name, GameProfile source) {
        GameProfile actor = new GameProfile(uuid, name);
        actor.getProperties().putAll(source.getProperties());
        actor.getProperties().put(ActorReplayMovement.PROFILE_MARKER, new Property(ActorReplayMovement.PROFILE_MARKER, "1"));
        if (!hasCustomSkin(source))
            actor.getProperties().put(SOURCE, new Property(SOURCE, source.getId().toString()));
        return actor;
    }
    private static boolean hasCustomSkin(GameProfile profile) {
        var textures = profile.getProperties().get("textures");
        if (textures.isEmpty()) return false;
        try {
            var json = JsonParser.parseString(new String(Base64.getDecoder().decode(
                    textures.iterator().next().getValue()), StandardCharsets.UTF_8)).getAsJsonObject();
            var values = json.getAsJsonObject("textures");
            return values == null || values.has("SKIN");
        } catch (RuntimeException malformed) {
            return true; // Malformed properties stay with vanilla's validation path.
        }
    }
    public record Avatar(Identifier texture, String model) {}
    public static Avatar textures(GameProfile profile) {
        if (hasCustomSkin(profile)) return null;
        var values = profile.getProperties().get(SOURCE);
        if (values.size() != 1) return null;
        try {
            UUID source = UUID.fromString(values.iterator().next().getValue());
            return new Avatar(DefaultSkinHelper.getTexture(source), DefaultSkinHelper.getModel(source));
        }
        catch (IllegalArgumentException invalid) { return null; }
    }
}
