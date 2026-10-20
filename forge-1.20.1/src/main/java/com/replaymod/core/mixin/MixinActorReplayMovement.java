package com.replaymod.core.mixin;

import com.replaymod.agent.ActorReplayMovement;
import com.replaymod.replay.ext.EntityExt;
import net.minecraft.client.network.OtherClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;

@Mixin(OtherClientPlayerEntity.class)
public abstract class MixinActorReplayMovement implements ActorReplayMovement.Target {
    @Unique private final ActorReplayMovement.State actorReplayState = new ActorReplayMovement.State();
    @Override public int actorEntityId() { return ((OtherClientPlayerEntity) (Object) this).getId(); }
    @Override public UUID actorUuid() { return ((OtherClientPlayerEntity) (Object) this).getUuid(); }
    @Override public ActorReplayMovement.Pose actorPose() {
        var player = (OtherClientPlayerEntity) (Object) this;
        return new ActorReplayMovement.Pose(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch(), player.headYaw, player.bodyYaw);
    }
    @Override public boolean actorReplayAccept(ActorReplayMovement.Frame frame) { return actorReplayState.accept(this, frame); }
    @Override public boolean actorReplayAccept(ActorReplayMovement.Frame frame, ActorReplayMovement.Pose previous) {
        return actorReplayState.accept(this, frame, previous);
    }
    @Override public void actorApply(ActorReplayMovement.Frame frame) {
        var player = (OtherClientPlayerEntity) (Object) this;
        var p = frame.pose();
        player.updateTrackedPosition(p.x(), p.y(), p.z());
        player.updateTrackedPositionAndAngles(p.x(), p.y(), p.z(), p.yaw(), p.pitch(), 0, true);
        player.updateTrackedHeadRotation(p.headYaw(), 0);
        player.setPosition(p.x(), p.y(), p.z());
        player.setYaw(p.yaw()); player.setPitch(p.pitch()); player.setHeadYaw(p.headYaw()); player.bodyYaw = p.bodyYaw();
        player.setVelocity(frame.vx(), frame.vy(), frame.vz());
        ((EntityExt) player).replaymod$setTrackedYaw(p.yaw());
        ((EntityExt) player).replaymod$setTrackedPitch(p.pitch());
    }
    @Override public void actorPrevious(ActorReplayMovement.Pose p) {
        var player = (OtherClientPlayerEntity) (Object) this;
        player.prevX = player.lastRenderX = p.x(); player.prevY = player.lastRenderY = p.y(); player.prevZ = player.lastRenderZ = p.z();
        player.prevYaw = p.yaw(); player.prevPitch = p.pitch(); player.prevHeadYaw = p.headYaw(); player.prevBodyYaw = p.bodyYaw();
    }
    @Override public void actorLimbs(float speed) { ((OtherClientPlayerEntity) (Object) this).limbAnimator.updateLimbs(speed, 0.4f); }
    @Inject(method = "tick", at = @At("RETURN"))
    private void actorReplayTick(CallbackInfo ci) { actorReplayState.afterTick(this); }
}
