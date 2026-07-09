package de.open4me.hibiscus.reports.ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import de.open4me.hibiscus.reports.data.DynamicReportRenderer;
import de.open4me.hibiscus.reports.data.DynamicReportRepository;
import de.open4me.hibiscus.reports.data.HibiscusReportAccountProvider;
import de.open4me.hibiscus.reports.data.HibiscusReportTransactionProvider;
import de.open4me.hibiscus.reports.data.ReportTransactionProvider;
import de.open4me.hibiscus.reports.model.DynamicReport;
import de.willuhn.jameica.gui.AbstractView;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.dialogs.TextDialog;
import de.willuhn.jameica.gui.parts.PanelButton;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.OperationCanceledException;
import de.willuhn.jameica.system.Settings;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;

public class DynamicReportsView extends AbstractView
{
    private static final Settings EXPORT_SETTINGS = new Settings(DynamicReportsView.class);

    private DynamicReportRepository repository;
    private DynamicReportRenderer renderer;
    private List<DynamicReport> reports = List.of();
    private DynamicReport selectedReport;

    private Composite root;
    private Combo reportCombo;
    private Button editButton;
    private Button updateButton;
    private Button saveButton;
    private PanelButton reloadPanelButton;
    private PanelButton saveHtmlPanelButton;
    private PanelButton printPanelButton;
    private Composite content;
    private Browser browser;
    private Text browserFallback;
    private HtmlTemplateEditor editor;
    private Text errors;
    private boolean editMode;
    private boolean selectingReport;
    private String currentTemplate = "";
    private String savedTemplate = "";
    private String lastRenderedHtml = "";

    @Override
    public void bind() throws Exception
    {
        GUI.getView().setTitle("Reports");
        repository = DynamicReportRepository.jameica();
        repository.initialize();
        ReportTransactionProvider transactionProvider = new HibiscusReportTransactionProvider();
        renderer = new DynamicReportRenderer(repository.baseDirectory(),
            new HibiscusReportAccountProvider(transactionProvider), transactionProvider);
        addReloadPanelButton();
        addSaveHtmlPanelButton();
        addPrintPanelButton();
        addHelpPanelButton();

        root = new Composite(getParent(), SWT.NONE);
        root.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        root.setLayout(new GridLayout(1, false));

        createToolbar(root);
        content = new Composite(root, SWT.NONE);
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        errors = new Text(root, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.READ_ONLY);
        errors.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        ((GridData) errors.getLayoutData()).heightHint = 90;

        DynamicReport reportToSelect = getCurrentObject() instanceof DynamicReport report ? report : null;
        loadReports(reportToSelect);
        createContent();
        loadSelectedReport();
    }

    private void createToolbar(Composite parent)
    {
        Composite toolbar = new Composite(parent, SWT.NONE);
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        toolbar.setLayout(new GridLayout(6, false));

        new Label(toolbar, SWT.NONE).setText("Report:");
        reportCombo = new Combo(toolbar, SWT.DROP_DOWN | SWT.READ_ONLY);
        reportCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        reportCombo.addListener(SWT.Selection, event -> selectReportFromCombo());

        button(toolbar, "Neu", this::createNewReport);
        editButton = button(toolbar, "Bearbeiten", this::toggleEditMode);
        saveButton = button(toolbar, "Speichern", this::saveReport);
        updateButton = button(toolbar, "Vorschau aktualisieren", this::renderEditor);
        updateButtons();
    }

    private void addReloadPanelButton()
    {
        reloadPanelButton = new PanelButton("view-refresh.png", new Action()
        {
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                reloadPreview();
            }
        }, "Report-Vorschau neu laden");
        GUI.getView().addPanelButton(reloadPanelButton);
    }

    private void addSaveHtmlPanelButton()
    {
        saveHtmlPanelButton = new PanelButton("document-save.png", new Action()
        {
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                saveRenderedHtml();
            }
        }, "Gerendertes HTML speichern...");
        GUI.getView().addPanelButton(saveHtmlPanelButton);
    }

    private void addPrintPanelButton()
    {
        printPanelButton = new PanelButton("document-print.png", new Action()
        {
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                printPreview();
            }
        }, "Drucken / als PDF speichern...");
        GUI.getView().addPanelButton(printPanelButton);
    }

    private void addHelpPanelButton()
    {
        PanelButton button = new PanelButton("dialog-information.png", new Action()
        {
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                openHelp();
            }
        }, "Report-Objekte anzeigen");
        GUI.getView().addPanelButton(button);
    }

    private void loadReports()
    {
        loadReports(selectedReport);
    }

    private void loadReports(DynamicReport reportToSelect)
    {
        try
        {
            reports = repository.listReports();
            reportCombo.removeAll();
            for (DynamicReport report : reports)
                reportCombo.add(report.displayName());
            int selection = selectedIndex(reportToSelect);
            if (selection < 0 && !reports.isEmpty())
                selection = 0;
            if (selection >= 0)
                reportCombo.select(selection);
            updateButtons();
        }
        catch (Exception e)
        {
            showError("Reports konnten nicht geladen werden", e);
        }
    }

    private void reloadPreview()
    {
        if (editMode)
        {
            renderEditor();
            return;
        }
        reloadSelectedReport();
    }

    private void saveRenderedHtml() throws ApplicationException
    {
        if (lastRenderedHtml == null || lastRenderedHtml.isBlank())
            throw new ApplicationException("Es ist keine Report-Vorschau zum Speichern vorhanden.");

        FileDialog dialog = new FileDialog(GUI.getShell(), SWT.SAVE);
        dialog.setText("Gerendertes HTML speichern");
        dialog.setFilterNames(new String[] { "HTML-Datei (*.html)" });
        dialog.setFilterExtensions(new String[] { "*.html" });
        dialog.setOverwrite(false);
        dialog.setFilterPath(EXPORT_SETTINGS.getString("lastdir", System.getProperty("user.home")));
        dialog.setFileName(defaultHtmlFileName());

        String selected = dialog.open();
        if (selected == null || selected.isBlank())
            return;
        selected = ExportFileNames.withExtension(selected, ".html");
        File file = new File(selected);
        try
        {
            if (file.exists() && !Application.getCallback().askUser(
                "Datei \"" + file.getName() + "\" existiert bereits. Überschreiben?"))
                return;
            Files.writeString(Path.of(selected), lastRenderedHtml, StandardCharsets.UTF_8);
            EXPORT_SETTINGS.setAttribute("lastdir", file.getParent());
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                "Report-HTML gespeichert: " + selected, StatusBarMessage.TYPE_SUCCESS));
        }
        catch (ApplicationException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ApplicationException("Report-HTML konnte nicht gespeichert werden: " + e.getMessage(), e);
        }
    }

    private void printPreview() throws ApplicationException
    {
        if (browser == null || browser.isDisposed())
            throw new ApplicationException("Es ist keine druckbare Browser-Vorschau vorhanden.");
        try
        {
            if (!browser.execute("window.print();"))
                throw new ApplicationException("Der Druckdialog konnte nicht geöffnet werden.");
        }
        catch (ApplicationException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ApplicationException("Der Druckdialog konnte nicht geöffnet werden: " + e.getMessage(), e);
        }
    }

    private void selectReportFromCombo()
    {
        if (selectingReport)
            return;
        if (!confirmDiscardChanges())
        {
            selectReport(selectedReport);
            return;
        }
        loadSelectedReport();
    }

    private void loadSelectedReport()
    {
        int index = reportCombo.getSelectionIndex();
        if (index < 0 || index >= reports.size())
            return;
        selectedReport = reports.get(index);
        reloadSelectedReport();
    }

    private void reloadSelectedReport()
    {
        if (selectedReport == null)
            return;
        try
        {
            savedTemplate = repository.read(selectedReport);
            currentTemplate = savedTemplate;
            if (editor != null && !editor.isDisposed())
                editor.setText(currentTemplate);
            render(currentTemplate);
            updateButtons();
        }
        catch (Exception e)
        {
            showError("Report konnte nicht gelesen werden", e);
        }
    }

    private void toggleEditMode()
    {
        if (editMode && !confirmDiscardChanges())
            return;
        editMode = !editMode;
        editButton.setText(editMode ? "Schließen" : "Bearbeiten");
        createContent();
        if (editor != null)
            editor.setText(currentTemplate);
        render(currentTemplate);
        updateButtons();
        root.layout(true, true);
    }

    private void renderEditor()
    {
        if (editor == null || editor.isDisposed())
            return;
        currentTemplate = editor.getText();
        render(currentTemplate);
        updateButtons();
    }

    private void saveReport()
    {
        if (selectedReport == null || editor == null || editor.isDisposed())
            return;
        try
        {
            currentTemplate = editor.getText();
            repository.write(selectedReport, currentTemplate);
            savedTemplate = currentTemplate;
            render(currentTemplate);
            updateButtons();
        }
        catch (Exception e)
        {
            showError("Report konnte nicht gespeichert werden", e);
        }
    }

    private void createNewReport()
    {
        if (!confirmDiscardChanges())
            return;
        try
        {
            TextDialog dialog = new TextDialog(AbstractDialog.POSITION_CENTER);
            dialog.setTitle("Neuen Report anlegen");
            dialog.setText("Bitte einen Namen relativ zu reports/ eingeben, zum Beispiel konten oder gruppe/salden.");
            dialog.setLabelText("Reportname");
            Object value = dialog.open();
            if (value == null || value.toString().isBlank())
                return;

            DynamicReport report = repository.createReport(value.toString());
            selectedReport = report;
            loadReports(report);
            editMode = true;
            editButton.setText("Schließen");
            createContent();
            loadSelectedReport();
            ReportsNavigationRefresher.refresh();
            root.layout(true, true);
        }
        catch (OperationCanceledException e)
        {
            // Dialog was cancelled.
        }
        catch (Exception e)
        {
            showError("Report konnte nicht angelegt werden", e);
        }
    }

    private void openHelp()
    {
        try
        {
            new ReportObjectsHelpDialog().open();
        }
        catch (OperationCanceledException e)
        {
            // Dialog was closed.
        }
        catch (Exception e)
        {
            showError("Report-Objekte konnten nicht angezeigt werden", e);
        }
    }

    private void createContent()
    {
        for (Control child : content.getChildren())
            child.dispose();
        browser = null;
        browserFallback = null;
        editor = null;

        if (editMode)
        {
            content.setLayout(new FillLayout());
            SashForm sash = new SashForm(content, SWT.HORIZONTAL);
            createPreview(sash);
            editor = new HtmlTemplateEditor(sash, event -> updateButtons());
            sash.setWeights(new int[] { 55, 45 });
        }
        else
        {
            content.setLayout(new FillLayout());
            createPreview(content);
        }
    }

    private void createPreview(Composite parent)
    {
        try
        {
            browser = new Browser(parent, SWT.BORDER);
        }
        catch (SWTError e)
        {
            Logger.warn("unable to create SWT browser, falling back to text preview: " + e.getMessage());
            browserFallback = new Text(parent, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.READ_ONLY);
            browserFallback.setText("HTML-Komponente konnte nicht initialisiert werden.");
        }
    }

    private void render(String template)
    {
        try
        {
            DynamicReportRenderer.RenderedReport rendered = renderer.render(template);
            lastRenderedHtml = rendered.html();
            setPreview(rendered.html());
            setErrors(rendered.errors());
            updateButtons();
        }
        catch (Exception e)
        {
            showError("Report konnte nicht gerendert werden", e);
        }
    }

    private void setPreview(String html)
    {
        if (browser != null && !browser.isDisposed())
            browser.setText(html == null ? "" : html);
        else if (browserFallback != null && !browserFallback.isDisposed())
            browserFallback.setText(html == null ? "" : html);
    }

    private void setErrors(List<String> messages)
    {
        if (messages == null || messages.isEmpty())
        {
            errors.setText("");
            return;
        }
        errors.setText(messages.stream().filter(message -> message != null && !message.isBlank())
            .collect(Collectors.joining(System.lineSeparator())));
    }

    private void showError(String message, Throwable error)
    {
        Logger.error(message, error);
        errors.setText(message + ": " + (error.getMessage() == null ? error.getClass().getName() : error.getMessage()));
    }

    private void updateButtons()
    {
        boolean haveReport = selectedReport != null;
        if (editButton != null && !editButton.isDisposed())
            editButton.setEnabled(haveReport);
        if (updateButton != null && !updateButton.isDisposed())
            updateButton.setEnabled(editMode && haveReport);
        if (saveButton != null && !saveButton.isDisposed())
            saveButton.setEnabled(editMode && haveReport && hasUnsavedChanges());
        if (reloadPanelButton != null)
            reloadPanelButton.setEnabled(haveReport);
        if (saveHtmlPanelButton != null)
            saveHtmlPanelButton.setEnabled(lastRenderedHtml != null && !lastRenderedHtml.isBlank());
        if (printPanelButton != null)
            printPanelButton.setEnabled(browser != null && !browser.isDisposed()
                && lastRenderedHtml != null && !lastRenderedHtml.isBlank());
    }

    private boolean hasUnsavedChanges()
    {
        return editor != null && !editor.isDisposed() && !editor.getText().equals(savedTemplate);
    }

    private boolean confirmDiscardChanges()
    {
        if (!hasUnsavedChanges())
            return true;
        try
        {
            return Application.getCallback().askUser("Es gibt ungespeicherte Änderungen. Sollen sie verworfen werden?");
        }
        catch (Exception e)
        {
            showError("Rückfrage konnte nicht angezeigt werden", e);
            return false;
        }
    }

    private int selectedIndex(DynamicReport report)
    {
        if (report == null)
            return -1;
        for (int i = 0; i < reports.size(); i++)
        {
            if (reports.get(i).path().equals(report.path()))
                return i;
        }
        return -1;
    }

    private void selectReport(DynamicReport report)
    {
        selectingReport = true;
        try
        {
            int index = selectedIndex(report);
            if (index >= 0)
                reportCombo.select(index);
            else
                reportCombo.deselectAll();
        }
        finally
        {
            selectingReport = false;
        }
    }

    private static Button button(Composite parent, String text, Runnable action)
    {
        Button button = new Button(parent, SWT.PUSH);
        button.setText(text);
        button.addListener(SWT.Selection, event -> action.run());
        return button;
    }

    private String defaultHtmlFileName()
    {
        String name = selectedReport == null ? "report" : selectedReport.displayName();
        name = name.replace('\\', '-').replace('/', '-').toLowerCase(Locale.ROOT);
        name = name.replaceAll("[^a-z0-9._-]+", "-").replaceAll("-+", "-");
        if (name.isBlank() || "-".equals(name))
            name = "report";
        return ExportFileNames.withExtension(name, ".html");
    }
}
