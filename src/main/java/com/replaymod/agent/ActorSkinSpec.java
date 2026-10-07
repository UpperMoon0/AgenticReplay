package com.replaymod.agent;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.*;

/** Skin identity is separate from the actor UUID, so two characters can share a real skin. */
record ActorSkinSpec(String username, UUID uuid, String value, String signature) {
    static ActorSkinSpec parse(JsonObject p) {
        if (!Set.of("username", "uuid", "value", "signature").containsAll(p.keySet()))
            throw new IllegalArgumentException("Unknown skin field");
        int sources = (p.has("username") ? 1 : 0) + (p.has("uuid") ? 1 : 0) + (p.has("value") ? 1 : 0);
        if (sources != 1) throw new IllegalArgumentException("skin requires exactly one of username, uuid, value");
        if (p.has("signature") && !p.has("value")) throw new IllegalArgumentException("signature requires value");
        if (p.has("username")) {
            String name = ActorTimeline.text(p, "username");
            if (!name.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("Invalid Minecraft username");
            return new ActorSkinSpec(name, null, null, null);
        }
        if (p.has("uuid")) return new ActorSkinSpec(null, UUID.fromString(ActorTimeline.text(p, "uuid")), null, null);
        String value = ActorTimeline.text(p, "value"), signature = ActorTimeline.text(p, "signature");
        if (value.length() > 16384 || signature.length() > 4096) throw new IllegalArgumentException("Skin property too large");
        try {
            JsonObject json = JsonParser.parseString(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject textures = json.getAsJsonObject("textures");
            if (textures == null || !textures.has("SKIN")) throw new IllegalArgumentException("Skin texture missing");
            for (var entry : textures.entrySet()) {
                if (!Set.of("SKIN", "CAPE", "ELYTRA").contains(entry.getKey())) throw new IllegalArgumentException("Unknown texture type");
                URI url = URI.create(ActorTimeline.text(entry.getValue().getAsJsonObject(), "url"));
                if (!Set.of("https", "http").contains(url.getScheme()) || !"textures.minecraft.net".equals(url.getHost())
                        || url.getUserInfo() != null || url.getPort() != -1 || url.getQuery() != null || url.getFragment() != null
                        || !url.getPath().matches("/texture/[a-fA-F0-9]{32,64}"))
                    throw new IllegalArgumentException("Skin textures must use textures.minecraft.net/texture/<hash>");
            }
            Base64.getDecoder().decode(signature);
        } catch (RuntimeException failure) { throw new IllegalArgumentException("Invalid signed skin property: " + failure.getMessage(), failure); }
        return new ActorSkinSpec(null, null, value, signature);
    }
}
