package de.open4me.hibiscus.reports.mcp;

import java.io.IOException;

import de.open4me.hibiscus.reports.data.HibiscusReportAccountProvider;
import de.open4me.hibiscus.reports.data.HibiscusReportCategoryProvider;
import de.open4me.hibiscus.reports.data.HibiscusReportTransactionProvider;
import de.open4me.hibiscus.reports.data.ReportTemplateContextFactory;
import de.willuhn.logging.Logger;

public final class McpServerManager
{
    private static final McpServerManager INSTANCE = new McpServerManager();

    private McpHttpServer server;
    private McpOAuthService oauth;
    private final NgrokTunnelManager ngrok = new NgrokTunnelManager();

    private McpServerManager()
    {
    }

    public static McpServerManager get()
    {
        return INSTANCE;
    }

    public synchronized void startIfEnabled()
    {
        if (!McpSettings.isEnabled())
            return;
        try
        {
            start();
        }
        catch (Exception e)
        {
            Logger.error("unable to start reports MCP server", e);
        }
    }

    public synchronized void restart() throws IOException
    {
        stop();
        if (McpSettings.isEnabled())
            start();
    }

    public synchronized void stop()
    {
        ngrok.stop();
        if (server != null)
        {
            server.stop();
            server = null;
        }
        oauth = null;
    }

    public synchronized boolean isRunning()
    {
        return server != null && server.isRunning();
    }

    public String publicEndpoint()
    {
        return ngrok.endpoint();
    }

    public String publicStatus()
    {
        return ngrok.status();
    }

    public synchronized void refreshPublicEndpoint()
    {
        if (isRunning())
            ngrok.start(McpSettings.getPort());
    }

    public synchronized void revokeOAuthSessions()
    {
        if (oauth != null)
            oauth.revokeAll();
    }

    public synchronized void passwordChanged(String password)
    {
        if (server != null)
            server.setToken(password);
        revokeOAuthSessions();
    }

    private void start() throws IOException
    {
        String token = McpSettings.ensureToken();
        HibiscusReportTransactionProvider transactions = new HibiscusReportTransactionProvider();
        HibiscusReportCategoryProvider categories = new HibiscusReportCategoryProvider();
        ReportTemplateContextFactory contextFactory = new ReportTemplateContextFactory(
            new HibiscusReportAccountProvider(transactions), transactions, categories);
        oauth = new McpOAuthService(ngrok::endpoint, McpSettings::ensureToken,
            McpSettings::getOAuthState, McpSettings::setOAuthState);
        server = new McpHttpServer(McpSettings.getPort(), McpSettings.bindAddress(),
            McpSettings.isLocalNetworkEnabled(), token,
            new McpJsonRpcHandler(contextFactory, transactions, categories), oauth, ngrok.ingressSecret());
        server.start();
        Logger.info("reports MCP server started at " + McpSettings.endpoint());
        ngrok.start(McpSettings.getPort());
    }
}
