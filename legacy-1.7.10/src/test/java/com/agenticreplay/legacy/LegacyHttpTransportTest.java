package com.agenticreplay.legacy;

import com.google.gson.*;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class LegacyHttpTransportTest {
    private static final String TOKEN = "0123456789abcdef0123456789abcdef";
    private static final class Response { int status; String body; }
    private Response request(LegacyHttpTransport server, String auth, String verb, String path, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + server.port() + path).openConnection(Proxy.NO_PROXY);
        c.setConnectTimeout(2000); c.setReadTimeout(5000); c.setRequestMethod(verb);
        if (auth != null) c.setRequestProperty("Authorization", auth);
        if (!body.isEmpty()) {
            c.setDoOutput(true);
            try (OutputStream out = c.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        Response r = new Response(); r.status = c.getResponseCode();
        try (InputStream in = r.status < 400 ? c.getInputStream() : c.getErrorStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] bytes = new byte[4096]; int n;
            while ((n = in.read(bytes)) >= 0) out.write(bytes, 0, n);
            r.body = new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally { c.disconnect(); }
        return r;
    }
    @Test public void rejectsUnauthorizedBeforeDispatch() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (LegacyHttpTransport server = new LegacyHttpTransport(0, TOKEN, Runnable::run, (m,p) -> {
            calls.incrementAndGet(); return p;
        })) {
            assertEquals(401, request(server,null,"POST","/rpc","{\"method\":\"replay.delete\"}").status);
            assertEquals(401, request(server,"Bearer wrong","POST","/rpc","{}").status);
            assertEquals(0,calls.get());
        }
    }
    @Test public void dispatchesOnClientExecutorAndPreservesEnvelope() throws Exception {
        ExecutorService client = Executors.newSingleThreadExecutor(r -> new Thread(r,"test-client"));
        try (LegacyHttpTransport server = new LegacyHttpTransport(0,TOKEN,client::execute,(m,p)-> {
            assertEquals("test-client",Thread.currentThread().getName()); assertEquals("playback.seek",m); return p;
        })) {
            Response r = request(server,"Bearer "+TOKEN,"POST","/rpc","{\"method\":\"playback.seek\",\"params\":{\"time\":42}}");
            assertEquals(200,r.status);
            JsonObject body = new JsonParser().parse(r.body).getAsJsonObject();
            assertTrue(body.get("ok").getAsBoolean()); assertEquals(42,body.getAsJsonObject("result").get("time").getAsInt());
        } finally { client.shutdownNow(); }
    }
    @Test public void rejectsMalformedOversizedAndWrongRoute() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (LegacyHttpTransport server = new LegacyHttpTransport(0,TOKEN,Runnable::run,(m,p)-> { calls.incrementAndGet(); return p; })) {
            assertEquals(405,request(server,"Bearer "+TOKEN,"GET","/rpc","").status);
            assertEquals(405,request(server,"Bearer "+TOKEN,"POST","/rpc/extra","{}").status);
            for (String body : new String[]{"invalid","{}","{\"method\":3}","{\"method\":\"status\",\"params\":null}"})
                assertEquals(400,request(server,"Bearer "+TOKEN,"POST","/rpc",body).status);
            assertEquals(413,request(server,"Bearer "+TOKEN,"POST","/rpc",new String(new char[65537]).replace('\0','x')).status);
            assertEquals(0,calls.get());
        }
    }
    @Test public void cancelsQueuedMutationOnTimeout() throws Exception {
        AtomicReference<Runnable> queued = new AtomicReference<Runnable>(); AtomicInteger calls = new AtomicInteger();
        try (LegacyHttpTransport server = new LegacyHttpTransport(0,TOKEN,queued::set,(m,p)-> {calls.incrementAndGet();return p;},20)) {
            assertEquals(504,request(server,"Bearer "+TOKEN,"POST","/rpc","{\"method\":\"status\"}").status);
            queued.get().run(); assertEquals(0,calls.get());
        }
    }
    @Test public void redactsTokenFromErrors() throws Exception {
        try (LegacyHttpTransport server = new LegacyHttpTransport(0,TOKEN,Runnable::run,(m,p)-> {throw new IllegalStateException(TOKEN);})) {
            Response r = request(server,"Bearer "+TOKEN,"POST","/rpc","{\"method\":\"status\"}");
            assertEquals(400,r.status); assertFalse(r.body.contains(TOKEN)); assertTrue(r.body.contains("[redacted]"));
        }
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsWeakToken() throws Exception {
        new LegacyHttpTransport(0,"weak",Runnable::run,(m,p)->p);
    }
}
