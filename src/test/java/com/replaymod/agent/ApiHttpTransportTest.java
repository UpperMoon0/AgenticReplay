package com.replaymod.agent;

import com.google.gson.*;
import org.junit.Test;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ApiHttpTransportTest {
    private static final String TOKEN = "0123456789abcdef0123456789abcdef";
    private HttpResponse<String> request(ApiHttpTransport server, String auth, String method, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/rpc"));
        if (auth != null) builder.header("Authorization", auth);
        return HttpClient.newHttpClient().send(builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    @Test public void rejectsUnauthenticatedMutations() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (var server = new ApiHttpTransport(0, TOKEN, Runnable::run, (method, params) -> {
            calls.incrementAndGet(); return new JsonObject();
        })) {
            assertEquals(401, request(server, null, "POST", "{\"method\":\"replay.delete\"}").statusCode());
            assertEquals(401, request(server, "Bearer wrong", "POST", "{\"method\":\"status\"}").statusCode());
            assertEquals(0, calls.get());
        }
    }
    @Test public void dispatchesOnAssignedThread() throws Exception {
        ExecutorService client = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "test-client"));
        try (var server = new ApiHttpTransport(0, TOKEN, client::execute, (method, params) -> {
            assertEquals("test-client", Thread.currentThread().getName());
            assertEquals("playback.seek", method);
            return params;
        })) {
            var response = request(server, "Bearer " + TOKEN, "POST", "{\"method\":\"playback.seek\",\"params\":{\"time\":42}}");
            assertEquals(200, response.statusCode());
            var body = JsonParser.parseString(response.body()).getAsJsonObject();
            assertTrue(body.get("ok").getAsBoolean());
            assertEquals(42, body.getAsJsonObject("result").get("time").getAsInt());
        } finally { client.shutdownNow(); }
    }
    @Test public void rejectsInvalidAndOversizedRequestsBeforeDispatch() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (var server = new ApiHttpTransport(0, TOKEN, Runnable::run, (method, params) -> {
            calls.incrementAndGet(); return new JsonObject();
        })) {
            assertEquals(405, request(server, "Bearer " + TOKEN, "GET", "").statusCode());
            assertEquals(400, request(server, "Bearer " + TOKEN, "POST", "invalid").statusCode());
            assertEquals(413, request(server, "Bearer " + TOKEN, "POST", "x".repeat(65537)).statusCode());
            assertEquals(0, calls.get());
        }
    }
    @Test public void operationErrorsAreStructuredAndRedacted() throws Exception {
        try (var server = new ApiHttpTransport(0, TOKEN, Runnable::run, (method, params) -> {
            throw new IllegalStateException("failure " + TOKEN);
        })) {
            var response = request(server, "Bearer " + TOKEN, "POST", "{\"method\":\"status\"}");
            assertEquals(400, response.statusCode());
            assertFalse(response.body().contains(TOKEN));
            assertTrue(response.body().contains("IllegalStateException"));
        }
    }
    @Test public void rejectsWeakTokens() throws Exception {
        try { new ApiHttpTransport(0, "weak", Runnable::run, (method, params) -> new JsonObject()); fail("Accepted weak token"); }
        catch (IllegalArgumentException expected) {}
    }
}
