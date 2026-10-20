package com.replaymod.agent;

import com.replaymod.replay.ReplayHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** Render-only override, restored before simulation and on every exit path. */
public final class ExportEntityMotion {
    private final List<ExportEntityTrack> tracks;
    private final List<Runnable> restore = new ArrayList<>();
    public ExportEntityMotion(List<ExportEntityTrack> tracks) { this.tracks = List.copyOf(tracks); }
    public void restore() {
        for (Runnable undo : restore) undo.run();
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
