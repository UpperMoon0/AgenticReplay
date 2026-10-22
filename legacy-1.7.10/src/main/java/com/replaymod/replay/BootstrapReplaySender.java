/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.replay;

import com.replaymod.replaystudio.replay.ReplayFile;
import io.netty.channel.ChannelHandlerContext;

/** Preserve legacy synthetic self-spawns without constructing players in a null world. */
public final class BootstrapReplaySender extends FullReplaySender {
    private final BootstrapPacketOrder bootstrap = new BootstrapPacketOrder();

    public BootstrapReplaySender(ReplayHandler handler, ReplayFile file, boolean async) throws java.io.IOException {
        super(handler, file, async);
    }

    @Override public void channelActive(ChannelHandlerContext context) throws Exception {
        bootstrap.reset();
        super.channelActive(context);
    }

    @Override public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
        if (message instanceof byte[] && bootstrap.defer((byte[]) message)) return;
        super.channelRead(context, message);
        ReplaySyncDelivery.drain(context,isAsyncMode());
        if (message instanceof byte[]) {
            byte[] packet = (byte[]) message;
            if (packet.length > 0 && packet[0] == 0x01) {
                for (byte[] pending : bootstrap.joined()) {
                    super.channelRead(context, pending);
                    ReplaySyncDelivery.drain(context,isAsyncMode());
                }
            }
        }
    }
}
