package de.open4me.hibiscus.reports.ui;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.TabFolder;

import de.open4me.hibiscus.reports.duplicates.DuplicateDetector;
import de.open4me.hibiscus.reports.duplicates.DuplicateMatch;
import de.open4me.hibiscus.reports.duplicates.DuplicateTransaction;
import de.open4me.hibiscus.reports.duplicates.DuplicateTransactionRepository;
import de.willuhn.jameica.gui.AbstractView;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.input.Input;
import de.willuhn.jameica.gui.input.MultiInput;
import de.willuhn.jameica.gui.util.ColumnLayout;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.jameica.gui.util.TabGroup;
import de.willuhn.jameica.hbci.gui.action.UmsatzDetail;
import de.willuhn.jameica.hbci.gui.filter.KontoFilter;
import de.willuhn.jameica.hbci.gui.input.DateFromInput;
import de.willuhn.jameica.hbci.gui.input.DateToInput;
import de.willuhn.jameica.hbci.gui.input.KontoInput;
import de.willuhn.jameica.hbci.gui.input.RangeInput;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.hbci.server.Range.Category;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.BackgroundTask;
import de.willuhn.jameica.system.Settings;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.ProgressMonitor;

public final class DuplicateDetectionView extends AbstractView
{
    private static final Settings SETTINGS = new Settings(DuplicateDetectionView.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final NumberFormat AMOUNT_FORMAT = NumberFormat.getCurrencyInstance(Locale.GERMANY);

    private final DuplicateTransactionRepository repository = new DuplicateTransactionRepository();
    private final DuplicateDetector detector = new DuplicateDetector();
    private final AtomicLong generation = new AtomicLong();

    private Composite root;
    private Input fromDate;
    private Input toDate;
    private KontoInput accountInput;
    private Button refresh;
    private Button selectDuplicates;
    private Label status;
    private Table table;
    private Font boldTableFont;

    @Override
    public void bind() throws Exception
    {
        GUI.getView().setTitle("Duplikate suchen");
        root = new Composite(getParent(), SWT.NONE);
        root.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        root.setLayout(new GridLayout(1, false));

        createFilter(root);
        createTable(root);
        status = new Label(root, SWT.WRAP);
        status.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        root.layout(true, true);
        search();
    }

    private void createFilter(Composite parent) throws Exception
    {
        LocalDate today = LocalDate.now();
        LocalDate startDefault = today.withDayOfYear(1);
        fromDate = new DateFromInput(toDate(readDate("period.from", startDefault)),
            "hibiscus.ly.reports.duplicates.filter.from");
        fromDate.setName("Von");
        fromDate.setComment(null);
        toDate = new DateToInput(toDate(readDate("period.to", today)),
            "hibiscus.ly.reports.duplicates.filter.to");
        toDate.setName("bis");
        toDate.setComment(null);
        RangeInput range = new RangeInput(fromDate, toDate, Category.AUSWERTUNG,
            "hibiscus.ly.reports.duplicates.filter.range");

        TabFolder folder = new TabFolder(parent, SWT.NONE);
        folder.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        TabGroup tab = new TabGroup(folder, "Suche einschränken");
        ColumnLayout columns = new ColumnLayout(tab.getComposite(), 2);
        Container left = new SimpleContainer(columns.getComposite());
        left.addInput(range);
        Container right = new SimpleContainer(columns.getComposite());
        MultiInput dates = new MultiInput(fromDate, toDate);
        right.addInput(dates);

        Composite row = (Composite) dates.getControl();
        GridLayout layout = (GridLayout) row.getLayout();
        layout.numColumns += 2;
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        refresh = button(row, "Aktualisieren", this::search);
        selectDuplicates = button(row, "Alle Duplikate auswählen", this::selectOlderRows);

        accountInput = new KontoInput(null, KontoFilter.ACTIVE);
        accountInput.setName("Konten");
        accountInput.setComment(null);
        accountInput.setSupportGroups(true);
        accountInput.setPleaseChoose("<Alle Konten>");
        accountInput.setRememberSelection("hibiscus.ly.reports.duplicates.filter.account");
        right.addInput(accountInput);
    }

    private void createTable(Composite parent)
    {
        table = new Table(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
        boldTableFont = boldFont(table.getFont());
        table.addListener(SWT.Dispose, event ->
        {
            if (boldTableFont != null && !boldTableFont.isDisposed())
                boldTableFont.dispose();
        });
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        column("Paar", 60);
        column("Konto", 190);
        column("Umsatz-ID", 90);
        column("Datum", 100);
        column("Betrag", 110);
        column("Kategorie", 150);
        column("Verwendungszweck", 360);
        column("Ähnlichkeit", 120);
        column("Notizen", 220);

        table.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseDoubleClick(MouseEvent event)
            {
                openSelected();
            }
        });
        Menu menu = new Menu(table);
        MenuItem open = new MenuItem(menu, SWT.NONE);
        open.setText("Anzeigen");
        open.addListener(SWT.Selection, event -> openSelected());
        MenuItem delete = new MenuItem(menu, SWT.NONE);
        delete.setText("Löschen");
        delete.addListener(SWT.Selection, event -> deleteSelected());
        table.setMenu(menu);
    }

    private void search()
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
        Set<String> selectedAccountIds;
        try
        {
            selectedAccountIds = selectedAccountIds();
        }
        catch (Exception e)
        {
            Logger.error("unable to read selected duplicate detection account", e);
            status.setText(errorText("Das ausgewählte Konto konnte nicht gelesen werden", e));
            return;
        }
        SETTINGS.setAttribute("period.from", from.toString());
        SETTINGS.setAttribute("period.to", to.toString());
        status.setText("Umsätze werden geprüft ...");
        table.removeAll();
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
                    List<DuplicateMatch> matches = detector.find(repository.loadTransactions(selectedAccountIds, from, to));
                    if (!interrupted)
                        GUI.getDisplay().asyncExec(() -> applyResult(current, matches));
                }
                catch (Exception e)
                {
                    Logger.error("unable to detect duplicate transactions", e);
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

    private Set<String> selectedAccountIds() throws Exception
    {
        Object value = accountInput.getValue();
        if (value == null)
            return activeAccountIds();
        if (!(value instanceof Konto account))
            return groupAccountIds(value.toString());
        Set<String> result = new LinkedHashSet<>();
        result.add(account.getID());
        return result;
    }

    private Set<String> activeAccountIds() throws Exception
    {
        Set<String> result = new LinkedHashSet<>();
        for (Konto account : repository.loadActiveAccounts())
            result.add(account.getID());
        return result;
    }

    private Set<String> groupAccountIds(String group) throws Exception
    {
        Set<String> result = new LinkedHashSet<>();
        if (group == null || group.isBlank())
            return result;
        for (Konto account : repository.loadActiveAccounts())
        {
            if (group.equals(account.getKategorie()))
                result.add(account.getID());
        }
        return result;
    }

    private void applyResult(long current, List<DuplicateMatch> matches)
    {
        if (disposed() || current != generation.get())
            return;
        refresh.setEnabled(true);
        table.setRedraw(false);
        try
        {
            table.removeAll();
            for (DuplicateMatch match : matches)
            {
                addRow(match, match.first());
                addRow(match, match.second());
            }
        }
        finally
        {
            table.setRedraw(true);
        }
        status.setText(matches.isEmpty() ? "Keine Duplikate gefunden."
            : matches.size() + " mögliche Duplikat-Paare gefunden.");
    }

    private void addRow(DuplicateMatch match, DuplicateTransaction transaction)
    {
        TableItem item = new TableItem(table, SWT.NONE);
        boolean older = olderCandidate(match, transaction);
        item.setData(new DuplicateRow(match, transaction, older));
        item.setText(new String[] {
            Integer.toString(match.pairId()),
            transaction.accountName(),
            transaction.id(),
            transaction.date() == null ? "" : DATE_FORMAT.format(transaction.date()),
            AMOUNT_FORMAT.format(transaction.amount()),
            transaction.category(),
            oneLine(transaction.purpose()),
            match.level().label() + " (" + match.score() + ")",
            transaction.note()
        });
        if (older)
            item.setFont(boldTableFont);
    }

    private void selectOlderRows()
    {
        if (table == null || table.isDisposed())
            return;
        List<TableItem> selected = new java.util.ArrayList<>();
        for (TableItem item : table.getItems())
        {
            Object data = item.getData();
            if (data instanceof DuplicateRow row && row.older())
                selected.add(item);
        }
        table.setSelection(selected.toArray(new TableItem[0]));
        status.setText(selected.isEmpty() ? "Keine löschbaren Duplikat-Vorschläge ausgewählt."
            : selected.size() + " ältere Duplikat-Einträge ausgewählt.");
    }

    private static boolean olderCandidate(DuplicateMatch match, DuplicateTransaction transaction)
    {
        long id = idNumber(transaction.id());
        long first = idNumber(match.first().id());
        long second = idNumber(match.second().id());
        if (id == Long.MAX_VALUE || first == Long.MAX_VALUE || second == Long.MAX_VALUE || first == second)
            return false;
        return id == Math.min(first, second);
    }

    private void openSelected()
    {
        DuplicateRow row = selectedRow();
        if (row == null)
            return;
        try
        {
            Umsatz transaction = repository.loadTransaction(row.transaction().id());
            new UmsatzDetail().handleAction(transaction);
        }
        catch (Exception e)
        {
            Logger.error("unable to open duplicate transaction " + row.transaction().id(), e);
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                errorText("Umsatz konnte nicht geöffnet werden", e), StatusBarMessage.TYPE_ERROR));
        }
    }

    private void deleteSelected()
    {
        TableItem[] selection = table.getSelection();
        if (selection == null || selection.length == 0)
            return;
        Set<String> ids = new LinkedHashSet<>();
        for (TableItem item : selection)
        {
            Object data = item.getData();
            if (data instanceof DuplicateRow row)
                ids.add(row.transaction().id());
        }
        if (ids.isEmpty())
            return;
        try
        {
            String label = ids.size() == 1 ? "Diesen Umsatz wirklich löschen?"
                : ids.size() + " Umsätze wirklich löschen?";
            if (!Application.getCallback().askUser(label))
                return;
            for (String id : ids)
                repository.loadTransaction(id).delete();
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                ids.size() + " Umsatz/Umsätze gelöscht.", StatusBarMessage.TYPE_SUCCESS));
            search();
        }
        catch (Exception e)
        {
            Logger.error("unable to delete duplicate transactions", e);
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                errorText("Umsätze konnten nicht gelöscht werden", e), StatusBarMessage.TYPE_ERROR));
        }
    }

    private DuplicateRow selectedRow()
    {
        TableItem[] selection = table.getSelection();
        if (selection == null || selection.length == 0)
            return null;
        Object data = selection[0].getData();
        return data instanceof DuplicateRow row ? row : null;
    }

    private void showError(long current, Exception error)
    {
        if (disposed() || current != generation.get())
            return;
        refresh.setEnabled(true);
        status.setText(errorText("Fehler bei der Duplikats-Erkennung", error));
    }

    private boolean disposed()
    {
        return root == null || root.isDisposed();
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

    private static String oneLine(String value)
    {
        return value == null ? "" : value.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private static long idNumber(String id)
    {
        try
        {
            return Long.parseLong(id);
        }
        catch (Exception e)
        {
            return Long.MAX_VALUE;
        }
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

    private void column(String text, int width)
    {
        TableColumn column = new TableColumn(table, "Betrag".equals(text) ? SWT.RIGHT : SWT.LEFT);
        column.setText(text);
        column.setWidth(width);
    }

    private static Font boldFont(Font base)
    {
        FontData[] data = base.getFontData();
        for (FontData fontData : data)
            fontData.setStyle(fontData.getStyle() | SWT.BOLD);
        return new Font(base.getDevice(), data);
    }

    private record DuplicateRow(DuplicateMatch match, DuplicateTransaction transaction, boolean older)
    {
    }
}
