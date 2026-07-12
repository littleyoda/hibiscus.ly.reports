package de.open4me.hibiscus.reports.mcp;

import java.io.IOException;

import de.open4me.hibiscus.reports.data.HibiscusReportAccountProvider;
import de.open4me.hibiscus.reports.data.HibiscusReportTransactionProvider;
import de.open4me.hibiscus.reports.data.ReportTemplateContextFactory;
import de.willuhn.logging.Logger;

public final class McpServerManager
{
    private static final McpServerManager INSTANCE = new McpServerManager();

    private McpHttpServer server;

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
        if (server != null)
        {
            server.stop();
            server = null;
        }
    }

    public synchronized boolean isRunning()
    {
        return server != null && server.isRunning();
    }

    private void start() throws IOException
    {
        String token = McpSettings.ensureToken();
        HibiscusReportTransactionProvider transactions = new HibiscusReportTransactionProvider();
        ReportTemplateContextFactory contextFactory = new ReportTemplateContextFactory(
            new HibiscusReportAccountProvider(transactions), transactions);
        server = new McpHttpServer(McpSettings.getPort(), token, new McpJsonRpcHandler(contextFactory, transactions));
        server.start();
        Logger.info("reports MCP server started at " + McpSettings.endpoint());
    }
}
