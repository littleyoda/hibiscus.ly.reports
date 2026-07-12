package de.open4me.hibiscus.reports;

import de.open4me.hibiscus.reports.mcp.McpServerManager;
import de.willuhn.jameica.plugin.AbstractPlugin;

public class Plugin extends AbstractPlugin
{
    @Override
    public void init()
    {
        McpServerManager.get().startIfEnabled();
    }

    @Override
    public void shutDown()
    {
        McpServerManager.get().stop();
    }
}
