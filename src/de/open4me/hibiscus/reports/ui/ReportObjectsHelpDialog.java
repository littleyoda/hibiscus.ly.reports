package de.open4me.hibiscus.reports.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.logging.Logger;

final class ReportObjectsHelpDialog extends AbstractDialog<Void>
{
    private static final String RESOURCE = "help/de_de/reports-objects.html";
    private static final int WINDOW_WIDTH = 900;
    private static final int WINDOW_HEIGHT = 700;

    ReportObjectsHelpDialog()
    {
        super(POSITION_CENTER);
        setTitle("Report-Objekte");
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
    }

    @Override
    protected void paint(Composite parent) throws Exception
    {
        String html = readHelp();
        parent.setLayout(new GridLayout(1, false));

        Composite content = new Composite(parent, SWT.NONE);
        content.setLayout(new FillLayout());
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        try
        {
            Browser browser = new Browser(content, SWT.BORDER);
            browser.setText(html);
        }
        catch (SWTError e)
        {
            Logger.warn("unable to create SWT browser for report objects help, falling back to text: "
                + e.getMessage());
            Text text = new Text(content, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.READ_ONLY);
            text.setText(toPlainText(html));
        }

        Composite buttons = new Composite(parent, SWT.NONE);
        buttons.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        GridLayout buttonLayout = new GridLayout(1, false);
        buttonLayout.marginHeight = 0;
        buttonLayout.marginWidth = 0;
        buttons.setLayout(buttonLayout);
        Button close = new Button(buttons, SWT.PUSH);
        close.setText("Schließen");
        close.addListener(SWT.Selection, event -> close());

        getShell().setMinimumSize(getShell().computeSize(WINDOW_WIDTH, WINDOW_HEIGHT));
    }

    private static String readHelp() throws IOException
    {
        ClassLoader loader = ReportObjectsHelpDialog.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(RESOURCE))
        {
            if (stream == null)
                throw new IOException("missing resource: " + RESOURCE);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String toPlainText(String html)
    {
        return html.replaceAll("(?is)<style.*?</style>", "")
            .replaceAll("(?i)<br\\s*/?>", "\n")
            .replaceAll("(?i)</(p|h1|h2|tr|table|pre|ul)>", "\n")
            .replaceAll("(?i)</(td|th)>", "\t")
            .replaceAll("(?s)<[^>]+>", "")
            .replace("&quot;", "\"")
            .replace("&gt;", ">")
            .replace("&lt;", "<")
            .replace("&amp;", "&")
            .replaceAll("[ \\t]+\\n", "\n")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }

    @Override
    protected Void getData()
    {
        return null;
    }
}
