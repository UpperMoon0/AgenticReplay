package com.replaymod.core.mixin;

import com.replaymod.agent.ActorReplayMovement;
import com.replaymod.replay.ReplayModReplay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinActorReplayPayload {
    // Handle our recording-only channel before Forge's live-network custom-payload hook.
    @Inject(method = "onCustomPayload", at = @At("HEAD"), cancellable = true)
    private void actorReplayPayload(CustomPayloadS2CPacket packet, CallbackInfo ci) {
        if (!ActorReplayMovement.CHANNEL.equals(packet.getChannel())) return;
        var mc = MinecraftClient.getInstance();
        NetworkThreadUtils.forceMainThread(packet, (ClientPlayPacketListener) (Object) this, mc);
        if (ReplayModReplay.instance != null && ReplayModReplay.instance.getReplayHandler() != null && mc.world != null) {
            var data = packet.getData();
            try {
                var frame = ActorReplayMovement.read(data);
                var entity = mc.world.getEntityById(frame.entityId());
                if (entity instanceof OtherClientPlayerEntity player
                        && player.getGameProfile().getProperties().containsKey(ActorReplayMovement.PROFILE_MARKER))
                    ((ActorReplayMovement.Target) player).actorReplayAccept(frame);
            } catch (IllegalArgumentException | IndexOutOfBoundsException malformed) {
                // A malformed optional extension must not move another player or prevent replay playback.
            } finally { data.release(); }
        }
        ci.cancel();
    }
}
