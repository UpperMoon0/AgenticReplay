package com.agenticreplay.legacy;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Render ticks continue when replay time is paused and after UI-driven close. */
public final class LegacyLifecycle {
    private final LegacyReplayBridge replay;

    public LegacyLifecycle(LegacyReplayBridge replay) { this.replay = replay; }

    public void register() { FMLCommonHandler.instance().bus().register(this); }

    @SubscribeEvent
    public void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) replay.restoreClosedReplayOptions();
    }
}
