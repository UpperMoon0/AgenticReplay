package com.replaymod.core.mixin;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** 26.1 moved backend selection and GLFW creation out of Window's constructor. */
@Mixin(Window.class)
public abstract class MixinAgentBackgroundWindow {
    @Inject(method = "createGlfwWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false))
    private static void agenticBackgroundCreate(CallbackInfoReturnable<Long> ci) {
        if (Boolean.getBoolean("agenticreplay.background")) {
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        }
    }
    @Inject(method = "createWindow", at = @At("RETURN"))
    private void agenticBackgroundHandoff(CallbackInfoReturnable<Long> ci) {
        if (Boolean.getBoolean("agenticreplay.background") && ci.getReturnValue() != 0) {
            GLFW.glfwSetWindowAttrib(ci.getReturnValue(), GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
            GLFW.glfwHideWindow(ci.getReturnValue());
        }
    }
}
