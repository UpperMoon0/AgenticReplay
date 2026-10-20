package com.replaymod.agent;

import com.replaymod.replay.ReplayHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
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
            Entity e = camera.getEntityWorld().getEntityById(track.entityId());
            if (e == null || e == camera) throw new IllegalStateException("Export entity unavailable: " + track.entityId());
            double x=e.getX(), y=e.getY(), z=e.getZ(), px=e.prevX, py=e.prevY, pz=e.prevZ;
            double rx=e.lastRenderX, ry=e.lastRenderY, rz=e.lastRenderZ;
            float yaw=e.getYaw(), pitch=e.getPitch(), prevYaw=e.prevYaw, prevPitch=e.prevPitch;
            float head=0, prevHead=0, body=0, prevBody=0;
            if (e instanceof LivingEntity living) {
                head=living.headYaw; prevHead=living.prevHeadYaw; body=living.bodyYaw; prevBody=living.prevBodyYaw;
            }
            final float h=head, ph=prevHead, b=body, pb=prevBody;
            restore.add(() -> {
                e.setPosition(x,y,z); e.prevX=px; e.prevY=py; e.prevZ=pz;
                e.lastRenderX=rx; e.lastRenderY=ry; e.lastRenderZ=rz;
                e.setYaw(yaw); e.setPitch(pitch); e.prevYaw=prevYaw; e.prevPitch=prevPitch;
                if (e instanceof LivingEntity living) {
                    living.headYaw=h; living.prevHeadYaw=ph; living.bodyYaw=b; living.prevBodyYaw=pb;
                }
            });
            var p = track.sample(outputTime);
            e.setPosition(p.x(),p.y(),p.z());
            e.prevX=e.lastRenderX=p.x(); e.prevY=e.lastRenderY=p.y(); e.prevZ=e.lastRenderZ=p.z();
            e.setYaw((float)p.yaw()); e.prevYaw=e.getYaw(); e.setPitch((float)p.pitch()); e.prevPitch=e.getPitch();
            if (e instanceof LivingEntity living) {
                living.headYaw=living.prevHeadYaw=living.bodyYaw=living.prevBodyYaw=e.getYaw();
            }
        }
    }
}
