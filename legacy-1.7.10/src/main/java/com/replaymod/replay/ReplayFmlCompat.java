/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.replay;
/** Historical custom-payload decode failures must not disconnect the offline replay channel. */
public final class ReplayFmlCompat {
 private ReplayFmlCompat() {}
 public static boolean keepReplayOpen(String reason) {
  if(ReplayModReplay.instance==null||ReplayModReplay.instance.getReplayHandler()==null)return false;
  if(net.minecraft.client.Minecraft.getMinecraft().theWorld==null
      ||ReplayModReplay.instance.getReplayHandler().getCameraEntity()==null)return false;
  org.apache.logging.log4j.LogManager.getLogger("AgenticReplay").warn("Historical mod packet rejected during offline playback: {}",reason);
  return true;
 }
}
