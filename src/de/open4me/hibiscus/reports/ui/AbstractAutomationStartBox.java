package de.open4me.hibiscus.reports.ui;

import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import de.open4me.hibiscus.reports.automation.model.Automation;
import de.open4me.hibiscus.reports.automation.model.AutomationRun;
import de.open4me.hibiscus.reports.automation.runtime.AutomationService;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.boxes.AbstractBox;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;

abstract class AbstractAutomationStartBox extends AbstractBox
{
    private static final String EMPTY_NAME = "Automatisierung: freier Automatisierungs-Platz";

    abstract int slot();

    @Override
    public String getName()
    {
        Automation automation = assignedAutomation();
        return automation == null ? EMPTY_NAME : "Automatisierung: " + automation.name();
    }

    @Override
    public boolean getDefaultEnabled()
    {
        return false;
    }

    @Override
    public int getDefaultIndex()
    {
        return 1100 + slot();
    }

    @Override
    public boolean isActive()
    {
        return assignedAutomation() != null;
    }

    @Override
    public int getHeight()
    {
        return 140;
    }

    @Override
    public void paint(Composite parent)
    {
        Automation automation = assignedAutomation();
        if (automation == null)
        {
            new Label(parent, SWT.WRAP).setText("Die Automation ist nicht mehr vorhanden.");
            return;
        }

        Composite root = new Composite(parent, SWT.NONE);
        root.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        root.setLayout(layout);

        Button run = new Button(root, SWT.PUSH);
        run.setText(automation.name());
        run.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));

        Label lastRun = new Label(root, SWT.WRAP);
        lastRun.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        refreshLastRun(lastRun, automation);

        run.addListener(SWT.Selection, event -> runManual(automation, lastRun));
    }

    private void runManual(Automation automation, Label lastRun)
    {
        try
        {
            Automation current = AutomationService.get().repository().getAutomation(automation.id());
            if (current == null)
            {
                GUI.getStatusBar().setErrorText("Automation ist nicht mehr vorhanden.");
                return;
            }
            AutomationService.get().runManual(current, false, () -> GUI.getDisplay().asyncExec(() -> {
                if (lastRun == null || lastRun.isDisposed())
                    return;
                refreshLastRun(lastRun, current);
                lastRun.getParent().layout(true, true);
            }));
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                "Automation gestartet.", StatusBarMessage.TYPE_SUCCESS));
            refreshLastRun(lastRun, current);
        }
        catch (Exception e)
        {
            Logger.error("unable to start automation from start box " + automation.name(), e);
            GUI.getStatusBar().setErrorText("Automation konnte nicht gestartet werden: " + message(e));
        }
    }

    private void refreshLastRun(Label label, Automation automation)
    {
        try
        {
            label.setText(AutomationStartBoxText.lastRunText(lastRun(automation)));
        }
        catch (Exception e)
        {
            Logger.error("unable to load automation run history for start box " + automation.name(), e);
            label.setText("Letzter Lauf: konnte nicht geladen werden: " + message(e));
        }
    }

    private static AutomationRun lastRun(Automation automation) throws Exception
    {
        if (automation == null || automation.id() == null)
            return null;
        List<AutomationRun> runs = AutomationService.get().repository().listRuns(automation.id(), 1);
        return runs.isEmpty() ? null : runs.get(0);
    }

    private Automation assignedAutomation()
    {
        try
        {
            Map<Integer, Automation> assignments = AutomationStartBoxAssignments.assignments(
                AutomationService.get().repository().listAutomations());
            return assignments.get(slot());
        }
        catch (Exception e)
        {
            Logger.error("unable to resolve automation start box slot " + slot(), e);
            return null;
        }
    }

    private static String message(Throwable error)
    {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getName() : message;
    }
}
