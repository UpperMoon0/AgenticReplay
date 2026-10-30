/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.replay;

import java.lang.reflect.Field;
import java.util.Queue;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.client.network.NetHandlerPlayClient;

/** Drain only already-delivered replay packets, independently of vanilla/Hodgepodge tick budgets. */
public final class ReplayPacketQueue {
    private ReplayPacketQueue() {}
    public static void drain(NetHandlerPlayClient handler) {
        try {
            NetworkManager manager=handler.getNetworkManager();
            Queue<?> queue = received(manager);
            Object packet;
            while ((packet = queue.poll()) != null) ((Packet) packet).processPacket(manager.getNetHandler());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot drain legacy replay receive queue", failure);
        }
    }
    public static Queue<?> received(Object manager) throws ReflectiveOperationException {
        for (Class<?> owner=manager.getClass();owner!=null;owner=owner.getSuperclass())
        for (String name : new String[]{"receivedPacketsQueue", "field_150748_i"}) {
            try {
                Field field=owner.getDeclaredField(name);field.setAccessible(true);
                return (Queue<?>)field.get(manager);
            } catch (NoSuchFieldException absent) { /* Try the pinned production SRG name. */ }
        }
        throw new NoSuchFieldException("Legacy NetworkManager receive queue");
    }
}
