package com.replaymod.core.mixin;

import com.replaymod.agent.ActorReplayMovement;
import com.replaymod.replay.ext.EntityExt;
import net.minecraft.client.player.RemotePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;

@Mixin(RemotePlayer.class)
public abstract class MixinActorReplayMovement implements ActorReplayMovement.Target {
    @Unique private final ActorReplayMovement.State actorReplayState = new ActorReplayMovement.State();
    @Override public int actorEntityId() { return ((RemotePlayer) (Object) this).getId(); }
    @Override public UUID actorUuid() { return ((RemotePlayer) (Object) this).getUUID(); }
    @Override public ActorReplayMovement.Pose actorPose() {
        var player = (RemotePlayer) (Object) this;
        return new ActorReplayMovement.Pose(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), player.yHeadRot, player.yBodyRot);
    }
    @Override public boolean actorReplayAccept(ActorReplayMovement.Frame frame) { return actorReplayState.accept(this, frame); }
    @Override public boolean actorReplayAccept(ActorReplayMovement.Frame frame, ActorReplayMovement.Pose previous) {
        return actorReplayState.accept(this, frame, previous);
    }
    @Override public void actorApply(ActorReplayMovement.Frame frame) {
        var player = (RemotePlayer) (Object) this;
        var p = frame.pose();
        player.getPositionCodec().setBase(new net.minecraft.world.phys.Vec3(p.x(), p.y(), p.z()));
        player.getInterpolation().cancel();
        player.lerpHeadTo(p.headYaw(), 0);
        player.setPos(p.x(), p.y(), p.z());
        player.setYRot(p.yaw()); player.setXRot(p.pitch()); player.setYHeadRot(p.headYaw()); player.yBodyRot = p.bodyYaw();
        player.setDeltaMovement(frame.vx(), frame.vy(), frame.vz());
        ((EntityExt) player).replaymod$setTrackedYaw(p.yaw());
        ((EntityExt) player).replaymod$setTrackedPitch(p.pitch());
    }
    @Override public void actorPrevious(ActorReplayMovement.Pose p) {
        var player = (RemotePlayer) (Object) this;
        player.xo = player.xOld = p.x(); player.yo = player.yOld = p.y(); player.zo = player.zOld = p.z();
        player.yRotO = p.yaw(); player.xRotO = p.pitch(); player.yHeadRotO = p.headYaw(); player.yBodyRotO = p.bodyYaw();
    }
    @Override public void actorLimbs(float speed) { ((RemotePlayer) (Object) this).walkAnimation.update(speed, 0.4f, 1.0f); }
    @Inject(method = "tick", at = @At("RETURN"))
    private void actorReplayTick(CallbackInfo ci) { actorReplayState.afterTick(this); }
}
