package com.agenticreplay.legacy;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Java 8/Gson 2.2.4 compatible, authenticated loopback-only RPC. */
public final class LegacyHttpTransport implements AutoCloseable {
    public interface Dispatcher { JsonElement call(String method, JsonObject params) throws Exception; }
    private final HttpServer server;
    private final ExecutorService workers;

    public LegacyHttpTransport(int port, final String token, final Consumer<Runnable> enqueue,
                               final Dispatcher dispatcher) throws IOException {
        this(port, token, enqueue, dispatcher, 30000);
    }
    LegacyHttpTransport(int port, final String token, final Consumer<Runnable> enqueue,
                        final Dispatcher dispatcher, final long timeoutMillis) throws IOException {
        if (token == null || token.length() < 32) throw new IllegalArgumentException("API token needs at least 32 characters");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 8);
        workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(8), r -> {
            Thread t = new Thread(r, "AgenticReplay legacy HTTP"); t.setDaemon(true); return t;
        }, new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(workers);
        server.createContext("/rpc", exchange -> {
            int status = 200;
            JsonObject response = new JsonObject();
            try {
                String supplied = exchange.getRequestHeaders().getFirst("Authorization");
                if (supplied == null || !MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                    status = 401; throw new IllegalArgumentException("Unauthorized");
                }
                if (!"POST".equals(exchange.getRequestMethod()) || !"/rpc".equals(exchange.getRequestURI().getPath())) {
                    status = 405; throw new IllegalArgumentException("Use POST /rpc");
                }
                byte[] bytes = readBounded(exchange.getRequestBody());
                if (bytes.length > 65536) { status = 413; throw new IllegalArgumentException("Request too large"); }
                JsonObject request = new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonElement name = request.get("method");
                if (name == null || !name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString())
                    throw new IllegalArgumentException("method must be a string");
                String method = name.getAsString();
                JsonObject params = request.has("params") ? request.get("params").getAsJsonObject() : new JsonObject();
                FutureTask<JsonElement> task = new FutureTask<JsonElement>(() -> dispatcher.call(method, params));
                enqueue.accept(task);
                try { response.add("result", task.get(timeoutMillis, TimeUnit.MILLISECONDS)); }
                catch (TimeoutException timeout) {
                    task.cancel(false); status = 504;
                    throw new IllegalStateException("Client operation timed out; inspect state before retrying");
                } catch (InterruptedException interrupted) {
                    task.cancel(false); Thread.currentThread().interrupt(); throw interrupted;
                }
                response.addProperty("ok", true);
            } catch (Exception exception) {
                Throwable cause = exception instanceof ExecutionException ? exception.getCause() : exception;
                if (status == 200) status = 400;
                response.addProperty("ok", false);
                response.addProperty("error", cause.getClass().getSimpleName());
                response.addProperty("message", cause.getMessage() == null ? "" : cause.getMessage().replace(token, "[redacted]"));
            }
            byte[] result = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            try {
                exchange.sendResponseHeaders(status, result.length);
                try (OutputStream output = exchange.getResponseBody()) { output.write(result); }
            } finally { exchange.close(); }
        });
        server.start();
    }
    private static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (result.size() <= 65536) {
            int n = input.read(buffer, 0, Math.min(buffer.length, 65537 - result.size()));
            if (n < 0) break;
            result.write(buffer, 0, n);
        }
        return result.toByteArray();
    }
    public int port() { return server.getAddress().getPort(); }
    @Override public void close() { server.stop(0); workers.shutdownNow(); }
}
