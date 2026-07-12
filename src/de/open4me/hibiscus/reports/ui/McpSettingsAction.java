package de.open4me.hibiscus.reports.ui;

import de.open4me.hibiscus.reports.mcp.McpServerManager;
import de.open4me.hibiscus.reports.mcp.McpSettings;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.util.ApplicationException;

public final class McpSettingsAction implements Action
{
    @Override
    public void handleAction(Object context) throws ApplicationException
    {
        try
        {
            McpSettingsDialog.Result result = new McpSettingsDialog().open();
            if (result == null)
                return;

            McpSettings.setEnabled(result.enabled());
            McpSettings.setWriteEnabled(result.writeEnabled());
            McpSettings.setPort(result.port());
            if (result.regenerateToken())
                McpSettings.regenerateToken();
            else
                McpSettings.ensureToken();
            McpServerManager.get().restart();

            String message = McpSettings.isEnabled()
                ? "MCP-Server aktiviert: " + McpSettings.endpoint()
                : "MCP-Server deaktiviert.";
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(message,
                StatusBarMessage.TYPE_SUCCESS));
        }
        catch (Exception e)
        {
            GUI.getStatusBar().setErrorText("MCP-Einstellungen konnten nicht gespeichert werden: "
                + e.getMessage());
            throw new ApplicationException("MCP-Einstellungen konnten nicht gespeichert werden: "
                + e.getMessage(), e);
        }
    }
}
