package de.open4me.hibiscus.reports.mcp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import de.willuhn.jameica.system.Application;

final class McpHttpServer
{
    static final String TUNNEL_HEADER = "X-Hibiscus-Mcp-Tunnel";
    private static final int MAX_REQUEST_BYTES = 1_048_576;
    private final int port;
    private final String bindAddress;
    private final boolean allowPrivateNetworkOrigins;
    private volatile String token;
    private final McpJsonRpcHandler handler;
    private final McpOAuthService oauth;
    private final String ingressSecret;
    private HttpServer server;
    private ExecutorService executor;

    McpHttpServer(int port, String bindAddress, boolean allowPrivateNetworkOrigins, String token,
                  McpJsonRpcHandler handler, McpOAuthService oauth, String ingressSecret)
    {
        this.port = port;
        this.bindAddress = bindAddress;
        this.allowPrivateNetworkOrigins = allowPrivateNetworkOrigins;
        this.token = token;
        this.handler = handler;
        this.oauth = oauth;
        this.ingressSecret = ingressSecret;
    }

    void start() throws IOException
    {
        try
        {
            server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
            ClassLoader classLoader = Application.getClassLoader();
            executor = Executors.newCachedThreadPool(runnable -> createWorker(runnable, classLoader));
            server.setExecutor(executor);
            server.createContext("/mcp", this::handle);
            server.createContext("/", this::handleOAuth);
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

    static Thread createWorker(Runnable runnable, ClassLoader classLoader)
    {
        Thread thread = new Thread(runnable, "hibiscus-reports-mcp");
        // The HTTP dispatcher uses the system loader. Sync workers must inherit Jameica's loader.
        thread.setContextClassLoader(classLoader);
        thread.setDaemon(true);
        return thread;
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

    void setToken(String token)
    {
        this.token = token;
    }

    private void handle(HttpExchange exchange) throws IOException
    {
        long started = System.nanoTime();
        String source = "Unbekannt";
        String operation = exchange.getRequestMethod() + " /mcp";
        String rpcError = "";
        int status = 500;
        try
        {
            boolean publicRequest = tunneled(exchange);
            source = requestSource(exchange, publicRequest);
            if (!"/mcp".equals(exchange.getRequestURI().getPath()))
            {
                status = 404;
                send(exchange, 404, "text/plain", "Not Found");
                return;
            }
            if (!originAllowed(exchange.getRequestHeaders().get("Origin"), allowPrivateNetworkOrigins)
                && !(publicRequest && publicOriginAllowed(exchange)))
            {
                status = 403;
                send(exchange, 403, "text/plain", "Forbidden");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()))
            {
                status = 405;
                send(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }
            if (!authenticated(exchange.getRequestHeaders().get("Authorization"), publicRequest))
            {
                String challenge = "Bearer";
                if (publicRequest && !oauth.resource().isBlank())
                    challenge += " resource_metadata=\"" + oauth.issuer()
                        + "/.well-known/oauth-protected-resource/mcp\", scope=\"" + McpOAuthService.SCOPE + "\"";
                exchange.getResponseHeaders().set("WWW-Authenticate", challenge);
                status = 401;
                send(exchange, 401, "text/plain", "Unauthorized");
                return;
            }
            byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
            if (body.length > MAX_REQUEST_BYTES)
            {
                status = 413;
                send(exchange, 413, "text/plain", "Request Too Large");
                return;
            }
            String request = new String(body, StandardCharsets.UTF_8);
            operation = McpRequestLog.operation(request);
            String response = handler.handle(request);
            rpcError = McpRequestLog.rpcError(response);
            if (response == null)
            {
                status = 202;
                send(exchange, 202, "application/json", "");
                return;
            }
            status = 200;
            send(exchange, 200, "application/json", response);
        }
        catch (IOException | RuntimeException e)
        {
            status = 500;
            throw e;
        }
        finally
        {
            try
            {
                exchange.close();
            }
            finally
            {
                McpRequestLog.get().add(source, operation, status, rpcError,
                    (System.nanoTime() - started) / 1_000_000);
            }
        }
    }

    private static String requestSource(HttpExchange exchange, boolean publicRequest)
    {
        if (publicRequest)
            return "ngrok";
        InetSocketAddress remote = exchange.getRemoteAddress();
        InetAddress address = remote == null ? null : remote.getAddress();
        if (address == null)
            return "Unbekannt";
        return address.isLoopbackAddress() ? "Lokal" : "LAN";
    }

    private void handleOAuth(HttpExchange exchange) throws IOException
    {
        if (!tunneled(exchange) || oauth.resource().isBlank() || !publicOriginAllowed(exchange))
        {
            send(exchange, 404, "text/plain", "Not Found");
            exchange.close();
            return;
        }
        new McpOAuthHttp(oauth).handle(exchange);
    }

    private boolean tunneled(HttpExchange exchange)
    {
        return tunneled(exchange.getRequestHeaders().get(TUNNEL_HEADER), ingressSecret);
    }

    static boolean tunneled(List<String> values, String ingressSecret)
    {
        return values != null && values.size() == 1 && OAuthTokens.equals(ingressSecret, values.get(0));
    }

    private boolean publicOriginAllowed(HttpExchange exchange)
    {
        List<String> origins = exchange.getRequestHeaders().get("Origin");
        return origins == null || origins.isEmpty()
            || (origins.size() == 1 && oauth.issuer().equals(origins.get(0)));
    }

    boolean authenticated(List<String> values, boolean publicRequest)
    {
        if (values == null || values.size() != 1)
            return false;
        for (String value : values)
        {
            if (value != null && value.startsWith("Bearer "))
            {
                String supplied = value.substring(7);
                if (OAuthTokens.equals(token, supplied) || (publicRequest && oauth.validAccess(supplied)))
                    return true;
            }
        }
        return false;
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
