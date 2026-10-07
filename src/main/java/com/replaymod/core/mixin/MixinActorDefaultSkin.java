package com.replaymod.core.mixin;

import com.mojang.authlib.GameProfile;
import com.replaymod.agent.ActorDefaultSkin;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListEntry.class)
public abstract class MixinActorDefaultSkin {
    @Shadow public abstract GameProfile getProfile();
    @Inject(method = "getSkinTextures", at = @At("HEAD"), cancellable = true)
    private void actorDefaultSkin(CallbackInfoReturnable<SkinTextures> result) {
        SkinTextures textures = ActorDefaultSkin.textures(getProfile());
        if (textures != null) result.setReturnValue(textures);
    }
}
