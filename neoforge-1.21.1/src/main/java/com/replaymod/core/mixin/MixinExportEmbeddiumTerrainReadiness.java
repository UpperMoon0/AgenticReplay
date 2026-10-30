package com.replaymod.core.mixin;

import com.replaymod.render.hooks.EmbeddiumTerrainReadiness;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** Embeddium 1.0.8-beta.367 terrain work discovered after the first graph pass. */
@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.render.EmbeddiumWorldRenderer", remap = false)
public abstract class MixinExportEmbeddiumTerrainReadiness implements EmbeddiumTerrainReadiness {
    @Shadow(remap = false) private RenderSectionManager renderSectionManager;
    @Shadow(remap = false) public abstract boolean isTerrainRenderComplete();

    @Override public boolean agenticReplay$terrainWorkPending() {
        return renderSectionManager.needsUpdate() || !isTerrainRenderComplete()
                || ((MixinExportEmbeddiumChunkQueues) renderSectionManager).agenticReplay$getRebuildLists()
                .values().stream().anyMatch(queue -> !queue.isEmpty());
    }
}
