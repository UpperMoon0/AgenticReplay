package com.replaymod.render.hooks;

import org.junit.Test;
import static org.junit.Assert.*;

public class AngelicaFrameStampTest {
    private static final class GlobalFixture { private int celeritas$frame = 42; }

    @Test public void barrierSearchesAndLaterNormalFramesHaveDistinctOrderedStamps() throws Exception {
        GlobalFixture renderer = new GlobalFixture();
        assertEquals(42, AngelicaReplayCompat.nextSearchFrame(renderer));
        assertEquals(43, AngelicaReplayCompat.nextSearchFrame(renderer));
        // This is the next normal clipRenderersByFrustum invocation's post-increment.
        assertEquals(44, renderer.celeritas$frame++);
        assertEquals(45, AngelicaReplayCompat.nextSearchFrame(renderer));
    }

    @Test public void counterWrapMatchesAngelicasUnsignedFrameStampEncoding() throws Exception {
        GlobalFixture renderer = new GlobalFixture();
        renderer.celeritas$frame = Integer.MAX_VALUE;
        assertEquals(Integer.MAX_VALUE, AngelicaReplayCompat.nextSearchFrame(renderer));
        assertEquals(Integer.MIN_VALUE, renderer.celeritas$frame);
        assertEquals(Integer.MIN_VALUE, AngelicaReplayCompat.nextSearchFrame(renderer));
    }
}
