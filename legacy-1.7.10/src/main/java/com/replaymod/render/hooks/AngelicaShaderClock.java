/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.render.hooks;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import net.minecraft.client.Minecraft;
import java.lang.reflect.Field;
import java.util.OptionalLong;

/** Export animation time must follow output frames, independent of render throughput. */
public final class AngelicaShaderClock {
    private AngelicaShaderClock() {}
    public static boolean update(Object timer, long now) {
        ModContainer mod = Loader.instance().getIndexedModList().get("angelica");
        if (mod == null || !"2.2.28".equals(mod.getVersion())) return false;
        Object renderer = Minecraft.getMinecraft().entityRenderer;
        if (!(renderer instanceof EntityRendererHandler.IEntityRenderer)) return false;
        EntityRendererHandler handler = ((EntityRendererHandler.IEntityRenderer) renderer).replayModRender_getHandler();
        if (handler == null) return false;
        int fps = handler.getRenderInfo().getRenderSettings().getFramesPerSecond();
        int frame = handler.getRenderInfo().getFramesDone();
        try {
            set(timer, "frameTimeCounter", counter(frame, fps));
            set(timer, "lastFrameTime", 1.0f / fps);
            // Keep the ordinary timer's origin current for a smooth return to live rendering.
            set(timer, "lastStartTime", OptionalLong.of(now));
            return true;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Angelica 2.2.28 export clock integration failed", failure);
        }
    }
    public static float counter(int frame, int fps) {
        if (fps <= 0) throw new IllegalArgumentException("Positive export FPS required");
        return (Math.max(0, frame - 1) / (float) fps) % 3600.0f;
    }
    private static void set(Object timer, String name, Object value) throws ReflectiveOperationException {
        Field field = timer.getClass().getDeclaredField(name);field.setAccessible(true);field.set(timer,value);
    }
}
