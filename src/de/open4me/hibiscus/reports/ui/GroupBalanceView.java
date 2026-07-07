package de.open4me.hibiscus.reports.ui;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.TabFolder;

import de.open4me.hibiscus.reports.data.BalanceSeriesAggregator;
import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.gui.AbstractView;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.input.CheckboxInput;
import de.willuhn.jameica.gui.input.DateInput;
import de.willuhn.jameica.gui.input.Input;
import de.willuhn.jameica.gui.input.MultiInput;
import de.willuhn.jameica.gui.input.SelectInput;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.parts.PanelButton;
import de.willuhn.jameica.gui.util.ColumnLayout;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.DelayedListener;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.jameica.gui.util.TabGroup;
import de.willuhn.jameica.hbci.HBCI;
import de.willuhn.jameica.hbci.HBCIProperties;
import de.willuhn.jameica.hbci.gui.chart.LineChart;
import de.willuhn.jameica.hbci.gui.filter.KontoFilter;
import de.willuhn.jameica.hbci.gui.input.DateFromInput;
import de.willuhn.jameica.hbci.gui.input.DateToInput;
import de.willuhn.jameica.hbci.gui.input.RangeInput;
import de.willuhn.jameica.hbci.report.balance.AccountBalanceProvider;
import de.willuhn.jameica.hbci.report.balance.AccountBalanceService;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.hbci.server.KontoUtil;
import de.willuhn.jameica.hbci.server.Range.Category;
import de.willuhn.jameica.hbci.server.UmsatzUtil;
import de.willuhn.jameica.hbci.server.Value;
import de.willuhn.jameica.services.BeanService;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.OperationCanceledException;
import de.willuhn.jameica.system.Settings;
import de.willuhn.jameica.util.DateUtil;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.ColorGenerator;

public class GroupBalanceView extends AbstractView
{
    private static final Settings SETTINGS = new Settings(GroupBalanceView.class);

    private final BalanceSeriesAggregator aggregator = new BalanceSeriesAggregator();
    private final Listener reloadListener = this::reloadChart;

    private Composite root;
    private Label status;
    private SelectInput groupInput;
    private CheckboxInput activeOnly;
    private DateInput fromDate;
    private DateInput toDate;
    private RangeInput range;
    private LineChart chart;
    private List<Konto> euroAccounts = List.of();
    private final Set<String> excludedGroupKeys = new HashSet<>();
    private final Map<String, NamedBalanceSeries> tooltipSeries = new LinkedHashMap<>();

    @Override
    public void bind() throws Exception
    {
        GUI.getView().setTitle("Saldo nach Gruppen");
        euroAccounts = loadEuroAccounts();
        excludedGroupKeys.addAll(GroupExclusionSettings.load(SETTINGS));

        root = new Composite(getParent(), SWT.NONE);
        root.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        root.setLayout(new GridLayout(1, false));

        createFilter(root);
        addSettingsButton();
        status = new Label(root, SWT.WRAP);
        status.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        chart = new GroupBalanceLineChart();
        chart.addFeature(new GroupBalanceTooltip(() -> Map.copyOf(tooltipSeries)));
        reloadChart(null);
        chart.paint(root);
        root.layout(true, true);
    }

    private List<Konto> loadEuroAccounts() throws RemoteException
    {
        List<Konto> result = new ArrayList<>();
        for (Konto account : KontoUtil.getKonten(KontoFilter.ALL))
        {
            if (HBCIProperties.CURRENCY_DEFAULT_DE.equalsIgnoreCase(account.getWaehrung()))
                result.add(account);
        }
        return List.copyOf(result);
    }

    private void createFilter(Composite parent) throws RemoteException
    {
        groupInput = new SelectInput(groupChoices(), storedChoice());
        groupInput.setName("Gruppe");
        groupInput.setComment(null);
        groupInput.addListener(reloadListener);

        activeOnly = new CheckboxInput(SETTINGS.getBoolean("filter.active", false));
        activeOnly.setName("Nur aktive Konten");
        activeOnly.addListener(reloadListener);

        fromDate = new DateFromInput(null, "hibiscus.ly.reports.groupbalance.filter.from");
        fromDate.setName("Von");
        fromDate.setComment(null);
        fromDate.addListener(new DelayedListener(300, reloadListener));
        toDate = new DateToInput(null, "hibiscus.ly.reports.groupbalance.filter.to");
        toDate.setName("bis");
        toDate.setComment(null);
        toDate.addListener(new DelayedListener(300, reloadListener));
        range = new RangeInput(fromDate, toDate, Category.AUSWERTUNG,
            "hibiscus.ly.reports.groupbalance.filter.range");
        range.addListener(reloadListener);

        TabFolder folder = new TabFolder(parent, SWT.NONE);
        folder.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        TabGroup tab = new TabGroup(folder, "Anzeige einschränken");
        ColumnLayout columns = new ColumnLayout(tab.getComposite(), 2);
        Container left = new SimpleContainer(columns.getComposite());
        left.addInput(groupInput);
        left.addInput(activeOnly);
        Container right = new SimpleContainer(columns.getComposite());
        right.addInput(range);
        right.addInput(new MultiInput(fromDate, toDate));

        ButtonArea buttons = new ButtonArea();
        buttons.addButton("Aktualisieren", new Action()
        {
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                reloadChart(new Event());
            }
        }, null, true, "view-refresh.png");
        buttons.paint(parent);
    }

    private List<AccountGroupChoice> allGroupChoices() throws RemoteException
    {
        List<String> names = new ArrayList<>();
        boolean haveUngrouped = false;
        for (Konto account : euroAccounts)
        {
            String group = normalizeGroup(account.getKategorie());
            if (group == null)
                haveUngrouped = true;
            else if (!names.contains(group))
                names.add(group);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));

        List<AccountGroupChoice> result = new ArrayList<>();
        result.add(AccountGroupChoice.all());
        for (String name : names)
            result.add(AccountGroupChoice.named(name));
        if (haveUngrouped)
            result.add(AccountGroupChoice.ungrouped());
        return result;
    }

    private List<AccountGroupChoice> groupChoices() throws RemoteException
    {
        return allGroupChoices().stream()
            .filter(choice -> choice.isAll() || !excludedGroupKeys.contains(choice.storageKey()))
            .toList();
    }

    private AccountGroupChoice storedChoice() throws RemoteException
    {
        String kind = SETTINGS.getString("selection.kind", AccountGroupChoice.Kind.ALL.name());
        String name = SETTINGS.getString("selection.name", null);
        for (AccountGroupChoice choice : groupChoices())
        {
            if (choice.kind().name().equals(kind) && (choice.isAll()
                || java.util.Objects.equals(choice.name(), name)))
                return choice;
        }
        return AccountGroupChoice.all();
    }

    private void reloadChart(Event event)
    {
        if (chart == null)
            return;
        try
        {
            AccountGroupChoice choice = selectedChoice();
            persistSelection(choice);
            boolean onlyActive = Boolean.TRUE.equals(activeOnly.getValue());
            SETTINGS.setAttribute("filter.active", onlyActive);

            Map<AccountGroupChoice, List<Konto>> groups = selectedGroups(choice, onlyActive);
            List<Konto> accounts = groups.values().stream().flatMap(List::stream).toList();
            chart.removeAllData();
            tooltipSeries.clear();
            chart.setTitle("Saldo nach Gruppen");
            if (accounts.isEmpty())
            {
                status.setText("Keine passenden EUR-Konten gefunden.");
                redraw(event);
                return;
            }

            Date start = effectiveStart(accounts);
            Date end = effectiveEnd();
            if (start.after(end))
            {
                status.setText("Der Zeitraum enthält keine Ist-Salden.");
                redraw(event);
                return;
            }
            List<String> warnings = new ArrayList<>(balanceWarnings(accounts, onlyActive));

            chart.setTitle("Saldo nach Gruppen " + HBCI.DATEFORMAT.format(start) + " - "
                + HBCI.DATEFORMAT.format(end));
            BeanService beans = Application.getBootLoader().getBootable(BeanService.class);
            AccountBalanceService balanceService = beans.get(AccountBalanceService.class);
            List<List<Value>> totalParts = new ArrayList<>();
            List<BalanceSeriesDetails.AccountSeries> totalDetails = new ArrayList<>();
            int color = 1;
            boolean haveData = false;
            for (Map.Entry<AccountGroupChoice, List<Konto>> entry : groups.entrySet())
            {
                List<List<Value>> groupParts = new ArrayList<>();
                List<BalanceSeriesDetails.AccountSeries> groupDetails = new ArrayList<>();
                for (Konto account : entry.getValue())
                {
                    AccountBalanceProvider provider = balanceService.getBalanceProviderForAccount(account);
                    boolean depot = DepotBalanceValidation.isDepotAccountType(account.getAccountType());
                    if (depot && !DepotBalanceValidation.hasSpecializedProvider(provider))
                    {
                        warnings.add("Das Depot „" + accountName(account)
                            + "“ wird nicht berücksichtigt, weil seine historischen Werte nicht berechnet werden können. "
                            + "Empfehlung: Installieren oder aktivieren Sie den DepotViewer und prüfen Sie in den "
                            + "Kontoeinstellungen, ob die Kontoart „Wertpapierdepot“ oder „Fondsdepot“ gewählt ist.");
                        continue;
                    }
                    if (provider == null)
                        continue;

                    List<Value> values;
                    try
                    {
                        values = provider.getBalanceData(account, start, end);
                    }
                    catch (RuntimeException e)
                    {
                        if (!depot)
                            throw e;
                        Logger.error("unable to load depot balance for account " + account.getID(), e);
                        warnings.add("Das Depot „" + accountName(account)
                            + "“ wird nicht berücksichtigt, weil seine Werte nicht gelesen werden konnten. "
                            + "Empfehlung: Öffnen Sie den DepotViewer, aktualisieren Sie Bestand und Kurse und "
                            + "rufen Sie die Auswertung anschließend erneut auf.");
                        continue;
                    }
                    if (!DepotBalanceValidation.isUsableSeries(values))
                    {
                        if (depot)
                        {
                            warnings.add("Das Depot „" + accountName(account)
                                + "“ wird nicht berücksichtigt, weil keine verwendbaren historischen Werte vorliegen. "
                                + "Empfehlung: Prüfen Sie im DepotViewer, ob Wertpapierbestand, Käufe und Verkäufe "
                                + "vollständig sind und aktuelle Kurse vorliegen.");
                        }
                        continue;
                    }
                    if (depot)
                        validateDepotBalance(account, provider, values, start, end, warnings);

                    groupParts.add(values);
                    totalParts.add(values);
                    BalanceSeriesDetails.AccountSeries accountDetails =
                        new BalanceSeriesDetails.AccountSeries(account.getID(), accountName(account), values);
                    groupDetails.add(accountDetails);
                    totalDetails.add(accountDetails);
                }
                List<Value> values = aggregator.sum(groupParts);
                if (values.isEmpty())
                    continue;
                NamedBalanceSeries series = new NamedBalanceSeries(entry.getKey().label(), values, 2,
                    groupDetails);
                series.setColor(ColorGenerator.create(ColorGenerator.PALETTE_OFFICE + color++));
                chart.addData(series);
                tooltipSeries.put(series.getLabel(), series);
                haveData = true;
            }

            if (choice.isAll())
            {
                List<Value> total = aggregator.sum(totalParts);
                if (!total.isEmpty())
                {
                    String totalLabel = "Gesamtsumme";
                    while (tooltipSeries.containsKey(totalLabel))
                        totalLabel += " (alle Gruppen)";
                    NamedBalanceSeries series = new NamedBalanceSeries(totalLabel, total, 3, totalDetails);
                    series.setColor(ColorGenerator.create(ColorGenerator.PALETTE_OFFICE));
                    chart.addData(series);
                    tooltipSeries.put(series.getLabel(), series);
                    haveData = true;
                }
            }
            if (!warnings.isEmpty())
                status.setText(warningText(warnings));
            else
                status.setText(haveData ? "" : "Keine Salden im gewählten Zeitraum gefunden.");
            redraw(event);
        }
        catch (Exception e)
        {
            Logger.error("unable to load group balance chart", e);
            status.setText(errorText("Fehler beim Laden des Saldos nach Gruppen", e));
        }
    }

    private Map<AccountGroupChoice, List<Konto>> selectedGroups(AccountGroupChoice selection,
        boolean onlyActive)
        throws RemoteException
    {
        Map<AccountGroupChoice, List<Konto>> result = new LinkedHashMap<>();
        List<AccountGroupChoice> choices = groupChoices();
        for (AccountGroupChoice choice : choices)
        {
            if (choice.isAll() || (!selection.isAll() && !choice.equals(selection)))
                continue;
            List<Konto> accounts = new ArrayList<>();
            for (Konto account : euroAccounts)
            {
                if (onlyActive && account.hasFlag(Konto.FLAG_DISABLED))
                    continue;
                if (choice.matches(account))
                    accounts.add(account);
            }
            if (!accounts.isEmpty())
                result.put(choice, accounts);
        }
        return result;
    }

    private Date effectiveStart(List<Konto> accounts) throws RemoteException
    {
        Object selected = fromDate.getValue();
        if (selected instanceof Date date)
            return DateUtil.startOfDay(date);

        Date oldest = null;
        for (Konto account : accounts)
        {
            Date candidate = UmsatzUtil.getOldest(account);
            if (candidate != null && (oldest == null || candidate.before(oldest)))
                oldest = candidate;
        }
        return DateUtil.startOfDay(oldest != null ? oldest : new Date());
    }

    private static String accountName(Konto account) throws RemoteException
    {
        String name = account.getBezeichnung();
        return name == null || name.isBlank() ? "Konto " + account.getID() : name;
    }

    private static void validateDepotBalance(Konto account, AccountBalanceProvider provider,
                                             List<Value> reportValues, Date reportStart, Date reportEnd,
                                             List<String> warnings) throws RemoteException
    {
        Date balanceDate = account.getSaldoDatum();
        if (balanceDate == null)
            return;

        double accountBalance = account.getSaldo();
        if (!Double.isFinite(accountBalance))
        {
            warnings.add("Depot „" + accountName(account)
                + "“ kann nicht zuverlässig geprüft werden, weil kein gültiger aktueller Depotwert gespeichert ist. "
                + "Empfehlung: Aktualisieren Sie das Depot und öffnen Sie die Auswertung danach erneut.");
            return;
        }

        Date dayStart = DateUtil.startOfDay(balanceDate);
        Date dayEnd = DateUtil.endOfDay(balanceDate);
        List<Value> validationValues = reportValues;
        if (dayStart.before(reportStart) || dayEnd.after(reportEnd))
        {
            try
            {
                validationValues = provider.getBalanceData(account, dayStart, dayEnd);
            }
            catch (RuntimeException e)
            {
                Logger.error("unable to validate depot balance for account " + account.getID(), e);
                warnings.add("Das Depot „" + accountName(account) + "“ kann für den "
                    + HBCI.DATEFORMAT.format(balanceDate) + " nicht zuverlässig geprüft werden. "
                    + "Empfehlung: Aktualisieren Sie im DepotViewer den Bestand und die Kurse.");
                return;
            }
        }

        if (!DepotBalanceValidation.isUsableSeries(validationValues))
        {
            warnings.add("Für das Depot „" + accountName(account) + "“ fehlt am "
                + HBCI.DATEFORMAT.format(balanceDate) + " ein berechneter Depotwert. "
                + "Empfehlung: Prüfen Sie im DepotViewer, ob für alle enthaltenen Wertpapiere Kurse vorliegen.");
            return;
        }

        Double providerBalance = DepotBalanceValidation.valueAtOrBefore(validationValues, dayEnd);
        if (providerBalance == null || !Double.isFinite(providerBalance))
        {
            warnings.add("Für das Depot „" + accountName(account) + "“ liegt am "
                + HBCI.DATEFORMAT.format(balanceDate) + " kein gültiger berechneter Wert vor. "
                + "Empfehlung: Aktualisieren Sie Bestand und Kurse im DepotViewer.");
            return;
        }

        if (DepotBalanceValidation.isZeroAtCent(providerBalance)
            && !DepotBalanceValidation.isZeroAtCent(accountBalance))
        {
            warnings.add("Für das Depot „" + accountName(account) + "“ wurde am "
                + HBCI.DATEFORMAT.format(balanceDate)
                + " ein Depotwert von 0,00 EUR berechnet, obwohl von der Bank "
                + HBCI.DECIMALFORMAT.format(accountBalance)
                + " EUR gespeichert sind. Für eine korrekte Darstellung fehlen wahrscheinlich Kursdaten "
                + "im DepotViewer. Empfehlung: Aktualisieren Sie im DepotViewer die Kurse und öffnen Sie "
                + "die Auswertung anschließend erneut.");
            return;
        }

        if (DepotBalanceValidation.differsMoreThanOnePercent(providerBalance, accountBalance))
        {
            double difference = Math.abs(DepotBalanceValidation.difference(providerBalance, accountBalance));
            Double percent = DepotBalanceValidation.relativeDifferencePercent(providerBalance, accountBalance);
            String percentText = percent == null ? "" : " / " + HBCI.DECIMALFORMAT.format(percent) + " %";
            warnings.add("Beim Depot „" + accountName(account) + "“ passt der von der Bank gespeicherte "
                + "Depotwert am "
                + HBCI.DATEFORMAT.format(balanceDate) + " nicht zum berechneten Depotwert: Aus Käufen, Verkäufen und Kursen "
                + "wurden " + HBCI.DECIMALFORMAT.format(providerBalance)
                + " EUR berechnet, gespeichert sind jedoch " + HBCI.DECIMALFORMAT.format(accountBalance)
                + " EUR (Abweichung " + HBCI.DECIMALFORMAT.format(difference)
                + " EUR" + percentText
                + "). Dadurch können Diagramm und Gesamtsumme falsch sein. Wahrscheinlich stimmt der Bestand "
                + "oder das Orderbuch im DepotViewer nicht. Empfehlung: Prüfen Sie im DepotViewer Bestand und "
                + "Orderbuch und starten Sie den „Abgleich zwischen Orderbuch und Bestand“.");
        }
    }

    private static List<String> balanceWarnings(List<Konto> accounts, boolean onlyActive)
        throws RemoteException
    {
        if (onlyActive)
            return List.of();

        List<String> warnings = new ArrayList<>();
        for (Konto account : accounts)
        {
            boolean disabled = account.hasFlag(Konto.FLAG_DISABLED);
            if (!disabled)
                continue;
            Double lastBookingBalance = lastBookedBalance(account);
            if (AccountBalanceConsistency.hasDisabledAccountMismatch(false, true, account.getSaldo(),
                lastBookingBalance))
            {
                warnings.add(accountName(account) + ": "
                    + HBCI.DECIMALFORMAT.format(lastBookingBalance)
                    + " EUR im letzten Umsatz, obwohl das deaktivierte Konto selbst 0,00 EUR ausweist. Dadurch "
                    + "können Diagramm und Gesamtsumme falsch sein. Empfehlung: Korrigieren Sie den Saldo der "
                    + "letzten Buchung oder schließen Sie das Konto über „Nur aktive Konten“ aus.");
            }
        }
        return List.copyOf(warnings);
    }

    private static Double lastBookedBalance(Konto account) throws RemoteException
    {
        DBIterator transactions = UmsatzUtil.getUmsaetzeBackwards();
        transactions.addFilter("konto_id = " + account.getID());
        while (transactions.hasNext())
        {
            Umsatz transaction = (Umsatz) transactions.next();
            if (!transaction.hasFlag(Umsatz.FLAG_NOTBOOKED))
                return transaction.getSaldo();
        }
        return null;
    }

    private static String warningText(List<String> warnings)
    {
        return "Warnung:\n• " + String.join("\n• ", warnings);
    }

    private Date effectiveEnd()
    {
        Date today = DateUtil.endOfDay(new Date());
        Object selected = toDate.getValue();
        if (!(selected instanceof Date date))
            return today;
        Date end = DateUtil.endOfDay(date);
        return end.after(today) ? today : end;
    }

    private AccountGroupChoice selectedChoice()
    {
        Object value = groupInput.getValue();
        return value instanceof AccountGroupChoice choice ? choice : AccountGroupChoice.all();
    }

    private void persistSelection(AccountGroupChoice choice)
    {
        SETTINGS.setAttribute("selection.kind", choice.kind().name());
        SETTINGS.setAttribute("selection.name", choice.name());
    }

    private void redraw(Event event) throws RemoteException
    {
        if (root != null && !root.isDisposed())
            root.layout(true, true);
        if (event != null)
            chart.redraw();
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

    private void openSettings()
    {
        try
        {
            List<AccountGroupChoice> configurable = allGroupChoices().stream()
                .filter(choice -> !choice.isAll()).toList();
            Set<String> result = new GroupBalanceSettingsDialog(configurable, excludedGroupKeys).open();
            if (result == null)
                return;
            excludedGroupKeys.clear();
            excludedGroupKeys.addAll(result);
            GroupExclusionSettings.save(SETTINGS, excludedGroupKeys);
            refreshGroupInput();
            reloadChart(new Event());
        }
        catch (OperationCanceledException e)
        {
            // Abbruch ist kein Fehler.
        }
        catch (Exception e)
        {
            Logger.error("unable to open group balance settings", e);
            status.setText(errorText("Einstellungen konnten nicht geöffnet werden", e));
        }
    }

    private void refreshGroupInput() throws RemoteException
    {
        AccountGroupChoice selected = selectedChoice();
        List<AccountGroupChoice> choices = groupChoices();
        groupInput.setList(choices);
        groupInput.setValue(choices.contains(selected) ? selected : AccountGroupChoice.all());
    }

    private static String normalizeGroup(String group)
    {
        return AccountGroupChoice.normalize(group);
    }

    private static String errorText(String prefix, Throwable error)
    {
        String detail = error == null ? null : error.getLocalizedMessage();
        return detail == null || detail.isBlank() ? prefix + "." : prefix + ": " + detail;
    }
}
