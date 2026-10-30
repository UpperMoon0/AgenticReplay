/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.recording.mixin;

import cpw.mods.fml.common.eventhandler.Event;
import java.lang.reflect.Constructor;
import net.minecraft.client.gui.GuiScreen;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.replaymod.core.versions.MCVer.FORGE_BUS;

/**
 * Keep vanilla GuiScreen.handleInput polling (and Angelica's Mouse.next /
 * Keyboard.next hooks) intact while delivering JGui's per-event notifications.
 * JGui is embedded with the engine, not exposed on the overlay compile classpath.
 */
@Mixin(GuiScreen.class)
public abstract class MixinGuiScreen {
    private static Constructor<? extends Event> mouseEvent;
    private static Constructor<? extends Event> keyboardEvent;
    private static boolean mouseResolved;
    private static boolean keyboardResolved;

    @Inject(method = "handleMouseInput", at = @At("HEAD"), cancellable = true)
    private void agenticreplay$jGuiMouseEvent(CallbackInfo ci) {
        if (!mouseResolved) {
            mouseEvent = resolveEvent("MouseInputEvent");
            mouseResolved = true;
        }
        if (post(mouseEvent)) ci.cancel();
    }

    @Inject(method = "handleKeyboardInput", at = @At("HEAD"), cancellable = true)
    private void agenticreplay$jGuiKeyboardEvent(CallbackInfo ci) {
        if (!keyboardResolved) {
            keyboardEvent = resolveEvent("KeyboardInputEvent");
            keyboardResolved = true;
        }
        if (post(keyboardEvent)) ci.cancel();
    }

    private static Constructor<? extends Event> resolveEvent(String name) {
        try {
            Class<?> event = Class.forName(
                    "de.johni0702.minecraft.gui.container.VanillaGuiScreen$" + name);
            return event.asSubclass(Event.class).getDeclaredConstructor();
        } catch (ReflectiveOperationException | ClassCastException failure) {
            // Never disable native input because an optional UI bridge is absent.
            LogManager.getLogger("AgenticReplay").warn(
                    "JGui input event {} unavailable; keeping native input", name, failure);
            return null;
        }
    }

    private static boolean post(Constructor<? extends Event> constructor) {
        if (constructor == null) return false;
        try {
            return FORGE_BUS.post(constructor.newInstance());
        } catch (ReflectiveOperationException failure) {
            LogManager.getLogger("AgenticReplay").warn(
                    "JGui input event construction failed; keeping native input", failure);
            return false;
        }
    }
}
