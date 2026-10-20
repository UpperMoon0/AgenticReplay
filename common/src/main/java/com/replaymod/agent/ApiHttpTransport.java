package com.replaymod.agent;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Bounded transport separated from the Minecraft lifecycle for contract tests. */
public final class ApiHttpTransport implements AutoCloseable {
    @FunctionalInterface public interface Dispatcher {
        JsonElement call(String method, JsonObject params) throws Exception;
    }
    private final HttpServer server;
    private final ExecutorService workers;
    public ApiHttpTransport(int port, String token, Consumer<Runnable> enqueue, Dispatcher dispatcher) throws java.io.IOException {
        if (token == null || token.length() < 32) throw new IllegalArgumentException("API token needs at least 32 characters");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 8);
        workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8), runnable -> {
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
                FutureTask<JsonElement> task = new FutureTask<>(() -> dispatcher.call(method, params));
                enqueue.accept(task);
                try { response.add("result", task.get(30, TimeUnit.SECONDS)); }
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
                String message = cause.getMessage();
                response.addProperty("message", message == null ? "" : message.replace(token, "[redacted]"));
            }
            byte[] result = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(status, result.length);
            try (var stream = exchange.getResponseBody()) { stream.write(result); }
            finally { exchange.close(); }
        });
        server.start();
    }
    public int port() { return server.getAddress().getPort(); }
    @Override public void close() { server.stop(0); workers.shutdownNow(); }
}
