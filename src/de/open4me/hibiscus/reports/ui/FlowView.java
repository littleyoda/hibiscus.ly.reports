package de.open4me.hibiscus.reports.ui;

import java.time.ZoneId;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TabFolder;

import de.open4me.hibiscus.reports.data.HibiscusDataProvider;
import de.open4me.hibiscus.reports.data.SankeyGraphBuilder;
import de.open4me.hibiscus.reports.model.AccountInfo;
import de.open4me.hibiscus.reports.model.FlowReport;
import de.open4me.hibiscus.reports.model.SankeyGraph;
import de.willuhn.jameica.gui.AbstractView;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.input.Input;
import de.willuhn.jameica.gui.input.MultiInput;
import de.willuhn.jameica.gui.parts.PanelButton;
import de.willuhn.jameica.gui.util.ColumnLayout;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.jameica.gui.util.SWTUtil;
import de.willuhn.jameica.gui.util.TabGroup;
import de.willuhn.jameica.hbci.gui.input.DateFromInput;
import de.willuhn.jameica.hbci.gui.input.DateToInput;
import de.willuhn.jameica.hbci.gui.input.RangeInput;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.hbci.server.Range.Category;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.BackgroundTask;
import de.willuhn.jameica.system.OperationCanceledException;
import de.willuhn.jameica.system.Settings;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.ProgressMonitor;

public class FlowView extends AbstractView
{
    private static final Settings SETTINGS = new Settings(FlowView.class);
    private final HibiscusDataProvider provider = new HibiscusDataProvider();
    private final SankeyGraphBuilder graphBuilder = new SankeyGraphBuilder();
    private final Set<String> expanded = new HashSet<>();
    private final AtomicLong loadGeneration = new AtomicLong();

    private Composite root;
    private Input fromDate;
    private Input toDate;
    private RangeInput range;
    private Button refresh;
    private Label status;
    private ScrolledComposite chartScroller;
    private SankeyCanvas canvas;
    private Button zoomIn;
    private Button zoomOut;
    private FlowReport report;
    private SankeyGraph currentGraph;
    private double appliedThreshold;
    private int thresholdTenths;
    private LocalDate displayedFrom;
    private LocalDate displayedTo;
    private List<AccountInfo> accountList = List.of();
    private final Set<String> excludedAccountIds = new HashSet<>();

    @Override
    public void bind() throws Exception
    {
        GUI.getView().setTitle("Geldfluss");
        root = new Composite(getParent(), SWT.NONE);
        root.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        root.setLayout(new GridLayout(1, false));

        createPeriodFilter(root);
        accountList = provider.loadAccounts();
        excludedAccountIds.addAll(AccountSelectionSettings.load(SETTINGS, accountList, true));
        thresholdTenths = SETTINGS.getInt("threshold.tenths", 20);
        addAccountPanelButton();
        addExportPanelButton();

        status = new Label(root, SWT.WRAP);
        status.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        chartScroller = new ScrolledComposite(root, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
        chartScroller.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        chartScroller.setExpandHorizontal(true);
        chartScroller.setExpandVertical(true);
        canvas = new SankeyCanvas(chartScroller, SWT.NONE);
        canvas.setCategoryToggle(this::toggleCategory);
        canvas.setTransactionOpen(this::openTransactions);
        chartScroller.setContent(canvas);
        createZoomControls(root);

        root.layout(true, true);
        loadReport();
    }

    private void createPeriodFilter(Composite parent)
    {
        LocalDate endDefault = YearMonth.now().minusMonths(1).atEndOfMonth();
        LocalDate startDefault = YearMonth.from(endDefault).minusMonths(11).atDay(1);
        fromDate = new DateFromInput(toDate(readDate("period.from", startDefault, false)),
            "hibiscus.ly.reports.filter.from");
        fromDate.setName("Von");
        fromDate.setComment(null);
        toDate = new DateToInput(toDate(readDate("period.to", endDefault, true)),
            "hibiscus.ly.reports.filter.to");
        toDate.setName("bis");
        toDate.setComment(null);
        range = new RangeInput(fromDate, toDate, Category.AUSWERTUNG, "hibiscus.ly.reports.filter.range");

        TabFolder folder = new TabFolder(parent, SWT.NONE);
        folder.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        TabGroup tab = new TabGroup(folder, "Anzeige einschränken");
        ColumnLayout columns = new ColumnLayout(tab.getComposite(), 2);
        Container left = new SimpleContainer(columns.getComposite());
        left.addInput(range);
        Container right = new SimpleContainer(columns.getComposite());
        MultiInput dates = new MultiInput(fromDate, toDate);
        right.addInput(dates);

        Composite dateRow = (Composite) dates.getControl();
        GridLayout dateLayout = (GridLayout) dateRow.getLayout();
        dateLayout.numColumns++;
        dateRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        refresh = button(dateRow, "Aktualisieren", this::loadReport);
        refresh.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
    }

    private void addAccountPanelButton()
    {
        PanelButton button = new PanelButton("document-properties.png", new Action()
        {
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                openAccountDialog();
            }
        }, "Einstellungen...");
        GUI.getView().addPanelButton(button);
    }

    private void addExportPanelButton()
    {
        PanelButton button = new PanelButton("document-save.png", new Action()
        {
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                FlowExportService.export(currentGraph, displayedFrom, displayedTo);
            }
        }, "Geldflussgrafik exportieren...");
        GUI.getView().addPanelButton(button);
    }

    private void createZoomControls(Composite parent)
    {
        Composite controls = new Composite(parent, SWT.NONE);
        controls.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        GridLayout layout = new GridLayout(2, true);
        layout.marginHeight = 0;
        layout.marginWidth = 0;
        controls.setLayout(layout);

        zoomOut = iconButton(controls, "list-remove.png", "Verkleinern (Zoom-Out)", () -> changeZoom(-10));
        zoomIn = iconButton(controls, "list-add.png", "Vergrößern (Zoom-In)", () -> changeZoom(10));
        updateZoomButtons();
    }

    private void loadReport()
    {
        LocalDate requestedFrom = selectedDate(fromDate);
        LocalDate requestedTo = selectedDate(toDate);
        if (requestedFrom != null && requestedTo != null && requestedTo.isBefore(requestedFrom))
        {
            status.setText("Das Enddatum muss am oder nach dem Startdatum liegen.");
            return;
        }
        Set<String> accountIds = selectedAccountIds();
        if (accountIds.isEmpty())
        {
            status.setText("Bitte mindestens ein EUR-Konto auswählen.");
            return;
        }

        double thresholdPercent = thresholdTenths / 10d;
        persist(requestedFrom, requestedTo);
        status.setText("Daten werden geladen …");
        refresh.setEnabled(false);
        long generation = loadGeneration.incrementAndGet();

        Application.getController().start(new BackgroundTask()
        {
            private volatile boolean interrupted;

            @Override
            public void run(ProgressMonitor monitor) throws ApplicationException
            {
                try
                {
                    LocalDate effectiveTo = requestedTo != null ? requestedTo : LocalDate.now();
                    LocalDate effectiveFrom = requestedFrom != null ? requestedFrom : provider.findOldestDate(accountIds);
                    if (effectiveFrom == null)
                        effectiveFrom = effectiveTo;
                    FlowReport loaded = provider.loadReport(accountIds, effectiveFrom, effectiveTo);
                    if (interrupted)
                        return;
                    LocalDate displayFrom = effectiveFrom;
                    LocalDate displayTo = effectiveTo;
                    GUI.getDisplay().asyncExec(() -> applyReport(generation, loaded, thresholdPercent,
                        displayFrom, displayTo));
                }
                catch (Exception e)
                {
                    GUI.getDisplay().asyncExec(() -> showError(generation, e));
                }
            }

            @Override
            public void interrupt()
            {
                interrupted = true;
            }

            @Override
            public boolean isInterrupted()
            {
                return interrupted;
            }
        });
    }

    private void applyReport(long generation, FlowReport loaded, double thresholdPercent,
                             LocalDate from, LocalDate to)
    {
        if (root == null || root.isDisposed() || generation != loadGeneration.get())
            return;
        report = loaded;
        appliedThreshold = thresholdPercent;
        displayedFrom = from;
        displayedTo = to;
        refresh.setEnabled(true);
        status.setText(loaded.incomes().isEmpty() && loaded.expenses().isEmpty()
            ? "Keine kategorisierten Umsätze im gewählten Zeitraum."
            : "");
        rebuildGraph();
        root.layout(true, true);
    }

    private void showError(long generation, Exception error)
    {
        if (root == null || root.isDisposed() || generation != loadGeneration.get())
            return;
        refresh.setEnabled(true);
        status.setText(errorText("Fehler beim Laden der Geldflussdaten", error));
    }

    private void rebuildGraph()
    {
        if (report == null)
            return;
        currentGraph = graphBuilder.build(report, expanded, appliedThreshold);
        canvas.setGraph(currentGraph);
        resizeCanvas();
    }

    private void changeZoom(int delta)
    {
        int oldZoom = canvas.getZoomPercent();
        canvas.setZoomPercent(oldZoom + delta);
        resizeCanvas();
        updateZoomButtons();
    }

    private void resizeCanvas()
    {
        var size = canvas.preferredSize();
        canvas.setSize(size);
        chartScroller.setMinSize(size);
        chartScroller.layout(true, true);
    }

    private void updateZoomButtons()
    {
        int zoom = canvas.getZoomPercent();
        zoomOut.setEnabled(zoom > 50);
        zoomIn.setEnabled(zoom < 200);
    }

    private void toggleCategory(String key)
    {
        if (!expanded.add(key))
            expanded.remove(key);
        rebuildGraph();
    }

    private void openTransactions(SankeyGraph.Node node)
    {
        if (node == null || node.transactionFilter() == null || displayedFrom == null || displayedTo == null)
        {
            Logger.warn("unable to open flow transactions: missing node, transaction filter or displayed period");
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                "Für diesen Block können keine Umsätze geöffnet werden.", StatusBarMessage.TYPE_INFO));
            return;
        }

        Logger.info("opening flow transactions for " + node.name() + " from " + displayedFrom + " to "
            + displayedTo);
        Application.getMessagingFactory().sendMessage(new StatusBarMessage(
            "Umsätze werden geöffnet...", StatusBarMessage.TYPE_INFO));
        GUI.startView(FlowTransactionsView.class, new FlowTransactionSelection("Umsätze – " + node.name(),
            displayedFrom, displayedTo, selectedAccountIds(), node.transactionFilter()));
    }

    private Set<String> selectedAccountIds()
    {
        Set<String> result = new LinkedHashSet<>();
        for (AccountInfo account : accountList)
        {
            if (account.isEuro() && !excludedAccountIds.contains(account.id()))
                result.add(account.id());
        }
        return result;
    }

    private void openAccountDialog()
    {
        try
        {
            AccountFilterDialog.Result result = new AccountFilterDialog(accountList, excludedAccountIds,
                provider.loadExcludedCategories(), thresholdTenths).open();
            if (result == null)
                return;
            excludedAccountIds.clear();
            excludedAccountIds.addAll(result.excludedAccountIds());
            thresholdTenths = result.thresholdTenths();
            AccountSelectionSettings.save(SETTINGS, excludedAccountIds);
            SETTINGS.setAttribute("threshold.tenths", thresholdTenths);
        }
        catch (OperationCanceledException e)
        {
            // Benutzer hat den Dialog abgebrochen. Das ist kein Fehler.
        }
        catch (Exception e)
        {
            Logger.error("unable to open flow report settings", e);
            if (status != null && !status.isDisposed())
                status.setText(errorText("Einstellungen konnten nicht geöffnet werden", e));
        }
    }

    private static String errorText(String prefix, Throwable error)
    {
        String detail = error == null ? null : error.getLocalizedMessage();
        return detail == null || detail.isBlank() ? prefix + "." : prefix + ": " + detail;
    }

    private void persist(LocalDate from, LocalDate to)
    {
        SETTINGS.setAttribute("period.from", from == null ? null : from.toString());
        SETTINGS.setAttribute("period.to", to == null ? null : to.toString());
        AccountSelectionSettings.save(SETTINGS, excludedAccountIds);
        SETTINGS.setAttribute("threshold.tenths", thresholdTenths);
    }

    private static LocalDate selectedDate(Input input)
    {
        Object value = input.getValue();
        if (!(value instanceof Date date))
            return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static LocalDate readDate(String key, LocalDate fallback, boolean endOfMonth)
    {
        String stored = SETTINGS.getString(key, fallback.toString());
        try
        {
            return LocalDate.parse(stored);
        }
        catch (DateTimeParseException e)
        {
            try
            {
                YearMonth oldMonth = YearMonth.parse(stored);
                return endOfMonth ? oldMonth.atEndOfMonth() : oldMonth.atDay(1);
            }
            catch (DateTimeParseException ignored)
            {
                return fallback;
            }
        }
    }

    private static Date toDate(LocalDate date)
    {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Button button(Composite parent, String text, Runnable action)
    {
        Button button = new Button(parent, SWT.PUSH);
        button.setText(text);
        button.addListener(SWT.Selection, event -> action.run());
        return button;
    }

    private static Button iconButton(Composite parent, String icon, String tooltip, Runnable action)
    {
        Button button = new Button(parent, SWT.PUSH);
        button.setImage(SWTUtil.getImage(icon));
        button.setToolTipText(tooltip);
        button.addListener(SWT.Selection, event -> action.run());
        return button;
    }
}
