package com.agenticreplay.legacy;

import com.google.gson.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class LegacyRenderCompatibilityTest {
    @Test public void absentRendererIsNotCertified() {
        JsonObject result = LegacyRenderCompatibility.describe(null);
        assertFalse(result.get("angelicaLoaded").getAsBoolean());
        assertFalse(result.get("shaderExportVerified").getAsBoolean());
        assertFalse(result.get("remoteExportAvailable").getAsBoolean());
        assertFalse(result.has("angelicaVersion"));
    }
    @Test public void inspectedRendererReportsKnownGaps() {
        JsonObject result = LegacyRenderCompatibility.describe("2.2.28");
        assertTrue(result.get("angelicaLoaded").getAsBoolean());
        assertFalse(result.get("shaderExportVerified").getAsBoolean());
        assertEquals("2.2.28", result.get("angelicaVersion").getAsString());
        assertEquals(5, result.getAsJsonArray("warnings").size());
        assertTrue(result.toString().contains("wall clock"));
        assertTrue(result.toString().contains("incomplete terrain"));
        assertTrue(result.toString().contains("first-person hand"));
    }
    @Test public void otherVersionDoesNotInheritCertification() {
        JsonObject result = LegacyRenderCompatibility.describe("2.2.34");
        assertEquals("2.2.34", result.get("angelicaVersion").getAsString());
        assertEquals("2.2.28", result.get("angelicaSourceInspectedVersion").getAsString());
        assertFalse(result.get("shaderExportVerified").getAsBoolean());
        assertTrue(result.toString().contains("separate validation"));
    }
}
