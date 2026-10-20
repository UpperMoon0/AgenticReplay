package com.replaymod.agent;

import com.mojang.authlib.*;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.concurrent.*;

/** Profile HTTP work never blocks the client thread. Queue and concurrency are bounded. */
final class ActorSkins {
    private static final ExecutorService LOOKUPS = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(32), task -> {
                Thread thread = new Thread(task, "AgenticReplay-skins"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    static CompletableFuture<GameProfile> resolve(Minecraft mc, UUID actorUuid, String name, ActorSkinSpec skin) {
        GameProfile self = mc.player.getGameProfile();
        return CompletableFuture.supplyAsync(() -> {
            GameProfile source;
            if (skin == null) source = self;
            else if (skin.value() != null) {
                var properties = com.google.common.collect.ArrayListMultimap.<String, Property>create();
                Property property = new Property("textures", skin.value(), skin.signature());
                // Verify the signed payload, not just its base64 shape, before exposing the character as ready.
                try { mc.services().sessionService().getSecurePropertyValue(property); }
                catch (com.mojang.authlib.minecraft.InsecurePublicKeyException failure) {
                    throw new IllegalArgumentException("Invalid skin signature", failure);
                }
                properties.put("textures", property);
                source = new GameProfile(actorUuid, name, new com.mojang.authlib.properties.PropertyMap(properties));
            } else {
                UUID id = skin.uuid();
                if (id == null) {
                    GameProfile[] found = new GameProfile[1];
                    RuntimeException[] failure = new RuntimeException[1];
                    new YggdrasilAuthenticationService(mc.getProxy()).createProfileRepository()
                            .findProfilesByNames(new String[]{skin.username()}, new ProfileLookupCallback() {
                                public void onProfileLookupSucceeded(String username, UUID id) { found[0] = new GameProfile(id, username); }
                                public void onProfileLookupFailed(String profile, Exception error) {
                                    failure[0] = new IllegalArgumentException("Skin player lookup failed: " + profile, error);
                                }
                            });
                    if (failure[0] != null) throw failure[0];
                    if (found[0] == null) throw new IllegalArgumentException("Skin player not found");
                    id = found[0].id();
                }
                var fetched = mc.services().sessionService().fetchProfile(id, true);
                source = fetched == null ? null : fetched.profile();
                if (source == null || source.properties().isEmpty()) throw new IllegalArgumentException("Skin profile not found: " + id);
            }
            if (skin == null && !source.properties().containsKey("textures")) {
                var result = mc.services().sessionService().fetchProfile(source.id(), true);
                if (result != null) source = result.profile();
            }
            return ActorDefaultSkin.character(actorUuid, name, source);
        }, LOOKUPS).orTimeout(30, TimeUnit.SECONDS);
    }
}
