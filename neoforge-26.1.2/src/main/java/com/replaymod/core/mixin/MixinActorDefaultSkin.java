package com.replaymod.core.mixin;
import com.mojang.authlib.GameProfile;
import com.replaymod.agent.ActorDefaultSkin;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PlayerInfo.class)
public abstract class MixinActorDefaultSkin {
    @Shadow public abstract GameProfile getProfile();
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void actorDefaultSkin(CallbackInfoReturnable<PlayerSkin> result) {
        if (ActorDefaultSkin.textures(getProfile()) != null) {
            var source = getProfile().properties().get(ActorDefaultSkin.SOURCE).iterator().next().value();
            result.setReturnValue(DefaultPlayerSkin.get(java.util.UUID.fromString(source)));
        }
    }
}
