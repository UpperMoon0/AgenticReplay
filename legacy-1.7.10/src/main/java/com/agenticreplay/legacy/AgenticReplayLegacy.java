package com.agenticreplay.legacy;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;

/** Client proxy keeps dedicated servers from resolving Minecraft client classes. */
@Mod(modid = "agenticreplay", name = "AgenticReplay", version = "0.7.0", acceptedMinecraftVersions = "[1.7.10]",
     dependencies = "required-after:replaymod@[1.7.10-2.5.2]", acceptableRemoteVersions = "*")
public final class AgenticReplayLegacy {
    @SidedProxy(clientSide = "com.agenticreplay.legacy.LegacyClientProxy", serverSide = "com.agenticreplay.legacy.LegacyServerProxy")
    public static LegacyServerProxy proxy;
    @Mod.EventHandler public void initialize(FMLInitializationEvent event) { proxy.initialize(); }
}
