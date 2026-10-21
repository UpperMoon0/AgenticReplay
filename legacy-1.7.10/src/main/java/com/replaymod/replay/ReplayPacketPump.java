/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.replay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;

/** Paused replay time stops client ticks; render-driven controls still need the delivered world state. */
public final class ReplayPacketPump {
    private ReplayPacketPump() {}
    public static void drain() {
        if (ReplayModReplay.instance == null) return;
        ReplayHandler replay = ReplayModReplay.instance.getReplayHandler();
        if (replay == null || !(replay.getReplaySender() instanceof FullReplaySender)
                || ((FullReplaySender) replay.getReplaySender()).isHurrying()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        NetHandlerPlayClient network = mc.getNetHandler();
        if (network != null) ReplayPacketQueue.drain(network);
    }
}
