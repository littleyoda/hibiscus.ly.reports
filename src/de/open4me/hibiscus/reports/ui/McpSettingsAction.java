package de.open4me.hibiscus.reports.ui;

import de.open4me.hibiscus.reports.mcp.McpServerManager;
import de.open4me.hibiscus.reports.mcp.McpSettings;
import de.willuhn.logging.Logger;
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
            String previousNgrokAuthtoken = McpSettings.getNgrokAuthtoken();
            String previousPassword = McpSettings.ensureToken();
            boolean passwordChanged = !previousPassword.equals(result.password());
            boolean wasRunning = McpServerManager.get().isRunning();

            McpSettings.setEnabled(result.enabled());
            McpSettings.setWriteEnabled(result.writeEnabled());
            McpSettings.setLocalNetworkEnabled(result.localNetworkEnabled());
            McpSettings.setPort(result.port());
            McpSettings.setNgrokAuthtoken(result.ngrokAuthtoken());
            try
            {
                if (passwordChanged)
                    McpSettings.setPassword(result.password());
                if (restartRequired(wasRunning, previousEnabled, previousLocalNetworkEnabled, previousPort,
                    result.enabled(), result.localNetworkEnabled(), result.port()))
                    McpServerManager.get().restart();
                else if (!previousNgrokAuthtoken.equals(result.ngrokAuthtoken()))
                    McpServerManager.get().refreshPublicEndpoint();
                if (passwordChanged)
                    McpServerManager.get().passwordChanged(result.password());
            }
            catch (Exception e)
            {
                restorePreviousSettings(previousEnabled, previousWriteEnabled, previousLocalNetworkEnabled,
                    previousPort, previousNgrokAuthtoken, previousPassword, passwordChanged, e);
                throw e;
            }

            String message = successMessage(wasRunning, previousEnabled, previousLocalNetworkEnabled,
                passwordChanged, result);
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(message,
                StatusBarMessage.TYPE_SUCCESS));
        }
        catch (Exception e)
        {
            Logger.error("MCP-Einstellungen konnten nicht gespeichert werden", e);
            GUI.getStatusBar().setErrorText("MCP-Einstellungen konnten nicht gespeichert werden: "
                + e.getMessage());
            throw new ApplicationException("MCP-Einstellungen konnten nicht gespeichert werden: "
                + e.getMessage(), e);
        }
    }

    static boolean restartRequired(boolean wasRunning, boolean previousEnabled, boolean previousLocalNetworkEnabled,
                                   int previousPort, boolean enabled, boolean localNetworkEnabled, int port)
    {
        if (deferBindingChange(wasRunning, previousEnabled, previousLocalNetworkEnabled, enabled,
            localNetworkEnabled))
            return false;
        return previousEnabled != enabled || wasRunning != enabled
            || (wasRunning && previousPort != port);
    }

    private static boolean deferBindingChange(boolean wasRunning, boolean previousEnabled,
                                             boolean previousLocalNetworkEnabled, boolean enabled,
                                             boolean localNetworkEnabled)
    {
        return wasRunning && previousEnabled && enabled && previousLocalNetworkEnabled != localNetworkEnabled;
    }

    private static String successMessage(boolean wasRunning, boolean previousEnabled,
                                         boolean previousLocalNetworkEnabled, boolean passwordChanged,
                                         McpSettingsDialog.Result result)
    {
        if (deferBindingChange(wasRunning, previousEnabled, previousLocalNetworkEnabled, result.enabled(),
            result.localNetworkEnabled()))
            return passwordChanged
                ? "MCP-Einstellungen gespeichert. Neues Passwort ist aktiv; Netzwerk-Bindung und Port werden erst nach einem Neustart von Hibiscus aktiv."
                : "MCP-Einstellungen gespeichert. Netzwerk-Bindung und Port werden erst nach einem Neustart von Hibiscus aktiv.";
        return McpSettings.isEnabled()
            ? "MCP-Server aktiviert: " + McpSettings.endpoint()
            : "MCP-Server deaktiviert.";
    }

    private void restorePreviousSettings(boolean enabled, boolean writeEnabled, boolean localNetworkEnabled,
                                         int port, String ngrokAuthtoken, String password,
                                         boolean passwordChanged, Exception original)
    {
        McpSettings.setEnabled(enabled);
        McpSettings.setWriteEnabled(writeEnabled);
        McpSettings.setLocalNetworkEnabled(localNetworkEnabled);
        McpSettings.setPort(port);
        McpSettings.setNgrokAuthtoken(ngrokAuthtoken);
        try
        {
            if (passwordChanged)
                McpSettings.setPassword(password);
            McpServerManager.get().restart();
        }
        catch (Exception restoreError)
        {
            original.addSuppressed(restoreError);
        }
    }
}
