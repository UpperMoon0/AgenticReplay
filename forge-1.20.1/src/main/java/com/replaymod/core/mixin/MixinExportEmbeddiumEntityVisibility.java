package com.replaymod.core.mixin;

import com.replaymod.agent.ExportFrameVisibility;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public abstract class MixinExportEmbeddiumEntityVisibility {
    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void agenticReplay$authoredActor(Entity entity, CallbackInfoReturnable<Boolean> result) {
        if (ExportFrameVisibility.actor(entity.getId())) result.setReturnValue(true);
    }
}
