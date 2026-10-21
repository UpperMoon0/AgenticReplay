package com.replaymod.agent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
@EventBusSubscriber(modid = "replaymod", value = net.neoforged.api.distmarker.Dist.CLIENT)
public record ActorFramePayload(ActorReplayMovement.Frame frame) implements CustomPacketPayload {
    public static final Type<ActorFramePayload> TYPE = new Type<>(ActorReplayMovement.CHANNEL);
    public static final StreamCodec<FriendlyByteBuf, ActorFramePayload> CODEC = StreamCodec.of(
        (buf, payload) -> ActorReplayMovement.write(payload.frame(), buf),
        buf -> new ActorFramePayload(ActorReplayMovement.read(buf)));
    @Override public Type<ActorFramePayload> type() { return TYPE; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(TYPE, CODEC, (payload, context) -> context.enqueueWork(() -> apply(payload.frame())));
    }
    public static void apply(ActorReplayMovement.Frame frame) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || com.replaymod.replay.ReplayModReplay.instance == null
            || com.replaymod.replay.ReplayModReplay.instance.getReplayHandler() == null) return;
        var entity = mc.level.getEntity(frame.entityId());
        if (entity instanceof net.minecraft.client.player.RemotePlayer player
            && player.getGameProfile().properties().containsKey(ActorReplayMovement.PROFILE_MARKER))
            ((ActorReplayMovement.Target) player).actorReplayAccept(frame);
    }
}
