/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.replay;

import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S0APacketUseBed;

/** Historical files can contain bed updates for an unrecorded respawned player. */
public final class ReplayMissingEntityCompat {
    private ReplayMissingEntityCompat() {}
    public static boolean skipBed(Object packet) {
        if (ReplayModReplay.instance == null || ReplayModReplay.instance.getReplayHandler() == null) return false;
        Minecraft mc = Minecraft.getMinecraft();
        return mc.theWorld == null || ((S0APacketUseBed) packet).func_149091_a(mc.theWorld) == null;
    }
}
