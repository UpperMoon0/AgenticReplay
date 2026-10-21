package com.agenticreplay.legacy;

import org.junit.Test;
import java.lang.reflect.Field;
import static org.junit.Assert.assertEquals;

/** Verify the pinned private seam without initializing Minecraft or OpenGL. */
public class LegacyReplayAbiTest {
    @Test public void requiredLegacyUiFieldsMatchTheOfficialEngine() throws Exception {
        String[][] fields = {
                {"com.replaymod.recording.handler.ConnectionEventHandler", "guiControls", "com.replaymod.recording.gui.GuiRecordingControls"},
                {"com.replaymod.recording.gui.GuiRecordingControls", "buttonPauseResume", "com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton"},
                {"com.replaymod.recording.gui.GuiRecordingControls", "buttonStartStop", "com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton"},
                {"com.replaymod.replay.gui.overlay.GuiMarkerTimeline", "markers", "java.util.Set"}
        };
        for (String[] expected : fields) {
            Class<?> owner = Class.forName(expected[0], false, getClass().getClassLoader());
            Field field = owner.getDeclaredField(expected[1]);
            assertEquals(expected[0] + "." + expected[1], expected[2], field.getType().getName());
            field.setAccessible(true);
        }
    }
}
