package com.replaymod.render.hooks;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class EmbeddiumTerrainBarrierTest {
    @Test public void initialEmptyWorkersDoNotBypassNewlyDiscoveredBuildsAndUploads() {
        AtomicInteger passes = new AtomicInteger();
        // First graph discovers builds; uploads dirty the next graph; only the
        // third pass has no queued work and a stable render list.
        EmbeddiumTerrainBarrier.complete(passes::incrementAndGet, () -> passes.get() < 3);
        assertEquals(3, passes.get());
    }

    @Test public void evenAnInitiallyReadyStateRunsTheCameraSetupFirst() {
        AtomicInteger passes = new AtomicInteger();
        EmbeddiumTerrainBarrier.complete(passes::incrementAndGet, () -> false);
        assertEquals(1, passes.get());
    }

    @Test public void nonConvergingTerrainFailsTheExportInsteadOfAcceptingAnEmptyFrame() {
        AtomicLong clock = new AtomicLong();
        try {
            EmbeddiumTerrainBarrier.complete(() -> {}, () -> true, clock::getAndIncrement, 3);
            fail("Expected failed export");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("export aborted"));
        }
    }
}
