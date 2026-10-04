package com.replaymod.agent;

import com.google.gson.*;
import com.replaymod.core.Module;
import com.replaymod.core.ReplayMod;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;

/** Opt-in loopback RPC transport. Minecraft operations always run on its client thread. */
public final class AgentApiServer implements Module {
    private final ReplayMod core;
    private final AgentReplayApi api;
    public AgentApiServer(ReplayMod core) { this.core = core; this.api = new AgentReplayApi(core); }
    @Override public void initClient() {
        String token = System.getProperty("agenticreplay.token", "");
        if (token.isEmpty()) return;
        if (token.length() < 32) throw new IllegalArgumentException("agenticreplay.token needs at least 32 characters");
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",
                    Integer.getInteger("agenticreplay.port", 8766)), 8);
            ExecutorService workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(8), runnable -> {
                        Thread thread = new Thread(runnable, "AgenticReplay HTTP"); thread.setDaemon(true); return thread;
                    }, new ThreadPoolExecutor.AbortPolicy());
            server.setExecutor(workers);
            server.createContext("/rpc", exchange -> {
                int status = 200;
                JsonObject response = new JsonObject();
                try {
                    String supplied = exchange.getRequestHeaders().getFirst("Authorization");
                    if (supplied == null || !MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                            supplied.getBytes(StandardCharsets.UTF_8))) {
                        status = 401; throw new IllegalArgumentException("Unauthorized");
                    }
                    if (!exchange.getRequestMethod().equals("POST") || !exchange.getRequestURI().getPath().equals("/rpc")) {
                        status = 405; throw new IllegalArgumentException("Use POST /rpc");
                    }
                    byte[] bytes = exchange.getRequestBody().readNBytes(65537);
                    if (bytes.length > 65536) { status = 413; throw new IllegalArgumentException("Request too large"); }
                    JsonObject request = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                    String method = request.get("method").getAsString();
                    JsonObject params = request.has("params") ? request.getAsJsonObject("params") : new JsonObject();
                    FutureTask<JsonElement> task = new FutureTask<>(() -> api.call(method, params));
                    core.getMinecraft().execute(task);
                    try { response.add("result", task.get(30, TimeUnit.SECONDS)); }
                    catch (TimeoutException timeout) {
                        task.cancel(false);
                        status = 504;
                        throw new IllegalStateException("Client operation timed out; inspect state before retrying");
                    }
                    response.addProperty("ok", true);
                } catch (Exception exception) {
                    Throwable cause = exception instanceof ExecutionException ? exception.getCause() : exception;
                    if (status == 200) status = 400;
                    response.addProperty("ok", false);
                    response.addProperty("error", cause.getClass().getSimpleName());
                    response.addProperty("message", cause.getMessage());
                }
                byte[] result = response.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(status, result.length);
                try (var stream = exchange.getResponseBody()) { stream.write(result); }
                finally { exchange.close(); }
            });
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> { server.stop(0); workers.shutdownNow(); }));
        } catch (java.io.IOException exception) { throw new IllegalStateException("Cannot start AgenticReplay API", exception); }
    }
}
