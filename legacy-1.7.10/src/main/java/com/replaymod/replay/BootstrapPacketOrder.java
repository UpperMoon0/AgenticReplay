/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.replay;

import java.util.ArrayList;
import java.util.List;

/** Backward compatibility for old recordings whose synthetic player predates JoinGame. */
public final class BootstrapPacketOrder {
    private boolean joined;
    private final List<byte[]> pending = new ArrayList<byte[]>();

    public synchronized boolean defer(byte[] packet) {
        if (!joined && packet.length > 0 && packet[0] == 0x0c) {
            if (pending.size() >= 1024) throw new IllegalStateException("Too many player spawns before replay JoinGame");
            pending.add(packet.clone());
            return true;
        }
        return false;
    }

    /** Call only AFTER JoinGame has been forwarded into the client packet queue. */
    public synchronized List<byte[]> joined() {
        joined = true;
        List<byte[]> result = new ArrayList<byte[]>(pending);
        pending.clear();
        return result;
    }

    public synchronized void reset() { joined = false; pending.clear(); }
}
