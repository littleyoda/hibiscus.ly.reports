package de.open4me.hibiscus.reports.mcp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.sun.net.httpserver.HttpExchange;

final class McpOAuthHttp
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private final McpOAuthService oauth;

    McpOAuthHttp(McpOAuthService oauth)
    {
        this.oauth = oauth;
    }

    void handle(HttpExchange exchange) throws IOException
    {
        try
        {
            String path = exchange.getRequestURI().getPath();
            switch (path)
            {
                case "/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/mcp" ->
                    resourceMetadata(exchange);
                case "/.well-known/oauth-authorization-server" -> serverMetadata(exchange);
                case "/register" -> register(exchange);
                case "/authorize" -> authorize(exchange);
                case "/token" -> token(exchange);
                default -> send(exchange, 404, "text/plain", "Not Found");
            }
        }
        catch (IllegalArgumentException e)
        {
            error(exchange, 400, "invalid_request", e.getMessage());
        }
        catch (IllegalStateException e)
        {
            error(exchange, 503, "temporarily_unavailable", "OAuth derzeit nicht verfuegbar.");
        }
        catch (Exception e)
        {
            error(exchange, 400, "invalid_request", "OAuth-Anfrage konnte nicht verarbeitet werden.");
        }
        finally
        {
            exchange.close();
        }
    }

    private void resourceMetadata(HttpExchange exchange) throws IOException
    {
        requireMethod(exchange, "GET");
        json(exchange, 200, Map.of("resource", oauth.resource(),
            "authorization_servers", List.of(oauth.issuer()), "scopes_supported", List.of(McpOAuthService.SCOPE)));
    }

    private void serverMetadata(HttpExchange exchange) throws IOException
    {
        requireMethod(exchange, "GET");
        String issuer = oauth.issuer();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("issuer", issuer);
        metadata.put("authorization_endpoint", issuer + "/authorize");
        metadata.put("token_endpoint", issuer + "/token");
        metadata.put("registration_endpoint", issuer + "/register");
        metadata.put("response_types_supported", List.of("code"));
        metadata.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
        metadata.put("token_endpoint_auth_methods_supported", List.of("none"));
        metadata.put("code_challenge_methods_supported", List.of("S256"));
        metadata.put("client_id_metadata_document_supported", true);
        metadata.put("authorization_response_iss_parameter_supported", true);
        metadata.put("scopes_supported", List.of(McpOAuthService.SCOPE));
        json(exchange, 200, metadata);
    }

    private void register(HttpExchange exchange) throws IOException
    {
        requireMethod(exchange, "POST");
        requireContentType(exchange, "application/json");
        JsonNode request = JSON.readTree(body(exchange));
        if (request == null || !request.path("redirect_uris").isArray())
            throw new IllegalArgumentException("Redirect-URIs fehlen.");
        List<String> redirects = new ArrayList<>();
        for (JsonNode value : request.path("redirect_uris"))
            redirects.add(value.asText());
        McpOAuthService.Client client = oauth.register(request.path("client_name").asText("MCP-Client"), redirects);
        json(exchange, 201, Map.of("client_id", client.id(), "client_name", client.name(),
            "redirect_uris", client.redirectUris(), "token_endpoint_auth_method", "none",
            "grant_types", List.of("authorization_code", "refresh_token"), "response_types", List.of("code")));
    }

    private void authorize(HttpExchange exchange) throws Exception
    {
        if ("GET".equals(exchange.getRequestMethod()))
        {
            if (exchange.getRequestURI().getRawQuery() == null
                || exchange.getRequestURI().getRawQuery().length() > 8192)
                throw new IllegalArgumentException("Autorisierungsanfrage ungueltig.");
            Map<String, String> params = form(exchange.getRequestURI().getRawQuery());
            String requestId = oauth.begin(params.get("client_id"), params.get("redirect_uri"),
                params.get("response_type"), params.get("code_challenge"),
                params.get("code_challenge_method"), params.get("resource"), params.get("scope"),
                params.get("state"));
            McpOAuthService.Authorization auth = oauth.pending(requestId);
            String issuer = oauth.issuer();
            String html = "<!doctype html><html lang=\"de\"><meta charset=\"utf-8\">"
                + "<title>Hibiscus MCP Anmeldung</title><body><main><h1>Hibiscus MCP</h1>"
                + "<p>Client: " + html(auth.clientName()) + "</p><p>Weiterleitung: "
                + html(auth.redirectUri()) + "</p><p>Zugriff auf Hibiscus erlauben?</p>"
                + "<p>Dieser Client kann Konten, Umsätze und Report-Daten lesen und Konten synchronisieren.</p>"
                + "<p>Wenn das Anlegen von Überweisungsentwürfen jetzt oder später aktiviert ist, "
                + "kann der Client auch lokale SEPA-Überweisungsentwürfe anlegen. "
                + "Die Entwürfe werden nicht an die Bank gesendet.</p>"
                + "<p>Weitere installierte Plugins können zusätzliche MCP-Tools bereitstellen.</p>"
                + "<form method=\"post\" action=\"" + html(issuer) + "/authorize\">"
                + "<input type=\"hidden\" name=\"request_id\" value=\"" + html(requestId) + "\">"
                + "<label>Benutzername <input name=\"username\" value=\"hibiscus-mcp\" required></label>"
                + "<label>Passwort <input name=\"password\" type=\"password\" required></label>"
                + "<button type=\"submit\">Anmelden und Zugriff erlauben</button></form></main></body></html>";
            exchange.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'");
            send(exchange, 200, "text/html", html);
            return;
        }
        requireMethod(exchange, "POST");
        requireContentType(exchange, "application/x-www-form-urlencoded");
        Map<String, String> params = form(body(exchange));
        McpOAuthService.Authorization auth = oauth.pending(params.get("request_id"));
        if (auth == null)
            throw new IllegalArgumentException("Autorisierungsanfrage abgelaufen.");
        String code;
        try
        {
            code = oauth.approve(params.get("request_id"), params.get("username"), params.get("password"));
        }
        catch (IllegalArgumentException e)
        {
            send(exchange, 403, "text/html", "<!doctype html><html lang=\"de\"><meta charset=\"utf-8\">"
                + "<title>Anmeldung fehlgeschlagen</title><h1>Anmeldung fehlgeschlagen</h1>"
                + "<p>Bitte die Verbindung im MCP-Client erneut starten.</p></html>");
            return;
        }
        String separator = auth.redirectUri().contains("?") ? "&" : "?";
        String location = auth.redirectUri() + separator + "code=" + encode(code)
            + "&state=" + encode(auth.state()) + "&iss=" + encode(oauth.issuer());
        exchange.getResponseHeaders().set("Location", location);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(302, -1);
    }

    private void token(HttpExchange exchange) throws IOException
    {
        requireMethod(exchange, "POST");
        requireContentType(exchange, "application/x-www-form-urlencoded");
        Map<String, String> params = form(body(exchange));
        McpOAuthService.Tokens result;
        try
        {
            if ("authorization_code".equals(params.get("grant_type")))
                result = oauth.exchangeCode(params.get("code"), params.get("client_id"),
                    params.get("redirect_uri"), params.get("code_verifier"), params.get("resource"));
            else if ("refresh_token".equals(params.get("grant_type")))
                result = oauth.refresh(params.get("refresh_token"), params.get("client_id"), params.get("resource"));
            else
                throw new IllegalArgumentException("Grant-Typ wird nicht unterstuetzt.");
        }
        catch (IllegalArgumentException e)
        {
            error(exchange, 400, "invalid_grant", "Autorisierung ungueltig oder abgelaufen.");
            return;
        }
        json(exchange, 200, Map.of("access_token", result.accessToken(), "refresh_token", result.refreshToken(),
            "token_type", "Bearer", "expires_in", result.expiresIn(), "scope", McpOAuthService.SCOPE));
    }

    private static void requireMethod(HttpExchange exchange, String expected)
    {
        if (!expected.equals(exchange.getRequestMethod()))
            throw new IllegalArgumentException("HTTP-Methode wird nicht unterstuetzt.");
    }

    private static void requireContentType(HttpExchange exchange, String expected)
    {
        String actual = exchange.getRequestHeaders().getFirst("Content-Type");
        if (actual == null || !actual.toLowerCase().startsWith(expected))
            throw new IllegalArgumentException("Content-Type wird nicht unterstuetzt.");
    }

    private static String body(HttpExchange exchange) throws IOException
    {
        byte[] bytes = exchange.getRequestBody().readNBytes(65_537);
        if (bytes.length > 65_536)
            throw new IllegalArgumentException("Anfrage zu gross.");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static Map<String, String> form(String encoded)
    {
        Map<String, String> values = new HashMap<>();
        if (encoded == null || encoded.isBlank())
            return values;
        for (String part : encoded.split("&"))
        {
            String[] pair = part.split("=", 2);
            String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            if (values.containsKey(name))
                throw new IllegalArgumentException("Doppelter OAuth-Parameter.");
            values.put(name, URLDecoder.decode(pair.length == 2 ? pair[1] : "", StandardCharsets.UTF_8));
        }
        return values;
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String html(String value)
    {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static void json(HttpExchange exchange, int status, Object value) throws IOException
    {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        send(exchange, status, "application/json", JSON.writeValueAsString(value));
    }

    private static void error(HttpExchange exchange, int status, String code, String description) throws IOException
    {
        json(exchange, status, Map.of("error", code, "error_description", description));
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException
    {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody())
        {
            output.write(bytes);
        }
    }
}
