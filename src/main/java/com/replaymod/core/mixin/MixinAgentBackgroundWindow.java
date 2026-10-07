package com.replaymod.core.mixin;

import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import java.util.function.IntSupplier;

/** Opt-in startup hint: the filming client must not steal another application's focus. */
@Mixin(value = Window.class, priority = 1100)
public abstract class MixinAgentBackgroundWindow {
    // These vanilla hint calls remain in Forge's patched constructor. Reapply
    // the opt-in hints there, after GLFW resets its defaults, and require an
    // actual match rather than silently accepting a missed handoff injection.
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", remap = false), index = 1, require = 1)
    private int agenticBackgroundHint(int hint, int value) {
        applyBackgroundHints();
        if (Boolean.getBoolean("agenticreplay.background") &&
                (hint == GLFW.GLFW_FOCUSED || hint == GLFW.GLFW_FOCUS_ON_SHOW || hint == GLFW.GLFW_VISIBLE)) {
            return GLFW.GLFW_FALSE;
        }
        return value;
    }

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false), index = 0, require = 0)
    private int agenticBackgroundWindow(int width) {
        applyBackgroundHints();
        return width;
    }

    // Forge replaces the vanilla GLFW call with this handoff. Its disabled
    // splash provider still creates a window, so hints must precede the handoff.
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J", remap = false), index = 0, require = 0)
    private IntSupplier agenticBackgroundForgeWindow(IntSupplier width) {
        applyBackgroundHints();
        return width;
    }

    private static void applyBackgroundHints() {
        if (Boolean.getBoolean("agenticreplay.background")) {
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        }
    }
}
