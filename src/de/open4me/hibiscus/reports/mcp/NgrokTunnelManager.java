package de.open4me.hibiscus.reports.mcp;

import de.willuhn.logging.Logger;

final class NgrokTunnelManager
{
    private final String ingressSecret = OAuthTokens.random();
    private long generation;
    private NgrokTunnel tunnel;
    private Thread closing;
    private String endpoint = "";
    private String status = "deaktiviert";

    synchronized void start(int port)
    {
        stop();
        String authtoken;
        try
        {
            authtoken = McpSettings.getNgrokAuthtoken();
        }
        catch (RuntimeException e)
        {
            status = "Fehler: ngrok Authtoken konnte nicht gelesen werden";
            Logger.error("unable to read reports ngrok authtoken", e);
            return;
        }
        if (authtoken.isBlank())
            return;
        long request = ++generation;
        status = "verbinde...";
        Thread previousClose = closing;
        Thread worker = new Thread(() -> connect(request, authtoken, port, previousClose), "hibiscus-reports-ngrok");
        worker.setDaemon(true);
        worker.start();
    }

    synchronized void stop()
    {
        ++generation;
        endpoint = "";
        status = "deaktiviert";
        NgrokTunnel old = tunnel;
        tunnel = null;
        if (old != null)
        {
            closing = new Thread(() -> close(old), "hibiscus-reports-ngrok-close");
            closing.setDaemon(true);
            closing.start();
        }
    }

    synchronized String endpoint()
    {
        return endpoint;
    }

    synchronized String status()
    {
        return status;
    }

    String ingressSecret()
    {
        return ingressSecret;
    }

    private void connect(long request, String authtoken, int port, Thread previousClose)
    {
        NgrokTunnel opened = null;
        try
        {
            if (previousClose != null)
                previousClose.join();
            synchronized (this)
            {
                if (request != generation)
                    return;
            }
            opened = NgrokTunnel.open(authtoken, port, ingressSecret);
            synchronized (this)
            {
                if (request == generation)
                {
                    tunnel = opened;
                    endpoint = opened.endpoint();
                    status = "verbunden";
                    Logger.info("reports MCP ngrok tunnel started at " + endpoint);
                    opened = null;
                }
            }
        }
        catch (Exception | LinkageError e)
        {
            synchronized (this)
            {
                if (request == generation)
                    status = "Fehler: " + safeMessage(e, authtoken);
            }
            Logger.error("unable to start reports ngrok tunnel", e);
        }
        finally
        {
            if (opened != null)
            {
                try
                {
                    opened.close();
                }
                catch (Exception e)
                {
                    Logger.error("unable to close superseded ngrok tunnel", e);
                }
            }
        }
    }

    private static String safeMessage(Throwable error, String authtoken)
    {
        String message = error.getMessage();
        if (message == null || message.isBlank())
            message = error.getClass().getSimpleName();
        return message.replace(authtoken, "[Token]");
    }

    private static void close(NgrokTunnel tunnel)
    {
        try
        {
            tunnel.close();
        }
        catch (Exception e)
        {
            Logger.error("unable to close reports ngrok tunnel", e);
        }
    }
}
