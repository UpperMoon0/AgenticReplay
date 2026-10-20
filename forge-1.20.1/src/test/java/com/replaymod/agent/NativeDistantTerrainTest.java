package com.replaymod.agent;

import org.junit.Test;
import static org.junit.Assert.*;

public class NativeDistantTerrainTest {
    public static class Level { int calls; public void clientTick() { calls++; } }
    public static class Wrapper { final Object level; Wrapper(Object level) { this.level=level; } public Object getDhLevel() { return level; } }
    public static class FailedLevel { public void clientTick() { throw new IllegalArgumentException("DH failed"); } }
    @Test public void frozenWorldStillUpdatesDetailForEveryOutputFrame() {
        Level level=new Level(); Wrapper wrapper=new Wrapper(level);
        for(int outputFrame=0;outputFrame<180;outputFrame++) NativeDistantTerrain.updateWrapper(wrapper);
        assertEquals(180,level.calls);
    }
    @Test public void absentLevelDuringLoadIsHarmless() {
        NativeDistantTerrain.updateWrapper(null);
        NativeDistantTerrain.updateWrapper(new Wrapper(null));
    }
    @Test public void optionalApiFailureIsReportedInsteadOfSilentlyRenderingMissingTerrain() {
        try { NativeDistantTerrain.updateWrapper(new Wrapper(new FailedLevel())); fail("DH failure hidden"); }
        catch(IllegalStateException expected) { assertEquals("DH failed",expected.getCause().getMessage()); }
    }
}
