package de.open4me.hibiscus.reports.ui;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.TabFolder;

import de.open4me.hibiscus.reports.data.HibiscusDataProvider;
import de.open4me.hibiscus.reports.data.PeriodBalanceAggregator;
import de.open4me.hibiscus.reports.model.AccountInfo;
import de.open4me.hibiscus.reports.model.AggregationInterval;
import de.open4me.hibiscus.reports.model.PeriodBalance;
import de.willuhn.jameica.gui.AbstractView;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.input.Input;
import de.willuhn.jameica.gui.input.MultiInput;
import de.willuhn.jameica.gui.parts.PanelButton;
import de.willuhn.jameica.gui.util.ColumnLayout;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.jameica.gui.util.TabGroup;
import de.willuhn.jameica.hbci.gui.input.DateFromInput;
import de.willuhn.jameica.hbci.gui.input.DateToInput;
import de.willuhn.jameica.hbci.gui.input.RangeInput;
import de.willuhn.jameica.hbci.server.Range.Category;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.BackgroundTask;
import de.willuhn.jameica.system.OperationCanceledException;
import de.willuhn.jameica.system.Settings;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.ProgressMonitor;

public class MonthlyOverviewView extends AbstractView
{
    private static final Settings SETTINGS = new Settings(MonthlyOverviewView.class);
    private final HibiscusDataProvider provider = new HibiscusDataProvider();
    private final PeriodBalanceAggregator aggregator = new PeriodBalanceAggregator();
    private final AtomicLong generation = new AtomicLong();
    private final Set<String> excludedAccountIds = new HashSet<>();

    private Composite root;
    private Input fromDate;
    private Input toDate;
    private Combo intervalCombo;
    private Button refresh;
    private Label status;
    private Table summary;
    private ScrolledComposite chartScroller;
    private BalanceChartCanvas canvas;
    private List<AccountInfo> accounts = List.of();
    private List<PeriodBalance> periods = List.of();
    private LocalDate displayedFrom;
    private LocalDate displayedTo;
    private AggregationInterval displayedInterval = AggregationInterval.MONTHLY;

    @Override
    public void bind() throws Exception
    {
        GUI.getView().setTitle("Monatsübersicht");
        root = new Composite(getParent(), SWT.NONE);
        root.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        root.setLayout(new GridLayout(1, false));

        createFilter(root);
        accounts = provider.loadAccounts();
        excludedAccountIds.addAll(AccountSelectionSettings.load(SETTINGS, accounts, false));
        addSettingsButton();
        addExportButton();
        createSummary(root);
        status = new Label(root, SWT.WRAP);
        status.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        chartScroller = new ScrolledComposite(root, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
        chartScroller.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        chartScroller.setExpandHorizontal(true);
        chartScroller.setExpandVertical(true);
        canvas = new BalanceChartCanvas(chartScroller, SWT.NONE);
        canvas.setTransactionOpen(this::openTransactions);
        chartScroller.setContent(canvas);

        root.layout(true, true);
        loadReport();
    }

    private void createFilter(Composite parent)
    {
        LocalDate endDefault = YearMonth.now().minusMonths(1).atEndOfMonth();
        LocalDate startDefault = YearMonth.from(endDefault).minusMonths(11).atDay(1);
        fromDate = new DateFromInput(toDate(readDate("period.from", startDefault)),
            "hibiscus.ly.reports.overview.filter.from");
        fromDate.setName("Von");
        fromDate.setComment(null);
        toDate = new DateToInput(toDate(readDate("period.to", endDefault)),
            "hibiscus.ly.reports.overview.filter.to");
        toDate.setName("bis");
        toDate.setComment(null);
        RangeInput range = new RangeInput(fromDate, toDate, Category.AUSWERTUNG,
            "hibiscus.ly.reports.overview.filter.range");

        TabFolder folder = new TabFolder(parent, SWT.NONE);
        folder.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        TabGroup tab = new TabGroup(folder, "Anzeige einschränken");
        ColumnLayout columns = new ColumnLayout(tab.getComposite(), 2);
        Container left = new SimpleContainer(columns.getComposite());
        left.addInput(range);
        Container right = new SimpleContainer(columns.getComposite());
        MultiInput dates = new MultiInput(fromDate, toDate);
        right.addInput(dates);

        Composite row = (Composite) dates.getControl();
        GridLayout layout = (GridLayout) row.getLayout();
        layout.numColumns += 3;
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        new Label(row, SWT.NONE).setText("Gruppierung:");
        intervalCombo = new Combo(row, SWT.DROP_DOWN | SWT.READ_ONLY);
        for (AggregationInterval interval : AggregationInterval.values())
            intervalCombo.add(interval.toString());
        AggregationInterval stored = readInterval();
        intervalCombo.select(stored.ordinal());
        refresh = button(row, "Aktualisieren", this::loadReport);
    }

    private void createSummary(Composite parent)
    {
        summary = new Table(parent, SWT.BORDER | SWT.FULL_SELECTION);
        summary.setHeaderVisible(true);
        summary.setLinesVisible(true);
        summary.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
        column(summary, "", 130);
        column(summary, "Gesamt", 170);
        column(summary, "pro Monat", 170);
        column(summary, "", 0);
        for (String label : new String[] { "Einnahmen", "Ausgaben", "Bilanz" })
            new TableItem(summary, SWT.NONE).setText(0, label);
    }

    private void loadReport()
    {
        LocalDate from = selectedDate(fromDate);
        LocalDate to = selectedDate(toDate);
        if (from == null || to == null)
        {
            status.setText("Bitte einen vollständigen Zeitraum auswählen.");
            return;
        }
        if (to.isBefore(from))
        {
            status.setText("Das Enddatum muss am oder nach dem Startdatum liegen.");
            return;
        }
        if (to.isAfter(from.plusYears(30)))
        {
            status.setText("Der Zeitraum darf höchstens 30 Jahre umfassen.");
            return;
        }
        Set<String> accountIds = selectedAccountIds();
        if (accountIds.isEmpty())
        {
            status.setText("Bitte mindestens ein EUR-Konto auswählen.");
            return;
        }
        AggregationInterval interval = selectedInterval();
        persist(from, to, interval);
        status.setText("Daten werden geladen …");
        refresh.setEnabled(false);
        long current = generation.incrementAndGet();

        Application.getController().start(new BackgroundTask()
        {
            private volatile boolean interrupted;

            @Override
            public void run(ProgressMonitor monitor) throws ApplicationException
            {
                try
                {
                    var bookings = provider.loadDatedBookings(accountIds, from, to);
                    var loaded = aggregator.aggregate(bookings, from, to, interval);
                    if (!interrupted)
                        GUI.getDisplay().asyncExec(() -> applyReport(current, loaded, from, to, interval));
                }
                catch (Exception e)
                {
                    Logger.error("unable to load monthly overview", e);
                    GUI.getDisplay().asyncExec(() -> showError(current, e));
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

    private void applyReport(long current, List<PeriodBalance> loaded, LocalDate from, LocalDate to,
                             AggregationInterval interval)
    {
        if (root == null || root.isDisposed() || current != generation.get())
            return;
        periods = List.copyOf(loaded);
        displayedFrom = from;
        displayedTo = to;
        displayedInterval = interval;
        refresh.setEnabled(true);
        status.setText(periods.stream().allMatch(p -> p.income() == 0d && p.expenses() == 0d)
            ? "Keine Umsätze im gewählten Zeitraum." : "");
        updateSummary();
        canvas.setPeriods(periods);
        var size = canvas.preferredSize();
        canvas.setSize(size);
        chartScroller.setMinSize(size);
        root.layout(true, true);
    }

    private void openTransactions(PeriodBalance period, FlowTransactionSelection.Sign sign)
    {
        if (period == null || period.start() == null || period.end() == null)
        {
            Logger.warn("unable to open monthly overview transactions: missing period");
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                "Für diesen Zeitraum können keine Umsätze geöffnet werden.", StatusBarMessage.TYPE_INFO));
            return;
        }

        Set<String> accountIds = selectedAccountIds();
        if (accountIds.isEmpty())
        {
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                "Bitte mindestens ein EUR-Konto auswählen.", StatusBarMessage.TYPE_INFO));
            return;
        }

        FlowTransactionSelection.Sign filter = sign == null ? FlowTransactionSelection.Sign.ALL : sign;
        String title = transactionTitle(period, filter);
        Logger.info("opening monthly overview transactions for " + period.label() + " from " + period.start()
            + " to " + period.end() + " sign " + filter);
        Application.getMessagingFactory().sendMessage(new StatusBarMessage(
            "Umsätze werden geöffnet...", StatusBarMessage.TYPE_INFO));
        GUI.startView(FlowTransactionsView.class, new FlowTransactionSelection(title, period.start(),
            period.end(), accountIds, null, filter, true));
    }

    private static String transactionTitle(PeriodBalance period, FlowTransactionSelection.Sign sign)
    {
        String label = period.label();
        return switch (sign)
        {
            case INCOME -> "Einnahmen – " + label;
            case EXPENSE -> "Ausgaben – " + label;
            default -> "Umsätze – " + label;
        };
    }

    private void updateSummary()
    {
        double income = periods.stream().mapToDouble(PeriodBalance::income).sum();
        double expenses = periods.stream().mapToDouble(PeriodBalance::expenses).sum();
        double balance = income - expenses;
        int count = Math.max(1, periods.size());
        int monthCount = monthCount(periods);
        boolean showMonthlyAverage = displayedInterval != AggregationInterval.MONTHLY;
        summary.setRedraw(false);
        try
        {
            summary.getColumn(2).setText(displayedInterval.averageLabel());
            summary.getColumn(3).setText(showMonthlyAverage ? "pro Monat" : "");
            double[] values = { income, expenses, balance };
            for (int i = 0; i < values.length; i++)
            {
                summary.getItem(i).setText(1, euro(values[i]));
                summary.getItem(i).setText(2, euro(values[i] / count));
                summary.getItem(i).setText(3, showMonthlyAverage ? euro(values[i] / monthCount) : "");
            }
            summary.pack();
            for (TableColumn column : summary.getColumns())
                column.setWidth(Math.max(column.getWidth(), column.getText().isEmpty() ? 130 : 170));
            if (!showMonthlyAverage)
                summary.getColumn(3).setWidth(0);
            summary.getParent().layout(true, true);
        }
        finally
        {
            summary.setRedraw(true);
        }
        summary.redraw();
        summary.update();
    }

    private void showError(long current, Exception error)
    {
        if (root == null || root.isDisposed() || current != generation.get())
            return;
        refresh.setEnabled(true);
        status.setText(errorText("Fehler beim Laden der Monatsübersicht", error));
    }

    private void addSettingsButton()
    {
        GUI.getView().addPanelButton(new PanelButton("document-properties.png", new Action()
        {
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                openSettings();
            }
        }, "Einstellungen..."));
    }

    private void addExportButton()
    {
        GUI.getView().addPanelButton(new PanelButton("document-save.png", new Action()
        {
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                OverviewExportService.export(periods, displayedFrom, displayedTo, displayedInterval);
            }
        }, "Monatsübersicht exportieren..."));
    }

    private void openSettings()
    {
        try
        {
            Set<String> result = new OverviewSettingsDialog(accounts, excludedAccountIds,
                provider.loadExcludedCategories()).open();
            if (result == null)
                return;
            excludedAccountIds.clear();
            excludedAccountIds.addAll(result);
            AccountSelectionSettings.save(SETTINGS, excludedAccountIds);
            loadReport();
        }
        catch (OperationCanceledException e)
        {
            // Abbruch ist kein Fehler.
        }
        catch (Exception e)
        {
            Logger.error("unable to open monthly overview settings", e);
            status.setText(errorText("Einstellungen konnten nicht geöffnet werden", e));
        }
    }

    private Set<String> selectedAccountIds()
    {
        Set<String> result = new LinkedHashSet<>();
        for (AccountInfo account : accounts)
        {
            if (account.isEuro() && !excludedAccountIds.contains(account.id()))
                result.add(account.id());
        }
        return result;
    }

    private AggregationInterval selectedInterval()
    {
        int selection = intervalCombo.getSelectionIndex();
        return selection < 0 ? AggregationInterval.MONTHLY : AggregationInterval.values()[selection];
    }

    private AggregationInterval readInterval()
    {
        try
        {
            return AggregationInterval.valueOf(SETTINGS.getString("interval", AggregationInterval.MONTHLY.name()));
        }
        catch (IllegalArgumentException e)
        {
            return AggregationInterval.MONTHLY;
        }
    }

    private void persist(LocalDate from, LocalDate to, AggregationInterval interval)
    {
        SETTINGS.setAttribute("period.from", from.toString());
        SETTINGS.setAttribute("period.to", to.toString());
        SETTINGS.setAttribute("interval", interval.name());
        AccountSelectionSettings.save(SETTINGS, excludedAccountIds);
    }

    private static LocalDate selectedDate(Input input)
    {
        Object value = input.getValue();
        return value instanceof Date date
            ? date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() : null;
    }

    private static LocalDate readDate(String key, LocalDate fallback)
    {
        try
        {
            return LocalDate.parse(SETTINGS.getString(key, fallback.toString()));
        }
        catch (DateTimeParseException e)
        {
            return fallback;
        }
    }

    private static Date toDate(LocalDate date)
    {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static String euro(double value)
    {
        return String.format(Locale.GERMANY, "%,.0f €", value);
    }

    private static int monthCount(List<PeriodBalance> periods)
    {
        int months = 0;
        for (PeriodBalance period : periods)
        {
            YearMonth start = YearMonth.from(period.start());
            YearMonth end = YearMonth.from(period.end());
            months += (end.getYear() - start.getYear()) * 12 + end.getMonthValue() - start.getMonthValue() + 1;
        }
        return Math.max(1, months);
    }

    private static String errorText(String prefix, Throwable error)
    {
        String detail = error == null ? null : error.getLocalizedMessage();
        return detail == null || detail.isBlank() ? prefix + "." : prefix + ": " + detail;
    }

    private static Button button(Composite parent, String text, Runnable action)
    {
        Button button = new Button(parent, SWT.PUSH);
        button.setText(text);
        button.addListener(SWT.Selection, event -> action.run());
        return button;
    }

    private static void column(Table table, String text, int width)
    {
        TableColumn column = new TableColumn(table, SWT.RIGHT);
        column.setText(text);
        column.setWidth(width);
    }
}
