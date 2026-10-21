package com.replaymod.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Collection;
import java.util.Map;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public interface MixinExportEmbeddiumChunkQueues {
    @Accessor(value = "rebuildLists", remap = false)
    Map<?, ? extends Collection<?>> agenticReplay$getRebuildLists();
}
