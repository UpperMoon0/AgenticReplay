package com.replaymod.agent;

import com.replaymod.replay.ReplayHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import com.replaymod.core.mixin.ActorEntityAccessor;
import com.replaymod.replay.mixin.EntityLivingBaseAccessor;
import java.util.*;

/** Render-only override, restored before simulation and on every exit path. */
public final class ExportEntityMotion {
    private final List<ExportEntityTrack> tracks;
    private final List<Runnable> restore = new ArrayList<>();
    public ExportEntityMotion(List<ExportEntityTrack> tracks) { this.tracks = List.copyOf(tracks); }
    public void restore() {
        for (int i=restore.size()-1; i>=0; i--) restore.get(i).run();
        restore.clear();
    }
    public void apply(ReplayHandler handler, double outputTime) {
        if (tracks.isEmpty()) return;
        var camera = handler.getCameraEntity();
        if (camera == null) throw new IllegalStateException("Replay camera unavailable");
        for (ExportEntityTrack track : tracks) {
            Entity e = camera.level().getEntity(track.entityId());
            if (e == null || e == camera) throw new IllegalStateException("Export entity unavailable: " + track.entityId());
            double x=e.getX(), y=e.getY(), z=e.getZ(), px=e.xo, py=e.yo, pz=e.zo;
            double rx=e.xOld, ry=e.yOld, rz=e.zOld;
            float yaw=e.getYRot(), pitch=e.getXRot(), yRotO=e.yRotO, xRotO=e.xRotO;
            float head=0, prevHead=0, body=0, prevBody=0;
            if (e instanceof LivingEntity living) {
                head=living.yHeadRot; prevHead=living.yHeadRotO; body=living.yBodyRot; prevBody=living.yBodyRotO;
            }
            final float h=head, ph=prevHead, b=body, pb=prevBody;
            restore.add(() -> {
                e.setPos(x,y,z); e.xo=px; e.yo=py; e.zo=pz;
                e.xOld=rx; e.yOld=ry; e.zOld=rz;
                e.setYRot(yaw); e.setXRot(pitch); e.yRotO=yRotO; e.xRotO=xRotO;
                if (e instanceof LivingEntity living) {
                    living.yHeadRot=h; living.yHeadRotO=ph; living.yBodyRot=b; living.yBodyRotO=pb;
                }
            });
            var authored = track.sample(outputTime);
            if (authored.pose() != null || track.elytra()) {
                if (!(e instanceof LivingEntity living)) throw new IllegalStateException("Pose/equipment tracks require a living entity");
                var originalPose = e.getPose();
                var originalVelocity = e.getDeltaMovement();
                boolean flying = living.isFallFlying();
                var flight = (EntityLivingBaseAccessor) living;
                int originalFlightTicks = flight.exportFlightTicks();
                ItemStack chest = living.getItemBySlot(EquipmentSlot.CHEST).copy();
                restore.add(() -> {
                    e.setPose(originalPose);
                    ((ActorEntityAccessor)e).actorSetFlag(7, flying);
                    flight.exportFlightTicks(originalFlightTicks);
                    e.setDeltaMovement(originalVelocity);
                    if (track.elytra()) living.setItemSlot(EquipmentSlot.CHEST, chest);
                });
                if (track.elytra()) living.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
                if (authored.pose() != null) {
                    boolean glide = authored.pose().equals("fall_flying");
                    e.setPose(glide ? Pose.FALL_FLYING : Pose.STANDING);
                    ((ActorEntityAccessor)e).actorSetFlag(7, glide);
                    flight.exportFlightTicks(track.flightTicks(outputTime));
                    if (glide) {
                        var before = track.sample(outputTime-1);
                        var after = track.sample(outputTime+1);
                        e.setDeltaMovement(new Vec3((after.x()-before.x())*25, (after.y()-before.y())*25, (after.z()-before.z())*25));
                    }
                }
            }
            var p = track.sample(outputTime);
            e.setPos(p.x(),p.y(),p.z());
            e.xo=e.xOld=p.x(); e.yo=e.yOld=p.y(); e.zo=e.zOld=p.z();
            e.setYRot((float)p.yaw()); e.yRotO=e.getYRot(); e.setXRot((float)p.pitch()); e.xRotO=e.getXRot();
            if (e instanceof LivingEntity living) {
                living.yHeadRot=living.yHeadRotO=living.yBodyRot=living.yBodyRotO=e.getYRot();
            }
        }
    }
}
