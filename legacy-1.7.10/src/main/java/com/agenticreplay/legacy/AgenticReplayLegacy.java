package com.agenticreplay.legacy;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;

/** Client proxy keeps dedicated servers from resolving Minecraft client classes. */
@Mod(modid = "agenticreplay", name = "AgenticReplay", version = BuildInfo.VERSION, acceptedMinecraftVersions = "[1.7.10]",
     dependencies = "required-after:replaymod@[1.7.10-2.5.2];required-after:unimixins@[0.3.1,)", acceptableRemoteVersions = "*")
public final class AgenticReplayLegacy {
    @SidedProxy(clientSide = "com.agenticreplay.legacy.LegacyClientProxy", serverSide = "com.agenticreplay.legacy.LegacyServerProxy")
    public static LegacyServerProxy proxy;
    @Mod.EventHandler public void initialize(FMLInitializationEvent event) { proxy.initialize(); }
}
