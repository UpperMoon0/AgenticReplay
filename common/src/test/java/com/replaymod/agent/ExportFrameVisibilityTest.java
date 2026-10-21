package com.replaymod.agent;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExportFrameVisibilityTest {
    @Test public void trackedAirborneActorsAreEligibleWithoutChangingOtherActorsOrBlocks() {
        assertFalse(ExportFrameVisibility.actor(101));
        try (var scope = ExportFrameVisibility.open(List.of(new ExportFrameVisibility.Actor(101, 1800.5, 850.2, -609.5)))) {
            assertTrue(ExportFrameVisibility.actor(101));
            assertTrue(ExportFrameVisibility.block(1800, 850, -610));
            assertFalse(ExportFrameVisibility.actor(102));
            assertFalse(ExportFrameVisibility.block(1800, 849, -610));
        }
        assertFalse(ExportFrameVisibility.actor(101));
        assertFalse(ExportFrameVisibility.block(1800, 850, -610));
    }
    @Test public void frameFailureAndBeforeTickCleanupCannotLeakEligibility() {
        try {
            try (var scope = ExportFrameVisibility.open(List.of(new ExportFrameVisibility.Actor(7, -8_000_000, 7_999_999, 2)))) {
                assertTrue(ExportFrameVisibility.actor(7));
                throw new IllegalStateException("render failed");
            }
        } catch (IllegalStateException expected) { }
        assertFalse(ExportFrameVisibility.actor(7));
    }
    @Test public void nestedFramesRestoreTheirPreviousEligibilityAndRejectWrongCloseOrder() {
        var first = ExportFrameVisibility.open(List.of(new ExportFrameVisibility.Actor(1, 0, 0, 0)));
        var second = ExportFrameVisibility.open(List.of(new ExportFrameVisibility.Actor(2, 1, 1, 1)));
        try {
            try { first.close(); fail("Out of order close accepted"); } catch (IllegalStateException expected) { }
            assertTrue(ExportFrameVisibility.actor(2)); assertFalse(ExportFrameVisibility.actor(1));
            second.close(); assertTrue(ExportFrameVisibility.actor(1)); assertFalse(ExportFrameVisibility.actor(2));
        } finally { second.close(); first.close(); }
        first.close(); assertFalse(ExportFrameVisibility.actor(1));
    }
}
