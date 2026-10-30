/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.replay;

/** A delayed chunk description must not overwrite later recorded block events. */
public final class HodgepodgeReplayCompat {
    private HodgepodgeReplayCompat() {}
    public static boolean useAsync(boolean configured) {
        return useAsync(configured, ReplayModReplay.instance != null
                && ReplayModReplay.instance.getReplayHandler() != null);
    }
    public static boolean useAsync(boolean configured, boolean replayOpen) {
        return configured && !replayOpen;
    }
}
