package com.replaymod.core.mixin;

import com.replaymod.agent.ExportFrameVisibility;
import com.replaymod.render.hooks.EmbeddiumTerrainReadiness;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public abstract class MixinExportEmbeddiumEntityVisibility implements EmbeddiumTerrainReadiness {
    @Shadow(remap = false) private RenderSectionManager renderSectionManager;
    @Shadow(remap = false) public abstract boolean isTerrainRenderComplete();

    @Override public boolean agenticReplay$terrainWorkPending() {
        return renderSectionManager.needsUpdate() || !isTerrainRenderComplete()
                || ((MixinExportEmbeddiumChunkQueues) renderSectionManager).agenticReplay$getRebuildLists()
                .values().stream().anyMatch(queue -> !queue.isEmpty());
    }

    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void agenticReplay$authoredActor(Entity entity, CallbackInfoReturnable<Boolean> result) {
        if (ExportFrameVisibility.actor(entity.getId())) result.setReturnValue(true);
    }
}
