package com.replaymod.core.mixin;

import com.mojang.authlib.GameProfile;
import com.replaymod.agent.ActorDefaultSkin;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListEntry.class)
public abstract class MixinActorDefaultSkin {
    @Shadow public abstract GameProfile getProfile();
    @Inject(method = "getSkinTexture", at = @At("HEAD"), cancellable = true)
    private void actorDefaultSkin(CallbackInfoReturnable<Identifier> result) {
        var avatar = ActorDefaultSkin.textures(getProfile());
        if (avatar != null) result.setReturnValue(avatar.texture());
    }
    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void actorDefaultModel(CallbackInfoReturnable<String> result) {
        var avatar = ActorDefaultSkin.textures(getProfile());
        if (avatar != null) result.setReturnValue(avatar.model());
    }
}
