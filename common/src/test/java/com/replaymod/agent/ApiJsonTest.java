package com.replaymod.agent;
import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ApiJsonTest {
    private JsonObject json(String text) { return new JsonParser().parse(text).getAsJsonObject(); }
    private void rejects(Runnable action) {
        try { action.run(); fail("Expected strict validation failure"); } catch (IllegalArgumentException expected) { }
    }
    @Test public void acceptsCorrectPrimitiveTypes() {
        JsonObject p=json("{\"name\":\"actor\",\"enabled\":true,\"x\":12.5,\"slot\":3}");
        assertEquals("actor",ApiJson.text(p,"name")); assertTrue(ApiJson.bool(p,"enabled"));
        assertEquals(12.5,ApiJson.number(p,"x"),0); assertEquals(3,ApiJson.integer(p,"slot",0,0,8));
    }
    @Test public void rejectsCoercionAcrossPrimitiveTypes() {
        JsonObject p=json("{\"string\":\"true\",\"number\":1,\"boolean\":true}");
        rejects(()->ApiJson.bool(p,"string")); rejects(()->ApiJson.bool(p,"number"));
        rejects(()->ApiJson.number(p,"string")); rejects(()->ApiJson.number(p,"boolean"));
        rejects(()->ApiJson.string(p,"number")); rejects(()->ApiJson.text(p,"boolean"));
    }
    @Test public void fallbackAppliesOnlyToMissingValues() {
        JsonObject p=json("{\"null\":null}");
        assertTrue(ApiJson.bool(p,"missing",true)); assertEquals(2,ApiJson.number(p,"missing",2),0);
        rejects(()->ApiJson.bool(p,"null",true)); rejects(()->ApiJson.number(p,"null",2));
        rejects(()->ApiJson.string(p,"missing"));
    }
    @Test public void rejectsNonfiniteNumbers() {
        for(double x:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            JsonObject p=new JsonObject();p.addProperty("x",x);rejects(()->ApiJson.number(p,"x"));
        }
    }
    @Test public void enforcesIntegerRangeWithoutTruncation() {
        for(String x:new String[]{"1.5","-1","9","2147483648"}) {
            JsonObject p=json("{\"slot\":"+x+"}");rejects(()->ApiJson.integer(p,"slot",0,0,8));
        }
        assertEquals(Integer.MIN_VALUE,ApiJson.integer(json("{\"x\":-2147483648}"),"x",0,Integer.MIN_VALUE,Integer.MAX_VALUE));
    }
    @Test public void distinguishesStringFromNonblankText() {
        JsonObject p=new JsonObject();p.addProperty("x","");assertEquals("",ApiJson.string(p,"x"));rejects(()->ApiJson.text(p,"x"));
        p.addProperty("x"," \t\u2003\n");rejects(()->ApiJson.text(p,"x"));
        p.addProperty("x","撮影");assertEquals("撮影",ApiJson.text(p,"x"));
    }
    @Test public void rejectsNonObjects() {
        JsonObject p=json("{\"ok\":{},\"bad\":[],\"null\":null}");
        assertTrue(ApiJson.object(p,"ok").entrySet().isEmpty());
        rejects(()->ApiJson.object(p,"bad"));rejects(()->ApiJson.object(p,"null"));rejects(()->ApiJson.object(p,"missing"));
    }
}
