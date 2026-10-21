package com.replaymod.core.mixin;
import com.replaymod.agent.ActorFramePayload;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class MixinActorReplayPayload {
    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void actorReplayPayload(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        if (packet.payload() instanceof ActorFramePayload payload) {
            var mc = Minecraft.getInstance();
            if (mc.isSameThread()) ActorFramePayload.apply(payload.frame());
            else mc.execute(() -> ActorFramePayload.apply(payload.frame()));
            ci.cancel();
        }
    }
}
