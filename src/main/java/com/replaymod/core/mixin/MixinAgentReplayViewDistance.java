package com.replaymod.core.mixin;

import com.replaymod.agent.AgentReplayViewDistance;
import com.replaymod.replay.ReplayModReplay;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameOptions.class)
public abstract class MixinAgentReplayViewDistance {
    @Inject(method = "getClampedViewDistance", at = @At("RETURN"), cancellable = true)
    private void agenticReplay$cameraBudget(CallbackInfoReturnable<Integer> result) {
        if (ReplayModReplay.instance != null && ReplayModReplay.instance.getReplayHandler() != null)
            result.setReturnValue(AgentReplayViewDistance.effective(result.getReturnValue()));
    }
}
