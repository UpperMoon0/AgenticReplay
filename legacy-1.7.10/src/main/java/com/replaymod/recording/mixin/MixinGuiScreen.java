/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.recording.mixin;

import de.johni0702.minecraft.gui.container.VanillaGuiScreen;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.replaymod.core.versions.MCVer.FORGE_BUS;

/**
 * Preserve vanilla's GuiScreen.handleInput polling loop (including other
 * mods' Mouse.next/Keyboard.next activity hooks). Offer JGui its Forge events
 * at the actual per-event dispatch boundary instead of cancelling the loop.
 */
@Mixin(GuiScreen.class)
public abstract class MixinGuiScreen {
    @Inject(method = "handleMouseInput", at = @At("HEAD"), cancellable = true)
    private void agenticreplay$jGuiMouseEvent(CallbackInfo ci) {
        if (FORGE_BUS.post(new VanillaGuiScreen.MouseInputEvent())) ci.cancel();
    }

    @Inject(method = "handleKeyboardInput", at = @At("HEAD"), cancellable = true)
    private void agenticreplay$jGuiKeyboardEvent(CallbackInfo ci) {
        if (FORGE_BUS.post(new VanillaGuiScreen.KeyboardInputEvent())) ci.cancel();
    }
}
