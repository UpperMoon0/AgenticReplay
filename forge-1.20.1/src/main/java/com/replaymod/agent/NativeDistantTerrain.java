package com.replaymod.agent;

import net.minecraft.client.MinecraftClient;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Update optional DH camera/detail work even when the replay time channel is frozen. */
public final class NativeDistantTerrain {
    private static boolean searched;
    private static Method wrapperFactory;
    private NativeDistantTerrain() {}

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        try {
            if (!searched) {
                try {
                    Class<?> type = Class.forName("com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper_forge", false, NativeDistantTerrain.class.getClassLoader());
                    wrapperFactory = type.getMethod("getWrapper", mc.world.getClass());
                } catch (ClassNotFoundException absent) {
                    // DH is optional; do not add a dependency for ordinary ReplayMod users.
                }
                searched = true;
            }
            if (wrapperFactory != null) updateWrapper(wrapperFactory.invoke(null, mc.world));
        } catch (ReflectiveOperationException failure) {
            throw failure(failure);
        }
    }

    static void updateWrapper(Object wrapper) {
        if (wrapper == null) return;
        try {
            Object level = wrapper.getClass().getMethod("getDhLevel").invoke(wrapper);
            if (level != null) level.getClass().getMethod("clientTick").invoke(level);
        } catch (ReflectiveOperationException failure) {
            throw failure(failure);
        }
    }

    private static IllegalStateException failure(ReflectiveOperationException failure) {
        Throwable cause = failure instanceof InvocationTargetException invocation ? invocation.getCause() : failure;
        return new IllegalStateException("Distant Horizons export camera/detail update failed", cause);
    }
}
