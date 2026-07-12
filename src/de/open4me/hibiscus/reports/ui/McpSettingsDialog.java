package de.open4me.hibiscus.reports.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import de.open4me.hibiscus.reports.mcp.McpServerManager;
import de.open4me.hibiscus.reports.mcp.McpSettings;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.input.CheckboxInput;
import de.willuhn.jameica.gui.input.TextInput;
import de.willuhn.jameica.gui.internal.buttons.Cancel;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.SWTUtil;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.util.ApplicationException;

final class McpSettingsDialog extends AbstractDialog<McpSettingsDialog.Result>
{
    private static final int WINDOW_WIDTH = 720;

    private final CheckboxInput enabled;
    private final CheckboxInput writeEnabled;
    private final TextInput port;
    private final CheckboxInput regenerateToken;
    private Text endpoint;
    private Text token;
    private Result result;

    record Result(boolean enabled, boolean writeEnabled, int port, boolean regenerateToken)
    {
    }

    McpSettingsDialog()
    {
        super(POSITION_CENTER);
        setTitle("Reports MCP-Server");
        setSize(WINDOW_WIDTH, SWT.DEFAULT);
        enabled = new CheckboxInput(McpSettings.isEnabled());
        enabled.setName("MCP-Server aktivieren");
        writeEnabled = new CheckboxInput(McpSettings.isWriteEnabled());
        writeEnabled.setName("Ueberweisungen anlegen");
        port = new TextInput(Integer.toString(McpSettings.getPort()));
        port.setName("Port");
        regenerateToken = new CheckboxInput(false);
        regenerateToken.setName("Token beim Speichern neu erzeugen");
    }

    @Override
    protected void paint(Composite parent) throws Exception
    {
        Container container = new SimpleContainer(parent);
        container.addText(
            "MCP steht fuer Model Context Protocol. Ein MCP-Server stellt Daten und Werkzeuge fuer "
                + "lokale KI-Clients wie Codex oder Claude Desktop bereit. Dieser Server bietet die "
                + "Report-Template-Objekte standardmaessig lesend an. Ueberweisungen anlegen erlaubt "
                + "nur das lokale Anlegen von Entwuerfen, kein Absenden an die Bank. Der Server bindet "
                + "nur an 127.0.0.1 und akzeptiert nur Requests mit dem angezeigten Bearer-Token.",
            true);
        container.addInput(enabled);
        container.addInput(writeEnabled);
        container.addInput(port);
        endpoint = copyRow(container.getComposite(), "Endpoint", McpSettings.endpoint(), "Endpoint kopieren");
        token = copyRow(container.getComposite(), "Bearer-Token", McpSettings.ensureToken(), "Token kopieren");
        if (port.getControl() instanceof Text portText)
            portText.addModifyListener(event -> endpoint.setText(endpointFromInput()));
        container.addInput(regenerateToken);
        container.addText("Status: " + (McpServerManager.get().isRunning() ? "laeuft" : "gestoppt"), true);
        container.addSeparator();

        ButtonArea buttons = new ButtonArea();
        buttons.addButton("Uebernehmen", new Action()
        {
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                try
                {
                    int parsedPort = Integer.parseInt(((String) port.getValue()).trim());
                    if (parsedPort <= 0 || parsedPort > 65535)
                        throw new NumberFormatException("Port ausserhalb des gueltigen Bereichs.");
                    result = new Result((Boolean) enabled.getValue(), (Boolean) writeEnabled.getValue(), parsedPort,
                        (Boolean) regenerateToken.getValue());
                    close();
                }
                catch (Exception e)
                {
                    throw new ApplicationException("Ungueltige MCP-Einstellungen: " + e.getMessage(), e);
                }
            }
        }, null, true, "ok.png");
        buttons.addButton(new Cancel());
        container.addButtonArea(buttons);
        getShell().setMinimumSize(getShell().computeSize(WINDOW_WIDTH, SWT.DEFAULT));
    }

    private Text copyRow(Composite parent, String labelText, String value, String tooltip)
    {
        Composite row = new Composite(parent, SWT.NONE);
        GridData rowData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        rowData.horizontalSpan = 2;
        row.setLayoutData(rowData);
        row.setLayout(new GridLayout(3, false));

        Label label = new Label(row, SWT.NONE);
        label.setText(labelText);
        label.setLayoutData(new GridData(120, SWT.DEFAULT));

        Text text = new Text(row, SWT.BORDER | SWT.READ_ONLY);
        text.setText(value == null ? "" : value);
        text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Button copy = new Button(row, SWT.PUSH);
        copy.setToolTipText(tooltip);
        copy.setImage(SWTUtil.getImage("edit-copy.png"));
        copy.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
        copy.addListener(SWT.Selection, event -> {
            try
            {
                copyToClipboard(text.getText(), tooltip);
            }
            catch (ApplicationException e)
            {
                GUI.getStatusBar().setErrorText(e.getMessage());
            }
        });
        return text;
    }

    private void copyToClipboard(String value, String description) throws ApplicationException
    {
        try
        {
            Clipboard clipboard = new Clipboard(GUI.getDisplay());
            try
            {
                clipboard.setContents(new Object[] { value }, new Transfer[] { TextTransfer.getInstance() });
            }
            finally
            {
                clipboard.dispose();
            }
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                description + ": in die Zwischenablage kopiert.", StatusBarMessage.TYPE_SUCCESS));
        }
        catch (Exception e)
        {
            throw new ApplicationException("Wert konnte nicht kopiert werden: "
                + e.getMessage(), e);
        }
    }

    private String endpointFromInput()
    {
        try
        {
            int parsedPort = Integer.parseInt(((String) port.getValue()).trim());
            if (parsedPort > 0 && parsedPort <= 65535)
                return "http://127.0.0.1:" + parsedPort + "/mcp";
        }
        catch (Exception ignored)
        {
        }
        return McpSettings.endpoint();
    }

    @Override
    protected Result getData()
    {
        return result;
    }
}
