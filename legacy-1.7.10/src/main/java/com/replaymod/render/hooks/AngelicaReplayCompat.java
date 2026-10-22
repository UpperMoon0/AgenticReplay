/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.render.hooks;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import net.minecraft.client.Minecraft;

/** Source-reviewed integration for Angelica 2.2.28's Celeritas renderer. */
public final class AngelicaReplayCompat {
    private static final ThreadLocal<Boolean> finishing = new ThreadLocal<>();
    private AngelicaReplayCompat() {}

    public static boolean forceImmediateTerrain(boolean original) {
        ModContainer angelica = Loader.instance().getIndexedModList().get("angelica");
        if (angelica == null || !"2.2.28".equals(angelica.getVersion())) return original;
        Object renderer = Minecraft.getMinecraft().entityRenderer;
        return original || (renderer instanceof EntityRendererHandler.IEntityRenderer
                && ((EntityRendererHandler.IEntityRenderer) renderer).replayModRender_getHandler() != null);
    }

    public static void finishTerrain(Object renderer, Object viewport, Object cameraState, int frame, boolean spectator) {
        if (Boolean.TRUE.equals(finishing.get()) || !forceImmediateTerrain(false)) return;
        finishing.set(true);
        try {
            ClassLoader loader = renderer.getClass().getClassLoader();
            Class<?> sections = Class.forName("org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager", false, loader);
            Object manager = renderer.getClass().getMethod("getRenderSectionManager").invoke(renderer);
            java.lang.reflect.Method finishGraph = sections.getMethod("finishAllGraphUpdates");
            java.lang.reflect.Method build = sections.getMethod("updateChunks", boolean.class);
            java.lang.reflect.Method upload = sections.getMethod("uploadChunks");
            java.lang.reflect.Method dirty = sections.getMethod("needsUpdate");
            java.lang.reflect.Method queued = sections.getDeclaredMethod("rebuildListHasUpdates");
            queued.setAccessible(true);
            java.lang.reflect.Method complete = renderer.getClass().getMethod("isTerrainRenderComplete");
            java.lang.reflect.Method setup = renderer.getClass().getMethod("setupTerrain",
                    Class.forName("org.embeddedt.embeddium.impl.render.viewport.Viewport", false, loader),
                    Class.forName("org.embeddedt.embeddium.impl.render.terrain.SimpleWorldRenderer$CameraState", false, loader),
                    int.class, boolean.class, boolean.class);
            Object global = Minecraft.getMinecraft().renderGlobal;
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
            do {
                // An empty worker queue alone is insufficient: the asynchronous
                // graph may still discover initial builds or overflowed sections.
                finishGraph.invoke(manager);
                build.invoke(manager, true);
                upload.invoke(manager);
                finishGraph.invoke(manager);
                if (!(Boolean) dirty.invoke(manager) && !(Boolean) queued.invoke(manager)
                        && (Boolean) complete.invoke(renderer)) return;
                // Celeritas uses ordered per-frame visit stamps. Reusing the
                // same stamp drops already-visited sections from the next list.
                // Advance the owning RenderGlobal counter exactly as its normal
                // clipRenderersByFrustum path does, including for future frames.
                int searchFrame = nextSearchFrame(global);
                setup.invoke(renderer, viewport, cameraState, searchFrame, spectator, true);
            } while (System.nanoTime() < deadline);
            throw new IllegalStateException("Angelica terrain did not finish within 30 seconds; export aborted");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Angelica 2.2.28 terrain integration failed", failure);
        } finally {
            finishing.remove();
        }
    }

    static int nextSearchFrame(Object global) throws ReflectiveOperationException {
        java.lang.reflect.Field counter = global.getClass().getDeclaredField("celeritas$frame");
        counter.setAccessible(true);
        int frame = counter.getInt(global);
        counter.setInt(global, frame + 1);
        return frame;
    }
}
