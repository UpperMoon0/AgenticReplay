package com.replaymod.agent;

import com.google.gson.*;
import org.junit.Test;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class ActorSkinSpecTest {
    private static ActorSkinSpec parse(String json) { return ActorSkinSpec.parse(JsonParser.parseString(json).getAsJsonObject()); }
    @Test public void realPlayerSourcesAreUnambiguous() {
        assertEquals("Alice", parse("{\"username\":\"Alice\"}").username());
        UUID id = UUID.randomUUID(); assertEquals(id, parse("{\"uuid\":\"" + id + "\"}").uuid());
        for (String input : List.of("{}", "{\"username\":\"Alice\",\"uuid\":\"" + id + "\"}",
                "{\"username\":\"https://example.com\"}", "{\"username\":\"Alice\",\"signature\":\"AA==\"}",
                "{\"uuid\":\"garbage\"}", "{\"username\":\"Alice\",\"url\":\"x\"}")) reject(input);
    }
    @Test public void signedSkinAcceptsClassicAndSlimTexturesAndRestrictsNetworkDestination() {
        String hash = "a".repeat(64);
        JsonObject skin = signed("https://textures.minecraft.net/texture/" + hash);
        assertNotNull(ActorSkinSpec.parse(skin).value());
        for (String url : List.of("http://127.0.0.1/texture/" + hash, "file:///tmp/skin.png",
                "https://textures.minecraft.net.evil.test/texture/" + hash,
                "https://user@textures.minecraft.net/texture/" + hash, "https://textures.minecraft.net:443/texture/" + hash,
                "https://textures.minecraft.net/texture/" + hash + "?redirect=evil")) {
            try { ActorSkinSpec.parse(signed(url)); fail(url); } catch (IllegalArgumentException expected) { }
        }
        skin.remove("signature"); try { ActorSkinSpec.parse(skin); fail(); } catch (IllegalArgumentException expected) { }
    }
    private static JsonObject signed(String url) {
        JsonObject result = new JsonObject();
        result.addProperty("value", Base64.getEncoder().encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"" + url
                + "\",\"metadata\":{\"model\":\"slim\"}}}}").getBytes(StandardCharsets.UTF_8)));
        result.addProperty("signature", "AA=="); return result;
    }
    private static void reject(String input) { try { parse(input); fail(input); } catch (IllegalArgumentException expected) { } }
}
