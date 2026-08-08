package de.open4me.hibiscus.reports.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import de.open4me.hibiscus.reports.automation.model.AutomationTriggerTypes;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.internal.buttons.Cancel;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.util.ApplicationException;

final class AutomationScheduleDialog extends AbstractDialog<AutomationScheduleDialog.Result>
{
    private static final int WINDOW_WIDTH = 760;
    private static final int WINDOW_HEIGHT = 440;
    static final String NO_TRIGGER = "";

    private final String type;
    private final String expression;
    private Combo typeInput;
    private AutomationScheduleEditor editor;
    private Label typeInfo;
    private Result result;

    record Result(String type, String schedule)
    {
        Result
        {
            type = type == null ? NO_TRIGGER : type;
            schedule = schedule == null ? "" : schedule.trim();
        }
    }

    AutomationScheduleDialog(String expression)
    {
        this(AutomationTriggerTypes.CRON, expression);
    }

    AutomationScheduleDialog(String type, String expression)
    {
        super(POSITION_CENTER);
        this.type = type == null ? NO_TRIGGER : type;
        this.expression = expression == null ? "" : expression;
        setTitle("Auslöser bearbeiten");
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
    }

    @Override
    protected void paint(Composite parent) throws Exception
    {
        parent.setLayout(new GridLayout(1, false));

        Composite typeRow = new Composite(parent, SWT.NONE);
        typeRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        typeRow.setLayout(new GridLayout(2, false));
        new Label(typeRow, SWT.NONE).setText("Typ:");
        typeInput = new Combo(typeRow, SWT.DROP_DOWN | SWT.READ_ONLY);
        typeInput.setItems(new String[] { "Kein Auslöser", "Zeitplan", "Nach erfolgreicher Synchronisierung",
            "Neuer Umsatz" });
        typeInput.select(selectionIndex(type));
        typeInput.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        typeInput.addListener(SWT.Selection, event -> refreshType());

        editor = new AutomationScheduleEditor(parent, SWT.NONE);
        GridData data = new GridData(SWT.FILL, SWT.TOP, true, false);
        editor.setLayoutData(data);
        editor.setExpression(expression);

        typeInfo = new Label(parent, SWT.WRAP);
        typeInfo.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        refreshType();

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
                    String selectedType = selectedType();
                    result = new Result(selectedType,
                        AutomationTriggerTypes.CRON.equals(selectedType) ? editor.getExpression() : "");
                    close();
                }
                catch (Exception e)
                {
                    throw new ApplicationException("Ungueltiger Auslöser: " + e.getMessage(), e);
                }
            }
        }, null, true, "ok.png");
        buttons.addButton(new Cancel());
        buttons.paint(parent);
        getShell().setMinimumSize(WINDOW_WIDTH, WINDOW_HEIGHT);
    }

    @Override
    protected Result getData()
    {
        return result;
    }

    private void refreshType()
    {
        String selectedType = selectedType();
        boolean schedule = AutomationTriggerTypes.CRON.equals(selectedType);
        setVisible(editor, schedule);
        setVisible(typeInfo, !schedule && !NO_TRIGGER.equals(selectedType));
        if (typeInfo != null && !typeInfo.isDisposed())
            typeInfo.setText(infoText(selectedType));
        if (editor != null && !editor.isDisposed())
            editor.getParent().layout(true, true);
    }

    private String selectedType()
    {
        if (typeInput == null)
            return AutomationTriggerTypes.CRON;
        return switch (typeInput.getSelectionIndex())
        {
            case 1 -> AutomationTriggerTypes.CRON;
            case 2 -> AutomationTriggerTypes.SYNC_AFTER;
            case 3 -> AutomationTriggerTypes.TRANSACTION_NEW;
            default -> NO_TRIGGER;
        };
    }

    private static int selectionIndex(String type)
    {
        if (type == null || type.isBlank())
            return 0;
        if (AutomationTriggerTypes.CRON.equals(type))
            return 1;
        if (AutomationTriggerTypes.SYNC_AFTER.equals(type))
            return 2;
        if (AutomationTriggerTypes.TRANSACTION_NEW.equals(type))
            return 3;
        return 0;
    }

    private static String infoText(String type)
    {
        if (AutomationTriggerTypes.TRANSACTION_NEW.equals(type))
            return "Startet fuer jeden neuen Hibiscus-Umsatz genau einmal. "
                + "Das Script erhaelt den Umsatz als Variable \"umsatz\".";
        return "Startet nach einer erfolgreichen Hibiscus-Synchronisierung. "
            + "Synchronisierungen, die aus einer Automation heraus gestartet werden, loesen diesen Ausloeser nicht erneut aus.";
    }

    private static void setVisible(org.eclipse.swt.widgets.Control control, boolean visible)
    {
        if (control == null || control.isDisposed())
            return;
        control.setVisible(visible);
        Object layoutData = control.getLayoutData();
        if (layoutData instanceof GridData data)
            data.exclude = !visible;
    }
}
