package com.replaymod.agent;

import com.mojang.authlib.*;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.MinecraftClient;
import java.util.*;
import java.util.concurrent.*;

/** Profile HTTP work never blocks the client thread. Queue and concurrency are bounded. */
final class ActorSkins {
    private static final ExecutorService LOOKUPS = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(32), task -> {
                Thread thread = new Thread(task, "AgenticReplay-skins"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    static CompletableFuture<GameProfile> resolve(MinecraftClient mc, UUID actorUuid, String name, ActorSkinSpec skin) {
        GameProfile self = new GameProfile(mc.player.getGameProfile().getId(), mc.player.getGameProfile().getName());
        self.getProperties().putAll(mc.player.getGameProfile().getProperties());
        return CompletableFuture.supplyAsync(() -> {
            GameProfile source;
            if (skin == null) source = self;
            else if (skin.value() != null) {
                source = new GameProfile(actorUuid, name);
                Property property = new Property("textures", skin.value(), skin.signature());
                // Verify the signed payload, not just its base64 shape, before exposing the character as ready.
                try { mc.getSessionService().getSecurePropertyValue(property); }
                catch (com.mojang.authlib.minecraft.InsecurePublicKeyException failure) {
                    throw new IllegalArgumentException("Invalid skin signature", failure);
                }
                source.getProperties().put("textures", property);
            } else {
                UUID id = skin.uuid();
                if (id == null) {
                    GameProfile[] found = new GameProfile[1];
                    RuntimeException[] failure = new RuntimeException[1];
                    new YggdrasilAuthenticationService(mc.getNetworkProxy()).createProfileRepository()
                            .findProfilesByNames(new String[]{skin.username()}, Agent.MINECRAFT, new ProfileLookupCallback() {
                                public void onProfileLookupSucceeded(GameProfile profile) { found[0] = profile; }
                                public void onProfileLookupFailed(GameProfile profile, Exception error) {
                                    failure[0] = new IllegalArgumentException("Skin player lookup failed: " + profile.getName(), error);
                                }
                            });
                    if (failure[0] != null) throw failure[0];
                    if (found[0] == null) throw new IllegalArgumentException("Skin player not found");
                    id = found[0].getId();
                }
                source = mc.getSessionService().fillProfileProperties(new GameProfile(id, skin.username()), true);
                if (source == null || source.getProperties().isEmpty()) throw new IllegalArgumentException("Skin profile not found: " + id);
            }
            if (skin == null && !source.getProperties().containsKey("textures")) {
                var result = mc.getSessionService().fillProfileProperties(source, true);
                if (result != null) source = result;
            }
            return ActorDefaultSkin.character(actorUuid, name, source);
        }, LOOKUPS).orTimeout(30, TimeUnit.SECONDS);
    }
}
