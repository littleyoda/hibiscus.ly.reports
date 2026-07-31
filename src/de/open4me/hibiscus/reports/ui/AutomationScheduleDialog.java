package de.open4me.hibiscus.reports.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.internal.buttons.Cancel;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.util.ApplicationException;

final class AutomationScheduleDialog extends AbstractDialog<String>
{
    private static final int WINDOW_WIDTH = 760;
    private static final int WINDOW_HEIGHT = 440;

    private final String expression;
    private AutomationScheduleEditor editor;
    private String result;

    AutomationScheduleDialog(String expression)
    {
        super(POSITION_CENTER);
        this.expression = expression == null ? "" : expression;
        setTitle("Zeitplan bearbeiten");
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
    }

    @Override
    protected void paint(Composite parent) throws Exception
    {
        parent.setLayout(new GridLayout(1, false));

        editor = new AutomationScheduleEditor(parent, SWT.NONE);
        GridData data = new GridData(SWT.FILL, SWT.TOP, true, false);
        editor.setLayoutData(data);
        editor.setExpression(expression);

        Label separator = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL);
        separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        ButtonArea buttons = new ButtonArea();
        buttons.addButton("Uebernehmen", new Action()
        {
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                try
                {
                    result = editor.getExpression();
                    close();
                }
                catch (Exception e)
                {
                    throw new ApplicationException("Ungueltiger Zeitplan: " + e.getMessage(), e);
                }
            }
        }, null, true, "ok.png");
        buttons.addButton(new Cancel());
        buttons.paint(parent);
        getShell().setMinimumSize(WINDOW_WIDTH, WINDOW_HEIGHT);
    }

    @Override
    protected String getData()
    {
        return result;
    }
}
