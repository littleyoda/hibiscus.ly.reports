package de.open4me.hibiscus.reports.mcp;

import java.util.List;
import java.net.InetAddress;

public final class McpOAuthServiceTests
{
    private static final String RESOURCE = "https://example.ngrok.app/mcp";
    private static final String REDIRECT = "http://127.0.0.1:49152/callback";
    private static final String VERIFIER = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~";

    private McpOAuthServiceTests()
    {
    }

    public static void run()
    {
        authorizationCodeAndRefreshSurviveRestart();
        rejectsInvalidRequests();
        localAndTunnelAuthentication();
        clientMetadataAddressPolicy();
    }

    private static void authorizationCodeAndRefreshSurviveRestart()
    {
        String[] saved = { "" };
        McpOAuthService oauth = service(saved);
        McpOAuthService.Client client = oauth.register("Test Client", List.of(REDIRECT));
        String requestId;
        try
        {
            requestId = oauth.begin(client.id(), REDIRECT, "code", OAuthTokens.hash(VERIFIER),
                "S256", RESOURCE, McpOAuthService.SCOPE, "client-state");
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        check(oauth.pending(requestId) != null, "authorization request exists");
        String code = oauth.approve(requestId, McpOAuthService.USERNAME, "test-password");
        McpOAuthService.Tokens tokens = oauth.exchangeCode(code, client.id(), REDIRECT, VERIFIER, RESOURCE);
        check(oauth.validAccess(tokens.accessToken()), "access token accepted");
        McpOAuthService restarted = service(saved);
        check(restarted.validAccess(tokens.accessToken()), "access token survives restart");
        McpOAuthService.Tokens refreshed = restarted.refresh(tokens.refreshToken(), client.id(), RESOURCE);
        check(restarted.validAccess(refreshed.accessToken()), "refreshed access token accepted");
        fails(() -> restarted.refresh(tokens.refreshToken(), client.id(), RESOURCE), "refresh replay rejected");
        check(!restarted.validAccess(refreshed.accessToken()), "refresh replay revokes token family");
        restarted.revokeAll();
        check(!service(saved).validAccess(tokens.accessToken()), "revocation survives restart");
    }

    private static void rejectsInvalidRequests()
    {
        String[] saved = { "" };
        McpOAuthService oauth = service(saved);
        fails(() -> oauth.register("Bad", List.of("http://example.com/callback")),
            "non-local HTTP redirect rejected");
        McpOAuthService.Client client = oauth.register("Test", List.of(REDIRECT));
        try
        {
            fails(() -> {
                try
                {
                    oauth.begin(client.id(), REDIRECT, "code", OAuthTokens.hash(VERIFIER),
                        "plain", RESOURCE, McpOAuthService.SCOPE, "state");
                }
                catch (Exception e)
                {
                    throw new IllegalArgumentException(e);
                }
            }, "plain PKCE rejected");
            String requestId = oauth.begin(client.id(), REDIRECT, "code", OAuthTokens.hash(VERIFIER),
                "S256", RESOURCE, "offline_access " + McpOAuthService.SCOPE, "state");
            check(oauth.pending(requestId) != null, "scope order accepted");
            fails(() -> oauth.approve(requestId, McpOAuthService.USERNAME, "wrong"),
                "wrong password rejected");
            fails(() -> {
                try
                {
                    oauth.begin(client.id(), REDIRECT, "code", OAuthTokens.hash(VERIFIER),
                        "S256", RESOURCE, "admin", "state");
                }
                catch (Exception e)
                {
                    throw new IllegalArgumentException(e);
                }
            }, "unsupported scope rejected");
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    private static void localAndTunnelAuthentication()
    {
        String[] saved = { "" };
        McpOAuthService oauth = service(saved);
        McpOAuthService.Client client = oauth.register("Test", List.of(REDIRECT));
        try
        {
            String requestId = oauth.begin(client.id(), REDIRECT, "code", OAuthTokens.hash(VERIFIER),
                "S256", RESOURCE, McpOAuthService.SCOPE, "state");
            String code = oauth.approve(requestId, McpOAuthService.USERNAME, "test-password");
            String access = oauth.exchangeCode(code, client.id(), REDIRECT, VERIFIER, RESOURCE).accessToken();
            McpHttpServer server = new McpHttpServer(0, "127.0.0.1", false, "static-token", null,
                oauth, "tunnel-secret");
            check(server.authenticated(List.of("Bearer static-token"), false), "static token works locally");
            check(server.authenticated(List.of("Bearer static-token"), true), "static token works via tunnel");
            check(!server.authenticated(List.of("Bearer " + access), false), "OAuth token rejected locally");
            check(server.authenticated(List.of("Bearer " + access), true), "OAuth token works via tunnel");
            check(!server.authenticated(List.of("Bearer static-token", "Bearer " + access), true),
                "ambiguous authorization rejected");
            check(!McpHttpServer.tunneled(List.of("invalid"), "tunnel-secret"), "spoofed tunnel rejected");
            check(!McpHttpServer.tunneled(List.of("tunnel-secret", "extra"), "tunnel-secret"),
                "duplicate tunnel header rejected");
            check(McpHttpServer.tunneled(List.of("tunnel-secret"), "tunnel-secret"),
                "tunnel marker accepted");
            server.setToken("new-password");
            oauth.revokeAll();
            check(!server.authenticated(List.of("Bearer static-token"), false),
                "previous Bearer password rejected after rotation");
            check(server.authenticated(List.of("Bearer new-password"), false),
                "new Bearer password works without server restart");
            check(!server.authenticated(List.of("Bearer " + access), true),
                "OAuth access revoked with password rotation");
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    private static void clientMetadataAddressPolicy()
    {
        try
        {
            check(!McpOAuthService.publicAddress(InetAddress.getByName("127.0.0.1")), "loopback blocked");
            check(!McpOAuthService.publicAddress(InetAddress.getByName("100.64.0.1")), "shared range blocked");
            check(!McpOAuthService.publicAddress(InetAddress.getByName("198.18.0.1")), "benchmark range blocked");
            check(McpOAuthService.publicAddress(InetAddress.getByName("8.8.8.8")), "public IP accepted");
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    private static McpOAuthService service(String[] saved)
    {
        return new McpOAuthService(() -> RESOURCE, () -> "test-password", () -> saved[0], value -> saved[0] = value);
    }

    private static void fails(Runnable action, String message)
    {
        try
        {
            action.run();
            throw new AssertionError(message);
        }
        catch (IllegalArgumentException expected)
        {
        }
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
