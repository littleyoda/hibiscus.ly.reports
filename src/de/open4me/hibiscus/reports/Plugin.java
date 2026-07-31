package de.open4me.hibiscus.reports;

import de.open4me.hibiscus.reports.automation.runtime.AutomationService;
import de.open4me.hibiscus.reports.mcp.McpServerManager;
import de.willuhn.jameica.plugin.AbstractPlugin;
import de.willuhn.util.ApplicationException;

public class Plugin extends AbstractPlugin
{
    @Override
    public void init() throws ApplicationException
    {
        AutomationService.get().start();
        McpServerManager.get().startIfEnabled();
    }

    @Override
    public void shutDown()
    {
        AutomationService.get().stop();
        McpServerManager.get().stop();
    }
}
