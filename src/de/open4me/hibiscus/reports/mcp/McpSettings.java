package de.open4me.hibiscus.reports.mcp;

import java.security.SecureRandom;
import java.util.Base64;

import de.willuhn.jameica.security.Wallet;
import de.willuhn.jameica.security.crypto.AESEngine;
import de.willuhn.jameica.system.Settings;

public final class McpSettings
{
    public static final int DEFAULT_PORT = 37653;

    private static final String LEGACY_TOKEN = "token";
    private static final String WALLET_TOKEN = "mcp.token";
    private static final Settings SETTINGS = new Settings(McpSettings.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private McpSettings()
    {
    }

    public static boolean isEnabled()
    {
        return SETTINGS.getBoolean("enabled", false);
    }

    public static void setEnabled(boolean enabled)
    {
        SETTINGS.setAttribute("enabled", enabled);
    }

    public static boolean isWriteEnabled()
    {
        return SETTINGS.getBoolean("writeEnabled", false);
    }

    public static void setWriteEnabled(boolean enabled)
    {
        SETTINGS.setAttribute("writeEnabled", enabled);
    }

    public static int getPort()
    {
        int port = SETTINGS.getInt("port", DEFAULT_PORT);
        return validPort(port) ? port : DEFAULT_PORT;
    }

    public static void setPort(int port)
    {
        SETTINGS.setAttribute("port", validPort(port) ? port : DEFAULT_PORT);
    }

    public static String getToken()
    {
        try
        {
            String token = (String) wallet().get(WALLET_TOKEN);
            if (token != null && !token.isBlank())
                return token;

            String legacy = SETTINGS.getString(LEGACY_TOKEN, "");
            if (legacy != null && !legacy.isBlank())
            {
                setToken(legacy);
                clearLegacyToken();
                return legacy;
            }
            return "";
        }
        catch (Exception e)
        {
            throw new IllegalStateException("MCP-Token konnte nicht aus dem Jameica-Wallet gelesen werden.", e);
        }
    }

    public static String ensureToken()
    {
        String token = getToken();
        if (token == null || token.isBlank())
        {
            token = generateToken();
            setToken(token);
        }
        return token;
    }

    public static String endpoint()
    {
        return "http://127.0.0.1:" + getPort() + "/mcp";
    }

    public static String regenerateToken()
    {
        String token = generateToken();
        setToken(token);
        clearLegacyToken();
        return token;
    }

    private static void setToken(String token)
    {
        try
        {
            wallet().set(WALLET_TOKEN, token);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("MCP-Token konnte nicht im Jameica-Wallet gespeichert werden.", e);
        }
    }

    private static void clearLegacyToken()
    {
        SETTINGS.setAttribute(LEGACY_TOKEN, "");
    }

    private static Wallet wallet() throws Exception
    {
        return new Wallet(McpSettings.class, new AESEngine());
    }

    private static boolean validPort(int port)
    {
        return port > 0 && port < 65536;
    }

    private static String generateToken()
    {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
