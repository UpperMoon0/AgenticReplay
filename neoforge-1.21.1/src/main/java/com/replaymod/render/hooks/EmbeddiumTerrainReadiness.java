package com.replaymod.render.hooks;

/** Implemented by the optional Embeddium renderer mixin. */
public interface EmbeddiumTerrainReadiness {
    boolean agenticReplay$terrainWorkPending();

    static EmbeddiumTerrainReadiness current() {
        try {
            Class<?> renderer = Class.forName("org.embeddedt.embeddium.impl.render.EmbeddiumWorldRenderer");
            return (EmbeddiumTerrainReadiness) renderer.getMethod("instance").invoke(null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Embeddium terrain integration is unavailable", failure);
        }
    }
}
