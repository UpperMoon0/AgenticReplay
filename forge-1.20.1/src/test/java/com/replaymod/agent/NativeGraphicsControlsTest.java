package com.replaymod.agent;
import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;
public class NativeGraphicsControlsTest {
    @Test public void rejectsInvalidSettingsBeforeAnyMutation() {
        for (String json : new String[]{"{\"dhDistance\":15}","{\"dhDistance\":257}","{\"dhDistance\":32.5}","{\"dhDistance\":\"64\"}","{\"dhEnabled\":\"false\"}","{\"clouds\":null}","{\"arbitraryMethod\":true}"}) {
            try { NativeGraphicsControls.validate(JsonParser.parseString(json).getAsJsonObject()); fail("Invalid graphics settings accepted: " + json); }
            catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void acceptsReadOnlyAndBoundedNativeSettings() {
        for (String json : new String[]{"{}","{\"dhDistance\":16}","{\"dhDistance\":256}","{\"clouds\":false,\"dhEnabled\":true,\"shadersEnabled\":false}"})
            NativeGraphicsControls.validate(JsonParser.parseString(json).getAsJsonObject());
    }
    @Test public void loadingNeverInitializesOptionalApisOrStartsGraphicsChanges() throws Exception {
        int[] calls = {0};
        var result = JsonParser.parseString("{\"ready\":true}").getAsJsonObject();
        NativeGraphicsControls.ReadyGraphics action = () -> { calls[0]++; return result; };
        for (int attempt = 0; attempt < 3; attempt++) {
            try { NativeGraphicsControls.whenReady(true, action); fail("Loading graphics access accepted"); }
            catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("player.state.loading=false")); }
        }
        assertEquals(0, calls[0]);
        assertSame(result, NativeGraphicsControls.whenReady(false, action));
        assertEquals(1, calls[0]);
    }
    @Test public void unchangedShaderRequestsDoNotScheduleAnotherResourceReload() throws Exception {
        boolean[] state = {true}; int[] reloads = {0};
        NativeGraphicsControls.ShaderStateChange change = enabled -> { state[0] = enabled; reloads[0]++; };
        NativeGraphicsControls.applyShaderState(state[0], true, change);
        assertEquals(0, reloads[0]);
        NativeGraphicsControls.applyShaderState(state[0], false, change);
        NativeGraphicsControls.applyShaderState(state[0], false, change);
        assertFalse(state[0]); assertEquals(1, reloads[0]);
        NativeGraphicsControls.applyShaderState(state[0], true, change);
        assertTrue(state[0]); assertEquals(2, reloads[0]);
    }
}
