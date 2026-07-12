package de.open4me.hibiscus.reports.mcp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

final class McpHttpServer
{
    private final int port;
    private final String token;
    private final McpJsonRpcHandler handler;
    private HttpServer server;
    private ExecutorService executor;

    McpHttpServer(int port, String token, McpJsonRpcHandler handler)
    {
        this.port = port;
        this.token = token;
        this.handler = handler;
    }

    void start() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "hibiscus-reports-mcp");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/mcp", this::handle);
        server.start();
    }

    void stop()
    {
        if (server != null)
            server.stop(0);
        if (executor != null)
            executor.shutdownNow();
    }

    boolean isRunning()
    {
        return server != null;
    }

    private void handle(HttpExchange exchange) throws IOException
    {
        try
        {
            if (!originAllowed(exchange.getRequestHeaders().get("Origin")))
            {
                send(exchange, 403, "text/plain", "Forbidden");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()))
            {
                send(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }
            if (!authenticated(exchange.getRequestHeaders().get("Authorization")))
            {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                send(exchange, 401, "text/plain", "Unauthorized");
                return;
            }
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String response = handler.handle(request);
            if (response == null)
            {
                send(exchange, 202, "application/json", "");
                return;
            }
            send(exchange, 200, "application/json", response);
        }
        finally
        {
            exchange.close();
        }
    }

    private boolean authenticated(List<String> values)
    {
        if (values == null || values.isEmpty())
            return false;
        return values.stream().anyMatch(value -> ("Bearer " + token).equals(value));
    }

    private static boolean originAllowed(List<String> origins)
    {
        if (origins == null || origins.isEmpty())
            return true;
        for (String origin : origins)
        {
            if (origin == null || origin.isBlank())
                continue;
            if (!(origin.startsWith("http://127.0.0.1:") || origin.startsWith("http://localhost:")))
                return false;
        }
        return true;
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException
    {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody())
        {
            output.write(bytes);
        }
    }
}
