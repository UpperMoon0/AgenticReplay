/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.recording.packet;

import com.replaymod.core.ReplayMod;
import com.replaymod.core.versions.MCVer;
import com.replaymod.replaystudio.replay.ReplayFile;
import com.replaymod.replaystudio.replay.ReplayMetaData;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.network.Packet;

/** Preserve arrival order when a client event saves before scheduled tasks run. */
public final class OrderedPacketListener extends PacketListener {
    private final AtomicBoolean disconnectQueued = new AtomicBoolean();
    private final OrderedClientTasks ordered = new OrderedClientTasks(
            MCVer::isOnMainThread, MCVer::scheduleOnMainThread);

    public OrderedPacketListener(ReplayMod core, Path outputPath, ReplayFile replayFile,
                                 ReplayMetaData metadata) throws IOException {
        super(core, outputPath, replayFile, metadata);
    }

    @Override public void save(Packet packet) {
        // Packets already enqueued drain before finalization. Never submit a
        // late packet after the save executor has been shut down.
        if (!disconnectQueued.get()) ordered.submit(() -> super.save(packet));
    }

    @Override public void channelInactive(ChannelHandlerContext context) {
        if (disconnectQueued.compareAndSet(false, true)) {
            ordered.submit(() -> super.channelInactive(context));
        }
    }
}
