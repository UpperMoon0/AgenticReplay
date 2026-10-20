package com.replaymod.agent;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExportEntityTrackTest {
    private static JsonArray input() {
        return JsonParser.parseString("[{\"entityId\":731,\"poses\":[" +
                "{\"time\":0,\"x\":627,\"y\":2130,\"z\":116.5,\"yaw\":179,\"pitch\":78}," +
                "{\"time\":28000,\"x\":627,\"y\":63,\"z\":116.5,\"yaw\":-179,\"pitch\":78}]}]").getAsJsonArray();
    }
    @Test public void sixtyFpsDescentHasNoPacketHoldsOrCatchUpJumps() {
        var track=ExportEntityTrack.parse(input()).get(0);
        double previous=track.sample(0).y();
        for(int i=1;i<=1680;i++) {
            var p=track.sample(i*1000.0/60);
            assertEquals(2067.0/1680, previous-p.y(), 1e-9);
            previous=p.y();
        }
        assertEquals(180,track.sample(14000).yaw(),0);
    }
    @Test public void randomSeeksAndSampleOwnershipAreDeterministic() {
        JsonArray input=input(); var track=ExportEntityTrack.parse(input).get(0);
        input.get(0).getAsJsonObject().getAsJsonArray("poses").get(0).getAsJsonObject().addProperty("y",-999);
        assertEquals(63,track.sample(999999).y(),0);
        assertEquals(2130,track.sample(-1).y(),0);
        assertEquals(1096.5,track.sample(14000).y(),0);
        assertEquals(2130,track.sample(0).y(),0);
        assertTrue(ExportEntityTrack.parse(null).isEmpty());
    }
    @Test public void rejectsAmbiguousClocksAndUnsafeValues() {
        for(String key:new String[]{"time","x","pitch"}) {
            JsonArray a=input(); var poses=a.get(0).getAsJsonObject().getAsJsonArray("poses");
            poses.get(1).getAsJsonObject().addProperty(key,key.equals("time")?0:key.equals("pitch")?91:30000000);
            try { ExportEntityTrack.parse(a); fail(key); } catch(IllegalArgumentException expected) {}
        }
        JsonArray duplicate=input(); duplicate.add(duplicate.get(0).deepCopy());
        try { ExportEntityTrack.parse(duplicate); fail("duplicate"); } catch(IllegalArgumentException expected) {}
    }
}
