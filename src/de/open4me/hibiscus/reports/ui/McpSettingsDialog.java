package de.open4me.hibiscus.reports.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
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
    private final CheckboxInput localNetworkEnabled;
    private final TextInput port;
    private Text endpoint;
    private Text password;
    private Text ngrokAuthtoken;
    private Text publicEndpoint;
    private Label publicStatus;
    private Result result;

    record Result(boolean enabled, boolean writeEnabled, boolean localNetworkEnabled, int port,
                  String ngrokAuthtoken, String password)
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
        localNetworkEnabled = new CheckboxInput(McpSettings.isLocalNetworkEnabled());
        localNetworkEnabled.setName("Zugriff aus lokalem Netzwerk erlauben");
        port = new TextInput(Integer.toString(McpSettings.getPort()));
        port.setName("Port");
    }

    @Override
    protected void paint(Composite parent) throws Exception
    {
        Container container = new SimpleContainer(parent, true, 2);
        TabFolder tabs = new TabFolder(container.getComposite(), SWT.NONE);
        GridData tabsData = new GridData(SWT.FILL, SWT.FILL, true, true);
        tabsData.horizontalSpan = 2;
        tabs.setLayoutData(tabsData);
        createLocalTab(tabs);
        createGlobalTab(tabs);

        container.addHeadline("Gemeinsames Passwort");
        container.addText("Lokal und im Netzwerk als Bearer-Token, global auch für OAuth. "
            + "Ein neues Passwort macht bisherige Bearer-Tokens und OAuth-Anmeldungen ungültig.", true);
        password = copyRow(container.getComposite(), "Passwort", McpSettings.ensureToken(), "Passwort kopieren");
        Button regeneratePassword = new Button(container.getComposite(), SWT.PUSH);
        regeneratePassword.setText("Password neu erzeugen");
        GridData passwordButtonData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
        passwordButtonData.horizontalSpan = 2;
        regeneratePassword.setLayoutData(passwordButtonData);
        regeneratePassword.addListener(SWT.Selection,
            event -> password.setText(McpSettings.generatePassword()));

        if (port.getControl() instanceof Text portText)
            portText.addModifyListener(event -> endpoint.setText(endpointFromInput()));
        if (localNetworkEnabled.getControl() instanceof Button localNetworkButton)
            localNetworkButton.addListener(SWT.Selection, event -> endpoint.setText(endpointFromInput()));
        refreshStatus();
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
                    result = new Result((Boolean) enabled.getValue(), (Boolean) writeEnabled.getValue(),
                        (Boolean) localNetworkEnabled.getValue(), parsedPort,
                        ngrokAuthtoken.getText().trim(), password.getText());
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

    private void createLocalTab(TabFolder tabs) throws Exception
    {
        Container local = new SimpleContainer(tab(tabs, "Freigabe lokal / lokales Netzwerk"), true);
        local.addText("Der MCP-Server ist zunächst nur auf diesem Rechner erreichbar. "
            + "Mit der Netzwerkfreigabe können auch Geräte im lokalen Netzwerk zugreifen. "
            + "Berechtigte Clients können Hibiscus-Daten lesen. Die Option für Überweisungen "
            + "erlaubt zusätzlich lokale Entwürfe, aber kein Absenden an die Bank. "
            + "Änderungen der Netzwerk-Bindung werden erst nach einem Hibiscus-Neustart aktiv.", true);
        local.addInput(enabled);
        local.addInput(writeEnabled);
        local.addInput(localNetworkEnabled);
        local.addInput(port);
        endpoint = copyRow(local.getComposite(), "Endpoint", McpSettings.endpoint(), "Endpoint kopieren");
        local.addText("Token-Nutzung: Lokale Clients und Clients im lokalen Netzwerk müssen bei jeder "
            + "Anfrage den HTTP-Header Authorization: Bearer <Passwort> senden. Das gemeinsame "
            + "Passwort steht unter den Tabs. OAuth ist für diese Freigabe nicht verfügbar. "
            + "Im lokalen Netzwerk wird der Token über HTTP unverschlüsselt übertragen; "
            + "verwende die Netzwerkfreigabe nur in vertrauenswürdigen Netzen.", true);
        local.addText("Status: " + (McpServerManager.get().isRunning() ? "läuft" : "gestoppt"), true);
    }

    private void createGlobalTab(TabFolder tabs)
    {
        Container global = new SimpleContainer(tab(tabs, "Globale Freigabe"), true);
        Label warning = new Label(global.getComposite(), SWT.WRAP);
        warning.setText("Warnung: Wenn der MCP-Server läuft und ein ngrok Authtoken hinterlegt ist, "
            + "ist der MCP-Endpoint weltweit über das Internet erreichbar. Die öffentliche Adresse "
            + "ist kein Zugriffsschutz. Wer das gemeinsame Passwort oder einen gültigen OAuth-Zugriffstoken "
            + "besitzt, kann Hibiscus-Daten abrufen. Ist das Anlegen von Überweisungsentwürfen aktiviert, "
            + "können berechtigte Clients auch Entwürfe erstellen. Gib Passwort und ngrok Authtoken "
            + "nur an vertrauenswürdige Personen weiter. Zum Beenden der globalen Freigabe das "
            + "ngrok Authtoken entfernen.");
        GridData warningData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        warningData.horizontalSpan = 2;
        warningData.widthHint = 640;
        warning.setLayoutData(warningData);

        Link ngrokLink = new Link(global.getComposite(), SWT.NONE);
        ngrokLink.setText("<a href=\"https://ngrok.com/\">https://ngrok.com/</a>");
        GridData ngrokLinkData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
        ngrokLinkData.horizontalSpan = 2;
        ngrokLink.setLayoutData(ngrokLinkData);
        ngrokLink.addListener(SWT.Selection, event -> Program.launch(event.text));

        Composite ngrokRow = new Composite(global.getComposite(), SWT.NONE);
        GridData ngrokData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        ngrokData.horizontalSpan = 2;
        ngrokRow.setLayoutData(ngrokData);
        ngrokRow.setLayout(new GridLayout(2, false));
        Label ngrokLabel = new Label(ngrokRow, SWT.NONE);
        ngrokLabel.setText("ngrok Authtoken");
        ngrokLabel.setLayoutData(new GridData(120, SWT.DEFAULT));
        ngrokAuthtoken = new Text(ngrokRow, SWT.BORDER | SWT.PASSWORD);
        ngrokAuthtoken.setText(McpSettings.getNgrokAuthtoken());
        ngrokAuthtoken.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        publicEndpoint = copyRow(global.getComposite(), "Oeffentlicher Endpoint",
            McpServerManager.get().publicEndpoint(), "Oeffentlichen Endpoint kopieren");
        copyRow(global.getComposite(), "OAuth-Benutzer", "hibiscus-mcp", "Benutzername kopieren");
        publicStatus = new Label(global.getComposite(), SWT.NONE);
        publicStatus.setText("ngrok: " + McpServerManager.get().publicStatus());
        GridData statusData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        statusData.horizontalSpan = 2;
        publicStatus.setLayoutData(statusData);
    }

    private static Composite tab(TabFolder tabs, String title)
    {
        TabItem item = new TabItem(tabs, SWT.NONE);
        item.setText(title);
        Composite content = new Composite(tabs, SWT.NONE);
        content.setLayout(new GridLayout(1, false));
        item.setControl(content);
        return content;
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
                return McpSettings.endpoint(parsedPort, (Boolean) localNetworkEnabled.getValue());
        }
        catch (Exception ignored)
        {
        }
        return McpSettings.endpoint();
    }

    private void refreshStatus()
    {
        if (getShell() == null || getShell().isDisposed())
            return;
        String address = McpServerManager.get().publicEndpoint();
        if (!publicEndpoint.getText().equals(address))
            publicEndpoint.setText(address);
        String status = "ngrok: " + McpServerManager.get().publicStatus();
        if (!publicStatus.getText().equals(status))
        {
            publicStatus.setText(status);
            publicStatus.getParent().layout();
        }
        GUI.getDisplay().timerExec(500, this::refreshStatus);
    }

    @Override
    protected Result getData()
    {
        return result;
    }
}
