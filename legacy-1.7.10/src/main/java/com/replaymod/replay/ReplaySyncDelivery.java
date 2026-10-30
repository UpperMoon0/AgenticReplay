/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.replay;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import java.util.Queue;
/** Forge custom payloads can execute immediately; earlier vanilla packets must precede them. */
public final class ReplaySyncDelivery {
 private ReplaySyncDelivery() {}
 public static void drain(ChannelHandlerContext context,boolean async) {
  if(async)return;
  NetworkManager manager=(NetworkManager)context.pipeline().get("packet_handler");
  try {
   Queue<?> queue=ReplayPacketQueue.received(manager);Object packet;
   while((packet=queue.poll())!=null)((Packet)packet).processPacket(manager.getNetHandler());
  }catch(ReflectiveOperationException failure){throw new IllegalStateException("Legacy synchronous packet delivery failed",failure);}
 }
}
