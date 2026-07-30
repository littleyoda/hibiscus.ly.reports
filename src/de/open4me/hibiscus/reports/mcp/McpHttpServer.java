package de.open4me.hibiscus.reports.mcp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

final class McpHttpServer
{
    private final int port;
    private final String bindAddress;
    private final boolean allowPrivateNetworkOrigins;
    private final String token;
    private final McpJsonRpcHandler handler;
    private HttpServer server;
    private ExecutorService executor;

    McpHttpServer(int port, String bindAddress, boolean allowPrivateNetworkOrigins, String token,
                  McpJsonRpcHandler handler)
    {
        this.port = port;
        this.bindAddress = bindAddress;
        this.allowPrivateNetworkOrigins = allowPrivateNetworkOrigins;
        this.token = token;
        this.handler = handler;
    }

    void start() throws IOException
    {
        try
        {
            server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
            executor = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "hibiscus-reports-mcp");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            server.createContext("/mcp", this::handle);
            server.start();
        }
        catch (BindException e)
        {
            stop();
            throw portInUse(e);
        }
        catch (IOException | RuntimeException e)
        {
            stop();
            throw e;
        }
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
            if (!originAllowed(exchange.getRequestHeaders().get("Origin"), allowPrivateNetworkOrigins))
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

    static boolean originAllowed(List<String> origins, boolean allowPrivateNetworkOrigins)
    {
        if (origins == null || origins.isEmpty())
            return true;
        for (String origin : origins)
        {
            if (origin == null || origin.isBlank())
                continue;
            if (!originAllowed(origin, allowPrivateNetworkOrigins))
                return false;
        }
        return true;
    }

    private static boolean originAllowed(String origin, boolean allowPrivateNetworkOrigins)
    {
        String host;
        try
        {
            host = URI.create(origin).getHost();
        }
        catch (IllegalArgumentException e)
        {
            return false;
        }
        if (host == null || host.isBlank())
            return false;
        if (isLoopbackHost(host))
            return true;
        return allowPrivateNetworkOrigins && isPrivateIpv4(host);
    }

    private static boolean isLoopbackHost(String host)
    {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    private static boolean isPrivateIpv4(String host)
    {
        String[] parts = host.split("\\.");
        if (parts.length != 4)
            return false;
        int[] octets = new int[4];
        for (int i = 0; i < parts.length; i++)
        {
            try
            {
                octets[i] = Integer.parseInt(parts[i]);
            }
            catch (NumberFormatException e)
            {
                return false;
            }
            if (octets[i] < 0 || octets[i] > 255)
                return false;
        }
        return octets[0] == 10
            || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
            || (octets[0] == 192 && octets[1] == 168);
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

    private IOException portInUse(BindException cause)
    {
        String hint = "0.0.0.0".equals(bindAddress)
            ? " Bei aktiviertem LAN-Zugriff blockiert auch ein Dienst auf 127.0.0.1:" + port + " diesen Port."
            : "";
        return new IOException("MCP-Port " + port + " ist auf " + bindAddress + " bereits belegt." + hint
            + " Bitte einen anderen Port waehlen oder Hibiscus neu starten.", cause);
    }
}
