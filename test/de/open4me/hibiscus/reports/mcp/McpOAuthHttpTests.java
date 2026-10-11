package de.open4me.hibiscus.reports.mcp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;

public final class McpOAuthHttpTests
{
    private McpOAuthHttpTests()
    {
    }

    public static void run()
    {
        String[] saved = { "" };
        McpOAuthService oauth = new McpOAuthService(() -> "https://example.ngrok.app/mcp",
            () -> "password", () -> saved[0], value -> saved[0] = value);
        McpOAuthHttp http = new McpOAuthHttp(oauth);
        Exchange resource = new Exchange("GET", "/.well-known/oauth-protected-resource/mcp", "");
        invoke(http, resource);
        check(resource.status == 200, "resource metadata status");
        check(resource.body().contains("https://example.ngrok.app/mcp"), "resource metadata audience");

        Exchange server = new Exchange("GET", "/.well-known/oauth-authorization-server", "");
        invoke(http, server);
        check(server.status == 200, "authorization metadata status");
        check(server.body().contains("client_id_metadata_document_supported"), "CIMD discovery");
        check(server.body().contains("code_challenge_methods_supported"), "PKCE discovery");

        Exchange register = new Exchange("POST", "/register",
            "{\"client_name\":\"Client\",\"redirect_uris\":[\"http://127.0.0.1:1234/callback\"]}");
        register.requestHeaders.set("Content-Type", "application/json");
        invoke(http, register);
        check(register.status == 201 && register.body().contains("client_id"), "client registration");
        authorizationFlow(http, oauth, register.body());

        McpHttpServer transport = new McpHttpServer(0, "127.0.0.1", false,
            "fixed-token", null, oauth, "private-ingress-marker");
        Exchange direct = new Exchange("GET", "/.well-known/oauth-protected-resource/mcp", "");
        invokeTransport(transport, "handleOAuth", direct);
        check(direct.status == 404, "OAuth discovery unavailable directly");
        Exchange publicDiscovery = new Exchange("GET", "/.well-known/oauth-protected-resource/mcp", "");
        publicDiscovery.requestHeaders.set(McpHttpServer.TUNNEL_HEADER, "private-ingress-marker");
        invokeTransport(transport, "handleOAuth", publicDiscovery);
        check(publicDiscovery.status == 200, "OAuth discovery available through tunnel");

        Exchange localMcp = new Exchange("POST", "/mcp", "{}");
        invokeTransport(transport, "handle", localMcp);
        check(localMcp.status == 401 && "Bearer".equals(localMcp.responseHeaders.getFirst("WWW-Authenticate")),
            "direct request only advertises fixed token");
        check(lastLog().httpStatus() == 401 && "Lokal".equals(lastLog().source()),
            "rejected local request is logged");
        Exchange publicMcp = new Exchange("POST", "/mcp", "{}");
        publicMcp.requestHeaders.set(McpHttpServer.TUNNEL_HEADER, "private-ingress-marker");
        invokeTransport(transport, "handle", publicMcp);
        check(publicMcp.status == 401
            && publicMcp.responseHeaders.getFirst("WWW-Authenticate").contains("resource_metadata="),
            "public request advertises OAuth");
        check(lastLog().httpStatus() == 401 && "ngrok".equals(lastLog().source()),
            "rejected public request is logged");

        McpHttpServer active = new McpHttpServer(0, "127.0.0.1", false,
            "fixed-token", new McpJsonRpcHandler(null, null, null), oauth, "private-ingress-marker");
        Exchange initialize = new Exchange("POST", "/mcp",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        initialize.requestHeaders.set("Authorization", "Bearer fixed-token");
        invokeTransport(active, "handle", initialize);
        check(initialize.status == 200 && "initialize".equals(lastLog().operation()),
            "successful MCP request is logged");
        check(!lastLog().operation().contains("fixed-token"), "credential is not logged");

        Exchange malformed = new Exchange("POST", "/mcp", "not-json");
        malformed.requestHeaders.set("Authorization", "Bearer fixed-token");
        invokeTransport(active, "handle", malformed);
        check(malformed.status == 200 && "RPC -32700".equals(lastLog().rpcError()),
            "JSON-RPC parse error is logged");

        Exchange forbidden = new Exchange("POST", "/mcp", "{}");
        forbidden.requestHeaders.set("Origin", "https://example.org");
        invokeTransport(active, "handle", forbidden);
        check(forbidden.status == 403 && lastLog().httpStatus() == 403,
            "forbidden origin is logged");

        Exchange wrongMethod = new Exchange("GET", "/mcp", "");
        invokeTransport(active, "handle", wrongMethod);
        check(wrongMethod.status == 405 && lastLog().httpStatus() == 405,
            "unsupported HTTP method is logged");

        Exchange wrongPath = new Exchange("POST", "/mcp/other", "");
        invokeTransport(active, "handle", wrongPath);
        check(wrongPath.status == 404 && lastLog().httpStatus() == 404,
            "unknown MCP path is logged");

        Exchange tooLarge = new Exchange("POST", "/mcp", "x".repeat(1_048_577));
        tooLarge.requestHeaders.set("Authorization", "Bearer fixed-token");
        invokeTransport(active, "handle", tooLarge);
        check(tooLarge.status == 413 && lastLog().httpStatus() == 413,
            "oversized MCP request is logged without body");
    }

    private static McpRequestLog.Entry lastLog()
    {
        var entries = McpRequestLog.get().snapshot();
        return entries.get(entries.size() - 1);
    }

    private static void authorizationFlow(McpOAuthHttp http, McpOAuthService oauth, String registration)
    {
        try
        {
            String clientId = new ObjectMapper().readTree(registration).path("client_id").asText();
            String verifier = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~";
            String redirect = "http://127.0.0.1:1234/callback";
            String resource = "https://example.ngrok.app/mcp";
            String query = "client_id=" + encode(clientId) + "&redirect_uri=" + encode(redirect)
                + "&response_type=code&code_challenge=" + encode(OAuthTokens.hash(verifier))
                + "&code_challenge_method=S256&resource=" + encode(resource) + "&state=client-state";
            Exchange login = new Exchange("GET", "/authorize?" + query, "");
            invoke(http, login);
            check(login.status == 200, "login page status");
            check(login.body().contains("action=\"https://example.ngrok.app/authorize\""),
                "form targets public HTTPS issuer");
            check(login.body().contains("Konten synchronisieren"),
                "consent explains account synchronization");
            check(login.body().contains("Überweisungsentwürfe anlegen")
                && login.body().contains("jetzt oder später aktiviert"),
                "consent explains current and future transfer draft access");
            check(login.body().contains("nicht an die Bank gesendet"),
                "consent distinguishes drafts from bank submissions");
            check(login.body().contains("zusätzliche MCP-Tools"),
                "consent mentions tools supplied by other plugins");
            check(!login.responseHeaders.getFirst("Content-Security-Policy").contains("form-action"),
                "CSP does not block OAuth callback redirects");
            String requestId = login.body().split("name=\"request_id\" value=\"")[1].split("\"")[0];
            Exchange approval = new Exchange("POST", "/authorize", "request_id=" + encode(requestId)
                + "&username=hibiscus-mcp&password=password");
            approval.requestHeaders.set("Content-Type", "application/x-www-form-urlencoded");
            invoke(http, approval);
            check(approval.status == 302, "authorization redirect");
            String location = approval.responseHeaders.getFirst("Location");
            check(location.contains("state=client-state") && location.contains("iss="),
                "state and issuer on authorization response");
            String code = URLDecoder.decode(location.split("code=")[1].split("&")[0], StandardCharsets.UTF_8);
            Exchange token = new Exchange("POST", "/token", "grant_type=authorization_code&code=" + encode(code)
                + "&client_id=" + encode(clientId) + "&redirect_uri=" + encode(redirect)
                + "&code_verifier=" + encode(verifier) + "&resource=" + encode(resource));
            token.requestHeaders.set("Content-Type", "application/x-www-form-urlencoded");
            invoke(http, token);
            check(token.status == 200, "token exchange");
            String access = new ObjectMapper().readTree(token.body()).path("access_token").asText();
            check(oauth.validAccess(access), "HTTP-issued access token accepted");
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void invoke(McpOAuthHttp http, Exchange exchange)
    {
        try
        {
            http.handle(exchange);
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    private static void invokeTransport(McpHttpServer server, String method, Exchange exchange)
    {
        try
        {
            var handler = McpHttpServer.class.getDeclaredMethod(method, HttpExchange.class);
            handler.setAccessible(true);
            handler.invoke(server, exchange);
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }

    private static final class Exchange extends HttpExchange
    {
        private final String method;
        private final URI uri;
        private final InputStream input;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private int status;

        Exchange(String method, String path, String body)
        {
            this.method = method;
            this.uri = URI.create(path);
            this.input = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }

        String body()
        {
            return output.toString(StandardCharsets.UTF_8);
        }

        @Override public Headers getRequestHeaders() { return requestHeaders; }
        @Override public Headers getResponseHeaders() { return responseHeaders; }
        @Override public URI getRequestURI() { return uri; }
        @Override public String getRequestMethod() { return method; }
        @Override public HttpContext getHttpContext() { return null; }
        @Override public void close() { }
        @Override public InputStream getRequestBody() { return input; }
        @Override public OutputStream getResponseBody() { return output; }
        @Override public void sendResponseHeaders(int code, long length) { status = code; }
        @Override public InetSocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 1234); }
        @Override public int getResponseCode() { return status; }
        @Override public InetSocketAddress getLocalAddress() { return new InetSocketAddress("127.0.0.1", 37653); }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) { }
        @Override public void setStreams(InputStream input, OutputStream output) { }
        @Override public HttpPrincipal getPrincipal() { return null; }
    }
}
