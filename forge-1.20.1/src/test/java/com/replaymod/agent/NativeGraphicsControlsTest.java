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
}
