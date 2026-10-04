package org.cttelsamicsterrassa.data.pipeline.runtime.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A scripted HTTP server on 127.0.0.1 (JDK only); responses are queued per method and path. */
public final class StubHttpServer implements AutoCloseable {

    public record Response(int status, Map<String, String> headers, byte[] body, Duration delay) {

        public static Response json(int status, String body) {
            return new Response(status, Map.of("Content-Type", "application/json"),
                    body.getBytes(StandardCharsets.UTF_8), Duration.ZERO);
        }

        public static Response bytes(int status, byte[] body, Map<String, String> headers) {
            return new Response(status, headers, body, Duration.ZERO);
        }

        public static Response empty(int status) {
            return new Response(status, Map.of(), new byte[0], Duration.ZERO);
        }

        public Response after(Duration delay) {
            return new Response(status, headers, body, delay);
        }
    }

    /** {@code query} is the raw (still encoded) query string, or null. */
    public record Recorded(
            String method, String path, Map<String, List<String>> headers, byte[] body, String query) {

        public String header(String name) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue().get(0);
                }
            }
            return null;
        }

        public String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private final HttpServer server;
    private final ExecutorService handlers = Executors.newCachedThreadPool();
    private final Map<String, Deque<Response>> queues = new ConcurrentHashMap<>();
    private final Map<String, Response> repeating = new ConcurrentHashMap<>();

    public final List<Recorded> requests = new CopyOnWriteArrayList<>();

    public StubHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start the stub server", e);
        }
        server.setExecutor(handlers);
        server.createContext("/", this::handle);
        server.start();
    }

    public URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /** Queues responses for the request; each is served once, in order. */
    public StubHttpServer on(String method, String path, Response... responses) {
        Deque<Response> queue = queues.computeIfAbsent(key(method, path), k -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        queue.addAll(List.of(responses));
        return this;
    }

    /** Served whenever the queue of the request is empty. */
    public StubHttpServer onRepeating(String method, String path, Response response) {
        repeating.put(key(method, path), response);
        return this;
    }

    public List<Recorded> requestsTo(String method, String path) {
        return requests.stream().filter(r -> r.method().equals(method) && r.path().equals(path)).toList();
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        Map<String, List<String>> headers = new LinkedHashMap<>(exchange.getRequestHeaders());
        requests.add(new Recorded(method, path, headers, body, exchange.getRequestURI().getRawQuery()));
        String key = key(method, path);
        Deque<Response> queue = queues.get(key);
        Response response = queue == null ? null : queue.poll();
        if (response == null) {
            response = repeating.get(key);
        }
        if (response == null) {
            response = Response.json(500, "{\"detail\":\"no stub for " + key + "\"}");
        }
        try {
            if (!response.delay().isZero()) {
                Thread.sleep(response.delay());
            }
            response.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
            exchange.sendResponseHeaders(response.status(), response.body().length == 0 ? -1 : response.body().length);
            if (response.body().length > 0) {
                exchange.getResponseBody().write(response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // the client gave up (read timeout); nothing to deliver
        } finally {
            exchange.close();
        }
    }

    private static String key(String method, String path) {
        return method + " " + path;
    }

    @Override
    public void close() {
        server.stop(0);
        handlers.shutdownNow();
    }
}
