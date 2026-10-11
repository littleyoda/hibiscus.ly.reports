package de.open4me.hibiscus.reports.mcp;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import de.willuhn.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** A single-user OAuth authorization server for the public MCP endpoint. */
final class McpOAuthService
{
    static final String USERNAME = "hibiscus-mcp";
    static final String SCOPE = "mcp:access";
    private static final long ACCESS_LIFETIME = 15 * 60;
    private static final long REFRESH_LIFETIME = 30L * 24 * 60 * 60;
    private static final long REQUEST_LIFETIME = 10 * 60;
    private static final int MAX_CLIENTS = 100;
    private static final ObjectMapper JSON = new ObjectMapper();

    record Client(String id, String name, List<String> redirectUris) {}
    record Credential(String clientId, String resource, String family, long expiresAt) {}
    record Spent(String family, long expiresAt) {}
    record Authorization(String clientId, String clientName, String redirectUri, String state,
                         String challenge, String resource, long expiresAt) {}
    record Code(Authorization authorization, long expiresAt) {}
    record Tokens(String accessToken, String refreshToken, long expiresIn) {}
    record State(Map<String, Client> clients, Map<String, Credential> access,
                 Map<String, Credential> refresh, Map<String, Spent> spentRefresh) {}

    private final Supplier<String> endpoint;
    private final Supplier<String> password;
    private final Consumer<String> stateWriter;
    private final Map<String, Client> clients = new HashMap<>();
    private final Map<String, Credential> access = new HashMap<>();
    private final Map<String, Credential> refresh = new HashMap<>();
    private final Map<String, Spent> spentRefresh = new HashMap<>();
    private final Map<String, Authorization> pending = new HashMap<>();
    private final Map<String, Code> codes = new HashMap<>();
    private final ArrayDeque<Long> loginFailures = new ArrayDeque<>();
    private final ArrayDeque<Long> registrations = new ArrayDeque<>();
    private final HttpClient metadataClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    McpOAuthService(Supplier<String> endpoint, Supplier<String> password,
                    Supplier<String> stateReader, Consumer<String> stateWriter)
    {
        this.endpoint = endpoint;
        this.password = password;
        this.stateWriter = stateWriter;
        try
        {
            String saved = stateReader.get();
            if (saved != null && !saved.isBlank())
            {
                State state = JSON.readValue(saved, State.class);
                clients.putAll(state.clients());
                access.putAll(state.access());
                refresh.putAll(state.refresh());
                spentRefresh.putAll(state.spentRefresh());
            }
        }
        catch (Exception e)
        {
            Logger.error("OAuth-Daten konnten nicht geladen werden; vorhandene Sitzungen werden verworfen.", e);
            clients.clear();
            access.clear();
            refresh.clear();
            spentRefresh.clear();
        }
    }

    String resource()
    {
        return endpoint.get();
    }

    String issuer()
    {
        URI uri = URI.create(resource());
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null)
            throw new IllegalStateException("Oeffentlicher ngrok-Endpoint ist nicht verfuegbar.");
        return "https://" + uri.getAuthority();
    }

    synchronized Client register(String name, List<String> redirects)
    {
        trimAttempts(registrations, 3600);
        if (clients.size() >= MAX_CLIENTS || name == null || name.isBlank() || name.length() > 100
            || redirects == null || redirects.isEmpty() || redirects.size() > 10 || registrations.size() >= 10)
            throw new IllegalArgumentException("Ungueltige Client-Registrierung.");
        for (String redirect : redirects)
            validateRedirect(redirect);
        Client client = new Client("hmcp_c_" + OAuthTokens.random(), name, List.copyOf(redirects));
        clients.put(client.id(), client);
        registrations.addLast(now());
        save();
        return client;
    }

    synchronized String begin(String clientId, String redirectUri, String responseType,
                              String challenge, String method, String requestedResource, String scope, String state)
        throws Exception
    {
        if (!"code".equals(responseType) || !"S256".equals(method) || challenge == null
            || !challenge.matches("[A-Za-z0-9_-]{43,128}") || !resource().equals(requestedResource)
            || !supportedScope(scope))
            throw new IllegalArgumentException("Ungueltige OAuth-Autorisierungsanfrage.");
        Client client = client(clientId);
        if (!client.redirectUris().contains(redirectUri))
            throw new IllegalArgumentException("Redirect-URI ist nicht registriert.");
        cleanup();
        if (pending.size() >= 1000)
            throw new IllegalArgumentException("Zu viele offene Autorisierungsanfragen.");
        String requestId = OAuthTokens.random();
        pending.put(OAuthTokens.hash(requestId), new Authorization(clientId, client.name(), redirectUri,
            state == null ? "" : state, challenge, requestedResource, now() + REQUEST_LIFETIME));
        return requestId;
    }

    synchronized Authorization pending(String requestId)
    {
        Authorization request = pending.get(OAuthTokens.hash(requestId));
        return request != null && request.expiresAt() > now() ? request : null;
    }

    synchronized String approve(String requestId, String username, String suppliedPassword)
    {
        trimAttempts(loginFailures, 600);
        if (loginFailures.size() >= 10)
            throw new IllegalArgumentException("Zu viele Anmeldeversuche. Bitte spaeter erneut versuchen.");
        Authorization request = pending.remove(OAuthTokens.hash(requestId));
        if (request == null || request.expiresAt() <= now())
            throw new IllegalArgumentException("Anmeldung fehlgeschlagen oder Anfrage abgelaufen.");
        if (!USERNAME.equals(username) || suppliedPassword == null
            || !OAuthTokens.equals(password.get(), suppliedPassword))
        {
            loginFailures.addLast(now());
            throw new IllegalArgumentException("Anmeldung fehlgeschlagen oder Anfrage abgelaufen.");
        }
        loginFailures.clear();
        String code = "hmcp_code_" + OAuthTokens.random();
        codes.put(OAuthTokens.hash(code), new Code(request, now() + 60));
        return code;
    }

    synchronized Tokens exchangeCode(String code, String clientId, String redirectUri,
                                     String verifier, String requestedResource)
    {
        Code saved = codes.remove(OAuthTokens.hash(code));
        if (saved == null || saved.expiresAt() <= now())
            throw new IllegalArgumentException("Autorisierungscode ungueltig.");
        Authorization request = saved.authorization();
        if (!request.clientId().equals(clientId) || !request.redirectUri().equals(redirectUri)
            || !request.resource().equals(requestedResource) || verifier == null
            || !verifier.matches("[A-Za-z0-9._~-]{43,128}")
            || !OAuthTokens.equals(request.challenge(), OAuthTokens.hash(verifier)))
            throw new IllegalArgumentException("Autorisierungscode ungueltig.");
        return issue(clientId, requestedResource, OAuthTokens.random());
    }

    synchronized Tokens refresh(String oldToken, String clientId, String requestedResource)
    {
        String hash = OAuthTokens.hash(oldToken);
        Credential old = refresh.remove(hash);
        if (old == null)
        {
            Spent spent = spentRefresh.get(hash);
            if (spent != null && spent.expiresAt() > now())
                revokeFamily(spent.family());
            throw new IllegalArgumentException("Refresh-Token ungueltig.");
        }
        if (old.expiresAt() <= now() || !old.clientId().equals(clientId)
            || !old.resource().equals(requestedResource) || !resource().equals(requestedResource))
        {
            save();
            throw new IllegalArgumentException("Refresh-Token ungueltig.");
        }
        spentRefresh.put(hash, new Spent(old.family(), old.expiresAt()));
        return issue(clientId, requestedResource, old.family());
    }

    synchronized boolean validAccess(String token)
    {
        Credential saved = access.get(OAuthTokens.hash(token));
        return saved != null && saved.expiresAt() > now() && saved.resource().equals(resource());
    }

    synchronized void revokeAll()
    {
        clients.clear();
        access.clear();
        refresh.clear();
        spentRefresh.clear();
        pending.clear();
        codes.clear();
        save();
    }

    private Tokens issue(String clientId, String requestedResource, String family)
    {
        String accessToken = "hmcp_a_" + OAuthTokens.random();
        String refreshToken = "hmcp_r_" + OAuthTokens.random();
        access.put(OAuthTokens.hash(accessToken),
            new Credential(clientId, requestedResource, family, now() + ACCESS_LIFETIME));
        refresh.put(OAuthTokens.hash(refreshToken),
            new Credential(clientId, requestedResource, family, now() + REFRESH_LIFETIME));
        cleanup();
        save();
        return new Tokens(accessToken, refreshToken, ACCESS_LIFETIME);
    }

    private Client client(String clientId) throws Exception
    {
        if (clientId != null && clientId.startsWith("https://"))
            return metadataClient(clientId);
        Client client = clients.get(clientId);
        if (client == null)
            throw new IllegalArgumentException("Client ist nicht registriert.");
        return client;
    }

    private Client metadataClient(String clientId) throws Exception
    {
        URI uri = URI.create(clientId);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawPath().isBlank()
            || "/".equals(uri.getRawPath()) || uri.getRawUserInfo() != null || uri.getFragment() != null
            || uri.getRawQuery() != null || uri.getPort() != -1)
            throw new IllegalArgumentException("Ungueltige Client-ID-Metadaten-URL.");
        for (InetAddress address : InetAddress.getAllByName(uri.getHost()))
        {
            if (!publicAddress(address))
                throw new IllegalArgumentException("Interne Client-ID-Metadaten-URL ist nicht erlaubt.");
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<InputStream> response = metadataClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body())
        {
            if (response.statusCode() != 200)
                throw new IllegalArgumentException("Client-ID-Metadaten nicht erreichbar.");
            byte[] body = input.readNBytes(65_537);
            if (body.length > 65_536)
                throw new IllegalArgumentException("Client-ID-Metadaten zu gross.");
            JsonNode metadata = JSON.readTree(body);
            if (metadata == null || !clientId.equals(metadata.path("client_id").asText()))
                throw new IllegalArgumentException("Client-ID-Metadaten stimmen nicht ueberein.");
            String name = metadata.path("client_name").asText("");
            if (name.isBlank() || name.length() > 100 || !metadata.path("redirect_uris").isArray())
                throw new IllegalArgumentException("Ungueltige Client-ID-Metadaten.");
            List<String> redirects = new ArrayList<>();
            for (JsonNode value : metadata.path("redirect_uris"))
            {
                validateRedirect(value.asText());
                redirects.add(value.asText());
            }
            if (redirects.isEmpty() || redirects.size() > 10)
                throw new IllegalArgumentException("Ungueltige Client-ID-Metadaten.");
            return new Client(clientId, name, redirects);
        }
    }

    static void validateRedirect(String redirect)
    {
        if (redirect == null)
            throw new IllegalArgumentException("Ungueltige Redirect-URI.");
        URI uri = URI.create(redirect);
        String host = uri.getHost();
        if (host == null || uri.getRawUserInfo() != null || uri.getFragment() != null
            || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme())
                && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host)))))
            throw new IllegalArgumentException("Ungueltige Redirect-URI.");
    }

    static boolean publicAddress(InetAddress address)
    {
        byte[] bytes = address.getAddress();
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
            || address.isSiteLocalAddress() || address.isMulticastAddress())
            return false;
        if (bytes.length == 16)
            return (bytes[0] & 0xfe) != 0xfc;
        int a = bytes[0] & 0xff;
        int b = bytes[1] & 0xff;
        int c = bytes[2] & 0xff;
        return a != 0 && a < 224 && !(a == 100 && b >= 64 && b <= 127)
            && !(a == 192 && b == 0 && c == 0)
            && !(a == 192 && b == 0 && c == 2)
            && !(a == 198 && (b == 18 || b == 19))
            && !(a == 198 && b == 51 && c == 100)
            && !(a == 203 && b == 0 && c == 113);
    }

    private static boolean supportedScope(String scope)
    {
        if (scope == null || scope.isBlank())
            return true;
        Set<String> requested = Set.of(scope.trim().split("\\s+"));
        return requested.contains(SCOPE) && requested.stream()
            .allMatch(value -> SCOPE.equals(value) || "offline_access".equals(value));
    }

    private void revokeFamily(String family)
    {
        access.entrySet().removeIf(entry -> family.equals(entry.getValue().family()));
        refresh.entrySet().removeIf(entry -> family.equals(entry.getValue().family()));
        save();
    }

    private void cleanup()
    {
        long now = now();
        access.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        refresh.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        spentRefresh.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        pending.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        codes.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private void save()
    {
        try
        {
            stateWriter.accept(JSON.writeValueAsString(new State(clients, access, refresh, spentRefresh)));
        }
        catch (Exception e)
        {
            throw new IllegalStateException("OAuth-Daten konnten nicht gespeichert werden.", e);
        }
    }

    private static long now()
    {
        return System.currentTimeMillis() / 1000;
    }

    private static void trimAttempts(ArrayDeque<Long> attempts, long windowSeconds)
    {
        while (!attempts.isEmpty() && attempts.peekFirst() <= now() - windowSeconds)
            attempts.removeFirst();
    }
}
