package com.replaymod.core.mixin;

import com.replaymod.agent.ExportFrameVisibility;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
public abstract class MixinExportEntityGeometryReadiness {
    @Inject(method = "isRenderingReady", at = @At("HEAD"), cancellable = true)
    private void agenticReplay$authoredActor(BlockPos position, CallbackInfoReturnable<Boolean> result) {
        if (ExportFrameVisibility.block(position.getX(), position.getY(), position.getZ())) result.setReturnValue(true);
    }
}
