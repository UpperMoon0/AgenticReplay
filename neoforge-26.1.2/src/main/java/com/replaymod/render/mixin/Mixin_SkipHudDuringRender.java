package com.replaymod.render.mixin;
import com.replaymod.render.hooks.EntityRendererHandler;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** 26.1 extracts all GUI state before drawing it; skip extraction during export. */
@Mixin(GameRenderer.class)
public abstract class Mixin_SkipHudDuringRender {
    @Inject(method = "extractGui", at = @At("TAIL"))
    private void replayModRender_skipHudDuringRender(CallbackInfo ci) {
        var renderer = (GameRenderer) (Object) this;
        if (((EntityRendererHandler.IEntityRenderer) renderer).replayModRender_getHandler() != null) {
            // Preserve the vanilla GUI extraction lifecycle for renderer integrations.
            // Empty its submissions after extraction instead of cancelling the method.
            renderer.getGameRenderState().guiRenderState.reset();
        }
    }
}
