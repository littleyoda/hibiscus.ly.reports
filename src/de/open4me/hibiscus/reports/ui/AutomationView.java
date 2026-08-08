package de.open4me.hibiscus.reports.ui;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import de.open4me.hibiscus.reports.automation.model.Automation;
import de.open4me.hibiscus.reports.automation.model.AutomationDecision;
import de.open4me.hibiscus.reports.automation.model.AutomationLogEntry;
import de.open4me.hibiscus.reports.automation.model.AutomationRun;
import de.open4me.hibiscus.reports.automation.model.AutomationTrigger;
import de.open4me.hibiscus.reports.automation.model.AutomationTriggerTypes;
import de.open4me.hibiscus.reports.automation.model.MissedTriggerPolicy;
import de.open4me.hibiscus.reports.automation.model.RunMode;
import de.open4me.hibiscus.reports.automation.model.RunStatus;
import de.open4me.hibiscus.reports.automation.runtime.AutomationSchedule;
import de.open4me.hibiscus.reports.automation.runtime.AutomationScheduleSpec;
import de.open4me.hibiscus.reports.automation.runtime.AutomationService;
import de.open4me.hibiscus.reports.automation.runtime.AutomationJsonTransfer;
import de.open4me.hibiscus.reports.automation.sql.AutomationRepository;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.AbstractView;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.OperationCanceledException;
import de.willuhn.util.ApplicationException;

public final class AutomationView extends AbstractView
{
    private static final int LABEL_WIDTH = 150;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private AutomationRepository repository;
    private List<Automation> automations = List.of();
    private List<AutomationRun> runs = List.of();
    private Automation selected;

    private Combo automationCombo;
    private Text description;
    private Button scheduleActive;
    private Combo missed;
    private Text scheduleSummary;
    private String triggerType = AutomationTriggerTypes.CRON;
    private String scheduleExpression = "";
    private Text script;
    private Table runsTable;
    private Text runLogs;
    private Button saveButton;
    private Label dirtyLabel;
    private TabItem historyTab;
    private Composite decisionArea;
    private Label decisionText;
    private Button catchUpDecision;
    private Button ignoreDecision;
    private AutomationDecision selectedDecision;
    private AutomationEditorState savedState = AutomationEditorState.empty();
    private String nameText = "";
    private boolean loading;

    private enum UnsavedDecision
    {
        SAVE,
        DISCARD,
        CANCEL
    }

    @Override
    public void bind() throws Exception
    {
        GUI.getView().setTitle("Automatisierung");
        repository = AutomationService.get().repository();

        Composite root = new Composite(getParent(), SWT.NONE);
        root.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        root.setLayout(new GridLayout(1, false));
        createList(root);
        createTabs(root);

        Automation initial = getCurrentObject() instanceof Automation automation ? automation : null;
        loadAutomations(initial);
    }

    @Override
    public void unbind() throws ApplicationException
    {
        try
        {
            if (!resolveUnsavedChanges())
                throw new ApplicationException("Ungespeicherte Änderungen.");
        }
        catch (ApplicationException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ApplicationException("Ungespeicherte Änderungen konnten nicht verarbeitet werden: "
                + e.getMessage(), e);
        }
    }

    private void createList(Composite parent)
    {
        Composite row = new Composite(parent, SWT.NONE);
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        row.setLayout(new GridLayout(6, false));

        automationCombo = new Combo(row, SWT.DROP_DOWN);
        GridData comboData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        comboData.widthHint = 420;
        automationCombo.setLayoutData(comboData);
        automationCombo.addListener(SWT.Selection, event -> selectFromCombo());
        automationCombo.addListener(SWT.Modify, event -> markNameDirty());
        fixedButton(row, "Neu", this::newAutomation);
        fixedButton(row, "Kopieren", this::copyAutomation);
        fixedButton(row, "Loeschen", this::deleteAutomation);
        fixedButton(row, "Importieren", this::importAutomation);
        fixedButton(row, "Exportieren", this::exportAutomation);
    }

    private void createTabs(Composite parent)
    {
        TabFolder tabs = new TabFolder(parent, SWT.NONE);
        tabs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        createConfigurationTab(tabs);
        createHistoryTab(tabs);
        tabs.addListener(SWT.Selection, event -> {
            if (event.item == historyTab)
                refreshHistoryFromTab();
        });
    }

    private void createConfigurationTab(TabFolder tabs)
    {
        Composite tab = tab(tabs, "Konfiguration");
        createEditor(tab);
    }

    private void createEditor(Composite parent)
    {
        Composite form = new Composite(parent, SWT.NONE);
        form.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        form.setLayout(new GridLayout(2, false));

        description = text(form, "Beschreibung", SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        height(description, 50);
        description.addListener(SWT.Modify, event -> markDirty());
        missed = combo(form, "Verpasste Trigger", new String[] { "ignorieren", "nachholen", "nachfragen" });
        missed.addListener(SWT.Selection, event -> markDirty());
        label(form, "");
        scheduleActive = new Button(form, SWT.CHECK);
        scheduleActive.setText("Auslöser aktiv");
        scheduleActive.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        scheduleActive.addListener(SWT.Selection, event -> markDirty());
        label(form, "Auslöser");
        Composite scheduleRow = new Composite(form, SWT.NONE);
        scheduleRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        scheduleRow.setLayout(new GridLayout(2, false));
        scheduleSummary = new Text(scheduleRow, SWT.BORDER | SWT.READ_ONLY);
        scheduleSummary.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Button editSchedule = new Button(scheduleRow, SWT.PUSH);
        editSchedule.setText("Bearbeiten");
        editSchedule.addListener(SWT.Selection, event -> editSchedule());
        script = text(form, "Script", SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        height(script, 320);
        script.addListener(SWT.Modify, event -> markDirty());

        Composite buttons = new Composite(parent, SWT.NONE);
        buttons.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        buttons.setLayout(new GridLayout(4, false));
        saveButton = button(buttons, "Speichern", this::save);
        dirtyLabel = new Label(buttons, SWT.NONE);
        dirtyLabel.setText("Ungespeicherte Änderungen");
        dirtyLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        button(buttons, "Manuell ausfuehren", () -> run(false));
        button(buttons, "Testlauf", () -> run(true));
        savedState = currentState();
        updateDirtyState();
    }

    private void createHistoryTab(TabFolder tabs)
    {
        Composite tab = tab(tabs, "Verlauf");
        historyTab = tabs.getItem(tabs.getItemCount() - 1);
        tab.setLayout(new GridLayout(1, false));

        runsTable = new Table(tab, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
        runsTable.setHeaderVisible(true);
        runsTable.setLinesVisible(true);
        runsTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        column(runsTable, "Status", 130);
        column(runsTable, "Ausloeser", 120);
        column(runsTable, "Start", 150);
        column(runsTable, "Ende", 150);
        column(runsTable, "Hinweis", 500);
        runsTable.addListener(SWT.Selection, event -> showSelectedRunLogs());

        runLogs = new Text(tab, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.READ_ONLY);
        GridData logsData = new GridData(SWT.FILL, SWT.FILL, true, false);
        logsData.heightHint = 140;
        runLogs.setLayoutData(logsData);

        decisionArea = new Composite(tab, SWT.NONE);
        decisionArea.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        decisionArea.setLayout(new GridLayout(3, false));
        decisionText = new Label(decisionArea, SWT.NONE);
        decisionText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        catchUpDecision = fixedButton(decisionArea, "Jetzt nachholen", this::acceptDecision);
        ignoreDecision = fixedButton(decisionArea, "Ignorieren", this::rejectDecision);
        showDecision(null);

        Composite buttons = new Composite(tab, SWT.NONE);
        buttons.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        buttons.setLayout(new GridLayout(1, true));
        button(buttons, "Aktualisieren", this::reloadSelectedAutomation);
    }

    private static Composite tab(TabFolder folder, String title)
    {
        TabItem item = new TabItem(folder, SWT.NONE);
        item.setText(title);
        Composite tab = new Composite(folder, SWT.NONE);
        tab.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        tab.setLayout(new GridLayout(1, false));
        item.setControl(tab);
        return tab;
    }

    private Text text(Composite parent, String label, int style)
    {
        label(parent, label);
        Text text = new Text(parent, style);
        text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        return text;
    }

    private Combo combo(Composite parent, String label, String[] values)
    {
        label(parent, label);
        Combo combo = new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY);
        combo.setItems(values);
        combo.select(0);
        combo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return combo;
    }

    private static Label label(Composite parent, String text)
    {
        Label label = new Label(parent, SWT.NONE);
        label.setText(text);
        label.setLayoutData(new GridData(LABEL_WIDTH, SWT.DEFAULT));
        return label;
    }

    private Button button(Composite parent, String label, Runnable action)
    {
        Button button = new Button(parent, SWT.PUSH);
        button.setText(label);
        button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        button.addListener(SWT.Selection, event -> {
            try
            {
                action.run();
            }
            catch (RuntimeException e)
            {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                GUI.getStatusBar().setErrorText(cause.getMessage());
            }
        });
        return button;
    }

    private Button fixedButton(Composite parent, String label, Runnable action)
    {
        Button button = button(parent, label, action);
        GridData data = new GridData(SWT.FILL, SWT.CENTER, false, false);
        data.widthHint = 140;
        button.setLayoutData(data);
        return button;
    }

    private static void height(Text text, int height)
    {
        GridData data = (GridData) text.getLayoutData();
        data.heightHint = height;
        data.grabExcessVerticalSpace = true;
    }

    private static void column(Table table, String label, int width)
    {
        TableColumn column = new TableColumn(table, SWT.LEFT);
        column.setText(label);
        column.setWidth(width);
    }

    private void loadAutomations(Automation select)
    {
        try
        {
            loading = true;
            automations = repository.listAutomations();
            automationCombo.removeAll();
            for (Automation automation : automations)
                automationCombo.add(automation.name());
            int index = indexOf(select);
            if (index < 0 && !automations.isEmpty())
                index = 0;
            if (index >= 0)
            {
                automationCombo.select(index);
                selected = automations.get(index);
                loading = false;
                show(selected);
            }
            else
            {
                selected = null;
                loading = false;
                show(emptyAutomation());
            }
        }
        catch (Exception e)
        {
            loading = false;
            throw new RuntimeException(e);
        }
    }

    private int indexOf(Automation automation)
    {
        if (automation == null || automation.id() == null)
            return -1;
        for (int i = 0; i < automations.size(); i++)
        {
            if (automation.id().equals(automations.get(i).id()))
                return i;
        }
        return -1;
    }

    private void selectFromCombo()
    {
        if (loading)
            return;
        int index = automationCombo.getSelectionIndex();
        if (index >= 0 && index < automations.size())
        {
            Automation next = automations.get(index);
            if (selected != null && selected.id() != null && selected.id().equals(next.id()))
                return;
            try
            {
                if (!resolveUnsavedChanges())
                {
                    restoreComboSelection();
                    return;
                }
                selected = next;
                show(selected);
            }
            catch (Exception e)
            {
                restoreComboSelection();
                throw new RuntimeException(e);
            }
        }
    }

    private void show(Automation automation)
    {
        loading = true;
        nameText = automation.name();
        automationCombo.setText(automation.name());
        description.setText(automation.description());
        scheduleActive.setSelection(false);
        missed.select(Math.max(0, automation.missedTriggerPolicy().ordinal()));
        script.setText(automation.script());
        setTrigger("", "");
        try
        {
            if (automation.id() != null)
            {
                List<AutomationTrigger> triggers = repository.listTriggers(automation.id());
                if (!triggers.isEmpty())
                {
                    AutomationTrigger trigger = triggers.get(0);
                    scheduleActive.setSelection(trigger.active());
                    setTrigger(trigger.type(), trigger.schedule());
                }
            }
        }
        catch (Exception ignored)
        {
        }
        finally
        {
            loading = false;
        }
        savedState = currentState();
        updateDirtyState();
        refreshHistory();
    }

    private void editSchedule()
    {
        try
        {
            AutomationScheduleDialog.Result value = new AutomationScheduleDialog(triggerType, scheduleExpression).open();
            if (value != null)
                setTrigger(value.type(), value.schedule());
        }
        catch (de.willuhn.jameica.system.OperationCanceledException ignored)
        {
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void setScheduleExpression(String expression)
    {
        setTrigger(AutomationTriggerTypes.CRON, expression);
    }

    private void setTrigger(String type, String expression)
    {
        scheduleExpression = expression == null ? "" : expression.trim();
        triggerType = type == null ? "" : type;
        if (AutomationTriggerTypes.CRON.equals(triggerType) && scheduleExpression.isBlank())
            triggerType = "";
        scheduleSummary.setText(triggerSummary(triggerType, scheduleExpression));
        markDirty();
    }

    private static String triggerSummary(String type, String expression)
    {
        if (AutomationTriggerTypes.SYNC_AFTER.equals(type))
            return "Nach erfolgreicher Synchronisierung";
        if (AutomationTriggerTypes.TRANSACTION_NEW.equals(type))
            return "Neuer Umsatz";
        if (type == null || type.isBlank() || expression == null || expression.isBlank())
            return "Kein Auslöser";
        return AutomationScheduleSpec.fromExpression(expression).describe();
    }

    private void markNameDirty()
    {
        if (loading)
            return;
        int index = automationCombo.getSelectionIndex();
        if (index >= 0 && index < automations.size()
            && automationCombo.getText().equals(automations.get(index).name()))
            return;
        nameText = automationCombo.getText();
        updateDirtyState();
    }

    private void markDirty()
    {
        if (!loading)
            updateDirtyState();
    }

    private boolean hasUnsavedChanges()
    {
        return currentState().differsFrom(savedState);
    }

    private void updateDirtyState()
    {
        boolean dirty = hasUnsavedChanges();
        if (saveButton != null && !saveButton.isDisposed())
            saveButton.setEnabled(dirty);
        if (dirtyLabel != null && !dirtyLabel.isDisposed())
        {
            dirtyLabel.setVisible(dirty);
            GridData data = (GridData) dirtyLabel.getLayoutData();
            data.exclude = !dirty;
            dirtyLabel.getParent().layout(true, true);
        }
    }

    private AutomationEditorState currentState()
    {
        if (automationCombo == null || automationCombo.isDisposed())
            return AutomationEditorState.empty();
        return new AutomationEditorState(nameText,
            description == null || description.isDisposed() ? "" : description.getText(),
            missed == null || missed.isDisposed() ? "" : missed.getText(),
            scheduleActive != null && !scheduleActive.isDisposed() && scheduleActive.getSelection(),
            triggerType,
            scheduleExpression,
            script == null || script.isDisposed() ? "" : script.getText());
    }

    private boolean resolveUnsavedChanges() throws Exception
    {
        if (!hasUnsavedChanges())
            return true;
        UnsavedDecision decision;
        try
        {
            decision = new UnsavedChangesDialog().open();
        }
        catch (OperationCanceledException e)
        {
            return false;
        }
        if (decision == UnsavedDecision.SAVE)
        {
            saveCurrent();
            ReportsNavigationRefresher.refresh();
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                "Automation gespeichert.", StatusBarMessage.TYPE_SUCCESS));
            return true;
        }
        if (decision == UnsavedDecision.DISCARD)
        {
            discardCurrentChanges();
            return true;
        }
        return false;
    }

    private void discardCurrentChanges()
    {
        if (selected == null || selected.id() == null)
            show(emptyAutomation());
        else
            loadAutomations(selected);
    }

    private void restoreComboSelection()
    {
        loading = true;
        try
        {
            automationCombo.setText(nameText);
        }
        finally
        {
            loading = false;
            updateDirtyState();
        }
    }

    private void newAutomation()
    {
        try
        {
            if (!resolveUnsavedChanges())
                return;
            selected = null;
            show(emptyAutomation());
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void copyAutomation()
    {
        try
        {
            if (!resolveUnsavedChanges())
                return;
            selected = null;
            automationCombo.setText(automationCombo.getText() + " Kopie");
            nameText = automationCombo.getText();
            updateDirtyState();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void deleteAutomation()
    {
        try
        {
            if (!resolveUnsavedChanges())
                return;
            if (selected == null || selected.id() == null)
                return;
            if (!Application.getCallback().askUser("Automation \"" + selected.name() + "\" loeschen?"))
                return;
            repository.deleteAutomation(selected.id());
            loadAutomations(null);
            ReportsNavigationRefresher.refresh();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void importAutomation()
    {
        try
        {
            if (!resolveUnsavedChanges())
                return;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        FileDialog dialog = new FileDialog(GUI.getShell(), SWT.OPEN);
        dialog.setText("Automation importieren");
        dialog.setFilterNames(new String[] { "Automation JSON (*.json)" });
        dialog.setFilterExtensions(new String[] { "*.json" });
        String selectedFile = dialog.open();
        if (selectedFile == null || selectedFile.isBlank())
            return;
        try
        {
            Automation imported = new AutomationJsonTransfer(repository).importAutomation(Path.of(selectedFile));
            loadAutomations(imported);
            ReportsNavigationRefresher.refresh();
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                "Automation importiert.", StatusBarMessage.TYPE_SUCCESS));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void reloadSelectedAutomation()
    {
        try
        {
            if (!resolveUnsavedChanges())
                return;
            loadAutomations(selected);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void exportAutomation()
    {
        if (selected == null || selected.id() == null)
        {
            GUI.getStatusBar().setErrorText("Bitte zuerst eine gespeicherte Automation auswaehlen.");
            return;
        }
        FileDialog dialog = new FileDialog(GUI.getShell(), SWT.SAVE);
        dialog.setText("Automation exportieren");
        dialog.setFileName(exportFileName(selected.name()));
        dialog.setFilterNames(new String[] { "Automation JSON (*.json)" });
        dialog.setFilterExtensions(new String[] { "*.json" });
        String selectedFile = dialog.open();
        if (selectedFile == null || selectedFile.isBlank())
            return;
        selectedFile = withJsonExtension(selectedFile);
        try
        {
            java.io.File target = new java.io.File(selectedFile);
            if (target.exists() && !Application.getCallback().askUser(
                "Datei \"" + target.getName() + "\" existiert bereits. Ueberschreiben?"))
                return;
            new AutomationJsonTransfer(repository).exportAutomation(selected, target.toPath());
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                "Automation exportiert.", StatusBarMessage.TYPE_SUCCESS));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void save()
    {
        try
        {
            saveCurrent();
            ReportsNavigationRefresher.refresh();
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                "Automation gespeichert.", StatusBarMessage.TYPE_SUCCESS));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private Automation saveCurrent() throws Exception
    {
        Automation saved = repository.saveAutomation(new Automation(selected == null ? null : selected.id(),
            nameText, description.getText(), true, RunMode.SINGLE,
            MissedTriggerPolicy.parse(missed.getText()), script.getText(), 100));
        savePrimaryTrigger(saved);
        selected = saved;
        loadAutomations(saved);
        savedState = currentState();
        updateDirtyState();
        return saved;
    }

    private void savePrimaryTrigger(Automation automation) throws Exception
    {
        String expr = scheduleExpression.trim();
        List<AutomationTrigger> triggers = repository.listTriggers(automation.id());
        boolean triggerActive = scheduleActive.getSelection();
        if (triggerType == null || triggerType.isBlank())
        {
            if (!triggers.isEmpty())
            {
                AutomationTrigger old = triggers.get(0);
                repository.saveTrigger(new AutomationTrigger(old.id(), automation.id(), old.name(), false,
                    old.type(), "", null, old.lastRun()));
            }
            return;
        }
        if (AutomationTriggerTypes.SYNC_AFTER.equals(triggerType)
            || AutomationTriggerTypes.TRANSACTION_NEW.equals(triggerType))
        {
            String triggerName = AutomationTriggerTypes.TRANSACTION_NEW.equals(triggerType)
                ? "Neuer Umsatz" : "Nach Synchronisierung";
            AutomationTrigger trigger = triggers.isEmpty()
                ? new AutomationTrigger(null, automation.id(), triggerName, triggerActive,
                    triggerType, "", null, null)
                : new AutomationTrigger(triggers.get(0).id(), automation.id(), triggerName,
                    triggerActive, triggerType, "", null, triggers.get(0).lastRun());
            repository.saveTrigger(trigger);
            return;
        }
        if (expr.isBlank())
        {
            if (!triggers.isEmpty())
            {
                AutomationTrigger old = triggers.get(0);
                repository.saveTrigger(new AutomationTrigger(old.id(), automation.id(), old.name(), false, old.type(),
                    "", null, old.lastRun()));
            }
            return;
        }
        new AutomationSchedule().validate(expr);
        AutomationTrigger trigger = triggers.isEmpty()
            ? new AutomationTrigger(null, automation.id(), "Zeitplan", triggerActive, AutomationTriggerTypes.CRON, expr,
                new AutomationSchedule().next(expr, java.time.LocalDateTime.now()), null)
            : new AutomationTrigger(triggers.get(0).id(), automation.id(), "Zeitplan", triggerActive,
                AutomationTriggerTypes.CRON, expr,
                new AutomationSchedule().next(expr, java.time.LocalDateTime.now()), triggers.get(0).lastRun());
        repository.saveTrigger(trigger);
    }

    private void run(boolean testRun)
    {
        try
        {
            if (hasUnsavedChanges() || selected == null || selected.id() == null)
            {
                Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                    "Bitte speichern Sie die Änderungen vor dem Ausführen.", StatusBarMessage.TYPE_INFO));
                return;
            }
            Automation current = repository.getAutomation(selected.id());
            AutomationService.get().runManual(current, testRun, () -> refreshHistoryAsync(true));
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                "Automation gestartet.", StatusBarMessage.TYPE_SUCCESS));
            refreshHistory();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void refreshHistoryAsync(boolean notifyFinished)
    {
        GUI.getDisplay().asyncExec(() -> {
            if (runsTable == null || runsTable.isDisposed())
                return;
            try
            {
                refreshHistory();
                if (notifyFinished)
                    notifyAutomationFinished();
            }
            catch (RuntimeException e)
            {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                GUI.getStatusBar().setErrorText(cause.getMessage());
            }
        });
    }

    private void notifyAutomationFinished()
    {
        AutomationRun run = runs.isEmpty() ? null : runs.get(0);
        if (run == null)
        {
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                "Automation beendet.", StatusBarMessage.TYPE_INFO));
            return;
        }

        String detail = hint(run);
        if (run.status() == RunStatus.FEHLGESCHLAGEN)
        {
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                message("Automation fehlgeschlagen.", detail), StatusBarMessage.TYPE_ERROR));
            return;
        }
        if (run.status() == RunStatus.ABGEBROCHEN)
        {
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                message("Automation abgebrochen.", detail), StatusBarMessage.TYPE_ERROR));
            return;
        }

        Application.getMessagingFactory().sendMessage(new StatusBarMessage(
            "Automation beendet.", StatusBarMessage.TYPE_INFO));
    }

    private void refreshHistoryFromTab()
    {
        try
        {
            refreshHistory();
        }
        catch (RuntimeException e)
        {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            GUI.getStatusBar().setErrorText(cause.getMessage());
        }
    }

    private void refreshHistory()
    {
        if (runsTable == null || runsTable.isDisposed())
            return;
        runsTable.removeAll();
        runLogs.setText("");
        showDecision(null);
        runs = List.of();
        if (selected == null || selected.id() == null)
            return;
        try
        {
            runs = repository.listRuns(selected.id(), 100);
            for (AutomationRun run : runs)
            {
                TableItem item = new TableItem(runsTable, SWT.NONE);
                item.setText(new String[] {
                    status(run.status()),
                    source(run),
                    format(run.startedAt()),
                    format(run.finishedAt()),
                    hint(run)
                });
            }
            if (!runs.isEmpty())
            {
                runsTable.select(0);
                showSelectedRunLogs();
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void showSelectedRunLogs()
    {
        int index = runsTable.getSelectionIndex();
        if (index < 0 || index >= runs.size())
        {
            runLogs.setText("");
            showDecision(null);
            return;
        }
        try
        {
            AutomationRun run = runs.get(index);
            StringBuilder text = new StringBuilder();
            for (AutomationLogEntry log : repository.listLogs(run.id()))
            {
                if (text.length() > 0)
                    text.append('\n');
                text.append(format(log.createdAt()))
                    .append(" [")
                    .append(log.level())
                    .append("] ")
                    .append(log.message());
            }
            runLogs.setText(text.toString());
            showDecision(openSupportedDecision(run));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private AutomationDecision openSupportedDecision(AutomationRun run) throws Exception
    {
        if (run.status() != RunStatus.WARTET)
            return null;
        for (AutomationDecision decision : repository.listOpenDecisions(run.id()))
        {
            if (supportedDecision(decision.type()))
                return decision;
        }
        return null;
    }

    private void showDecision(AutomationDecision decision)
    {
        selectedDecision = decision;
        boolean visible = decision != null;
        decisionText.setText(visible ? decisionLabel(decision) : "");
        catchUpDecision.setText("Jetzt nachholen");
        ignoreDecision.setText("Ignorieren");
        catchUpDecision.setEnabled(visible);
        ignoreDecision.setEnabled(visible);
        decisionArea.setVisible(visible);
        GridData data = (GridData) decisionArea.getLayoutData();
        data.exclude = !visible;
        decisionArea.getParent().layout(true, true);
    }

    private void acceptDecision()
    {
        if (selectedDecision == null || selected == null || selected.id() == null)
            return;
        int index = runsTable.getSelectionIndex();
        if (index < 0 || index >= runs.size())
            return;
        AutomationRun run = runs.get(index);
        try
        {
            acceptMissedTrigger(run);
            refreshHistory();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void rejectDecision()
    {
        if (selectedDecision == null)
            return;
        int index = runsTable.getSelectionIndex();
        if (index < 0 || index >= runs.size())
            return;
        AutomationRun run = runs.get(index);
        try
        {
            if ("missed-trigger".equals(selectedDecision.type()))
                ignoreMissedTrigger(run);
            refreshHistory();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void acceptMissedTrigger(AutomationRun run) throws Exception
    {
        repository.resolveDecision(selectedDecision.id(), "{\"action\":\"catch-up\"}");
        repository.addLog(run.id(), "info", "Verpasster Trigger wird jetzt nachgeholt.");
        repository.updateRunStatus(run.id(), RunStatus.UEBERSPRUNGEN,
            "Wartende Entscheidung wurde nachgeholt; siehe neuen Lauf.", "", true);
        Automation current = repository.getAutomation(selected.id());
        AutomationService.get().runMissed(current, () -> refreshHistoryAsync(true));
        Application.getMessagingFactory().sendMessage(new StatusBarMessage(
            "Automation wird nachgeholt.", StatusBarMessage.TYPE_INFO));
    }

    private void ignoreMissedTrigger(AutomationRun run) throws Exception
    {
        repository.resolveDecision(selectedDecision.id(), "{\"action\":\"ignore\"}");
        repository.addLog(run.id(), "info", "Verpasster Trigger wurde ignoriert.");
        repository.updateRunStatus(run.id(), RunStatus.UEBERSPRUNGEN,
            "Verpasster Trigger wurde ignoriert.", "", true);
        Application.getMessagingFactory().sendMessage(new StatusBarMessage(
            "Verpasster Trigger ignoriert.", StatusBarMessage.TYPE_INFO));
    }

    private static boolean supportedDecision(String type)
    {
        return "missed-trigger".equals(type);
    }

    private static String decisionLabel(AutomationDecision decision)
    {
        if ("missed-trigger".equals(decision.type()))
            return "Verpasster Zeittrigger wartet auf Entscheidung.";
        return "Entscheidung wartet.";
    }

    private static String status(RunStatus status)
    {
        return switch (status)
        {
            case GEPLANT -> "geplant";
            case LAEUFT -> "laeuft";
            case WARTET -> "wartet";
            case UEBERSPRUNGEN -> "uebersprungen";
            case ERFOLGREICH -> "erfolgreich";
            case FEHLGESCHLAGEN -> "fehlgeschlagen";
            case ABGEBROCHEN -> "abgebrochen";
            case TESTLAUF -> "testlauf";
        };
    }

    private static String source(AutomationRun run)
    {
        return run.testRun() ? "testlauf" : run.source();
    }

    private static String hint(AutomationRun run)
    {
        if (!run.error().isBlank())
            return run.error();
        if (!run.warning().isBlank())
            return run.warning();
        return "";
    }

    private static String message(String text, String detail)
    {
        if (detail == null || detail.isBlank())
            return text;
        return text + " " + detail;
    }

    private static String format(LocalDateTime value)
    {
        return value == null ? "" : DATE_TIME.format(value);
    }

    private static String exportFileName(String name)
    {
        String safe = name == null ? "automation" : name.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        if (safe.isBlank())
            safe = "automation";
        return withJsonExtension(safe);
    }

    private static String withJsonExtension(String path)
    {
        return path.toLowerCase().endsWith(".json") ? path : path + ".json";
    }

    private static Automation emptyAutomation()
    {
        return new Automation(null, "Neue Automation", "", true, RunMode.SINGLE,
            MissedTriggerPolicy.IGNORIEREN, "", 100);
    }

    private static final class UnsavedChangesDialog extends AbstractDialog<UnsavedDecision>
    {
        private UnsavedDecision result = UnsavedDecision.CANCEL;

        private UnsavedChangesDialog()
        {
            super(POSITION_CENTER);
            setTitle("Ungespeicherte Änderungen");
            setSize(560, 180);
        }

        @Override
        protected void paint(Composite parent) throws Exception
        {
            parent.setLayout(new GridLayout(1, false));

            Label text = new Label(parent, SWT.WRAP);
            text.setText("Es gibt ungespeicherte Änderungen. Was soll mit diesen Änderungen passieren?");
            text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

            ButtonArea buttons = new ButtonArea();
            buttons.addButton("Speichern", new Action()
            {
                @Override
                public void handleAction(Object context) throws ApplicationException
                {
                    result = UnsavedDecision.SAVE;
                    close();
                }
            }, null, true, "ok.png");
            buttons.addButton("Verwerfen", new Action()
            {
                @Override
                public void handleAction(Object context) throws ApplicationException
                {
                    result = UnsavedDecision.DISCARD;
                    close();
                }
            }, null, false, "edit-delete.png");
            buttons.addButton("Abbrechen", new Action()
            {
                @Override
                public void handleAction(Object context) throws ApplicationException
                {
                    result = UnsavedDecision.CANCEL;
                    close();
                }
            }, null, false, "process-stop.png");
            buttons.paint(parent);
            getShell().setMinimumSize(560, 180);
        }

        @Override
        protected UnsavedDecision getData()
        {
            return result;
        }
    }
}
