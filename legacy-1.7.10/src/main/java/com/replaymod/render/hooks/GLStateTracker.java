/* SPDX-License-Identifier: GPL-3.0-or-later
 * Based on ReplayMod ff47174fd6e94c051eaf6c0a0f3f4745a6b0e885.
 */
package com.replaymod.render.hooks;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Per-render-thread state used by the retained upstream ODS renderer. */
public class GLStateTracker {
    private static final ThreadLocal<GLStateTracker> stateTracker = ThreadLocal.withInitial(GLStateTracker::new);
    public static GLStateTracker getInstance() { return stateTracker.get(); }
    public int activeTexture;
    public void updateActiveTexture(int magic) { activeTexture = magic - GL13.GL_TEXTURE0; }
    public void updateEnabledState(int magic, boolean enabled) {
        switch (magic) {
            case GL11.GL_FOG: FogStateCallback.EVENT.invoker().fogStateChanged(enabled); break;
            case GL11.GL_TEXTURE_2D: Texture2DStateCallback.EVENT.invoker().texture2DStateChanged(activeTexture, enabled); break;
        }
    }
    /** Notification only: the original GL call has already run. */
    public static void notify_glEnable(int magic) { getInstance().updateEnabledState(magic, true); }
    public static void notify_glDisable(int magic) { getInstance().updateEnabledState(magic, false); }
    /** Retain the upstream ABI for any previously transformed callers. */
    @Deprecated public static void hook_glEnable(int magic) { GL11.glEnable(magic); notify_glEnable(magic); }
    @Deprecated public static void hook_glDisable(int magic) { GL11.glDisable(magic); notify_glDisable(magic); }
}
