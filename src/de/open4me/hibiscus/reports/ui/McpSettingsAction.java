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

            boolean previousEnabled = McpSettings.isEnabled();
            boolean previousWriteEnabled = McpSettings.isWriteEnabled();
            boolean previousLocalNetworkEnabled = McpSettings.isLocalNetworkEnabled();
            int previousPort = McpSettings.getPort();
            boolean wasRunning = McpServerManager.get().isRunning();

            McpSettings.setEnabled(result.enabled());
            McpSettings.setWriteEnabled(result.writeEnabled());
            McpSettings.setLocalNetworkEnabled(result.localNetworkEnabled());
            McpSettings.setPort(result.port());
            if (result.regenerateToken())
                McpSettings.regenerateToken();
            else
                McpSettings.ensureToken();
            try
            {
                if (restartRequired(wasRunning, previousEnabled, previousLocalNetworkEnabled, result.enabled(),
                    result.localNetworkEnabled()))
                    McpServerManager.get().restart();
            }
            catch (Exception e)
            {
                restorePreviousSettings(previousEnabled, previousWriteEnabled, previousLocalNetworkEnabled,
                    previousPort, e);
                throw e;
            }

            String message = successMessage(wasRunning, previousEnabled, previousLocalNetworkEnabled, result);
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

    static boolean restartRequired(boolean wasRunning, boolean previousEnabled, boolean previousLocalNetworkEnabled,
                                   boolean enabled, boolean localNetworkEnabled)
    {
        return !deferBindingChange(wasRunning, previousEnabled, previousLocalNetworkEnabled, enabled,
            localNetworkEnabled);
    }

    private static boolean deferBindingChange(boolean wasRunning, boolean previousEnabled,
                                             boolean previousLocalNetworkEnabled, boolean enabled,
                                             boolean localNetworkEnabled)
    {
        return wasRunning && previousEnabled && enabled && previousLocalNetworkEnabled != localNetworkEnabled;
    }

    private static String successMessage(boolean wasRunning, boolean previousEnabled,
                                         boolean previousLocalNetworkEnabled, McpSettingsDialog.Result result)
    {
        if (deferBindingChange(wasRunning, previousEnabled, previousLocalNetworkEnabled, result.enabled(),
            result.localNetworkEnabled()))
            return result.regenerateToken()
                ? "MCP-Einstellungen gespeichert. Netzwerk-Bindung, Port und neuer Token werden erst nach einem Neustart von Hibiscus aktiv."
                : "MCP-Einstellungen gespeichert. Netzwerk-Bindung und Port werden erst nach einem Neustart von Hibiscus aktiv.";
        return McpSettings.isEnabled()
            ? "MCP-Server aktiviert: " + McpSettings.endpoint()
            : "MCP-Server deaktiviert.";
    }

    private void restorePreviousSettings(boolean enabled, boolean writeEnabled, boolean localNetworkEnabled,
                                         int port, Exception original)
    {
        McpSettings.setEnabled(enabled);
        McpSettings.setWriteEnabled(writeEnabled);
        McpSettings.setLocalNetworkEnabled(localNetworkEnabled);
        McpSettings.setPort(port);
        try
        {
            McpServerManager.get().restart();
        }
        catch (Exception restoreError)
        {
            original.addSuppressed(restoreError);
        }
    }
}
