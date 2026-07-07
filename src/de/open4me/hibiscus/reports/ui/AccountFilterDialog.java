package de.open4me.hibiscus.reports.ui;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;

import de.open4me.hibiscus.reports.model.AccountInfo;
import de.open4me.hibiscus.reports.model.ExcludedCategoryInfo;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.internal.buttons.Cancel;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.util.ApplicationException;

final class AccountFilterDialog extends AbstractDialog<AccountFilterDialog.Result>
{
    private static final int WINDOW_WIDTH = 720;
    private static final int WINDOW_HEIGHT = 520;

    private final List<AccountInfo> accounts;
    private final Set<String> excludedIds;
    private final List<ExcludedCategoryInfo> excludedCategories;
    private Table table;
    private Spinner threshold;
    private Result result;

    record Result(Set<String> excludedAccountIds, int thresholdTenths)
    {
    }

    AccountFilterDialog(List<AccountInfo> accounts, Set<String> excludedIds,
                        List<ExcludedCategoryInfo> excludedCategories, int thresholdTenths)
    {
        super(POSITION_CENTER);
        this.accounts = List.copyOf(accounts);
        this.excludedIds = Set.copyOf(excludedIds);
        this.excludedCategories = List.copyOf(excludedCategories);
        this.result = null;
        setTitle("Einstellungen der Geldfluss-Auswertung");
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        this.initialThresholdTenths = thresholdTenths;
    }

    @Override
    protected void paint(Composite parent) throws Exception
    {
        Container container = new SimpleContainer(parent, true, 1);
        TabFolder tabs = new TabFolder(container.getComposite(), SWT.NONE);
        tabs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        createAccountTab(tabs);
        createCategoryTab(tabs);
        createDisplayTab(tabs);

        ButtonArea buttons = new ButtonArea();
        buttons.addButton("Übernehmen", new Action()
        {
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                Set<String> excluded = new HashSet<>();
                for (TableItem item : table.getItems())
                {
                    AccountInfo account = (AccountInfo) item.getData();
                    if (account.isEuro() && !item.getChecked())
                        excluded.add(account.id());
                }
                result = new Result(Set.copyOf(excluded), threshold.getSelection());
                close();
            }
        }, null, true, "ok.png");
        buttons.addButton(new Cancel());
        container.addButtonArea(buttons);
        getShell().setMinimumSize(getShell().computeSize(WINDOW_WIDTH, WINDOW_HEIGHT));
    }

    private final int initialThresholdTenths;

    private void createAccountTab(TabFolder tabs)
    {
        Composite content = tab(tabs, "Konten");
        Label description = new Label(content, SWT.WRAP);
        description.setText("Alle EUR-Konten sind einbezogen, solange sie hier nicht explizit abgewählt werden. "
            + "Neue Konten werden automatisch einbezogen. Konten in anderen Währungen können ohne "
            + "Währungsumrechnung nicht ausgewertet werden.");
        description.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Composite selectionButtons = new Composite(content, SWT.NONE);
        selectionButtons.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        selectionButtons.setLayout(new GridLayout(2, false));
        nativeButton(selectionButtons, "Alle einbeziehen", () -> setAll(true));
        nativeButton(selectionButtons, "Alle ausschließen", () -> setAll(false));

        table = new Table(content, SWT.CHECK | SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        column(table, "Gruppe", 180);
        column(table, "Konto", 360);
        column(table, "Status", 130);

        for (AccountInfo account : accounts)
        {
            TableItem item = new TableItem(table, SWT.NONE);
            item.setData(account);
            item.setText(0, account.groupLabel());
            item.setText(1, account.name());
            item.setText(2, account.disabled() ? "Deaktiviert" : "");
            item.setChecked(account.isEuro() && !excludedIds.contains(account.id()));
            if (!account.isEuro())
                item.setForeground(table.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
        }
        table.addListener(SWT.Selection, event -> {
            if (event.detail == SWT.CHECK && event.item instanceof TableItem item)
            {
                AccountInfo account = (AccountInfo) item.getData();
                if (!account.isEuro())
                    item.setChecked(false);
            }
        });
    }

    private void createCategoryTab(TabFolder tabs)
    {
        Composite content = tab(tabs, "Ausgeschlossene Kategorien");
        Label description = new Label(content, SWT.WRAP);
        description.setText("Die Liste enthält alle effektiv ausgeschlossenen Kategorien. "
            + "Ein Ausschluss einer Oberkategorie gilt automatisch für ihre Unterkategorien.");
        description.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Table categories = new Table(content, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
        categories.setHeaderVisible(true);
        categories.setLinesVisible(true);
        categories.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        column(categories, "Kategoriepfad", 430);
        column(categories, "Grund", 220);
        if (excludedCategories.isEmpty())
        {
            TableItem item = new TableItem(categories, SWT.NONE);
            item.setText(0, "Keine Kategorien ausgeschlossen");
        }
        else
        {
            for (ExcludedCategoryInfo category : excludedCategories)
            {
                TableItem item = new TableItem(categories, SWT.NONE);
                item.setText(0, category.path());
                item.setText(1, category.reason());
            }
        }
    }

    private void createDisplayTab(TabFolder tabs)
    {
        Composite content = tab(tabs, "Darstellung");
        Label description = new Label(content, SWT.WRAP);
        description.setText("Kleine Flüsse unterhalb des Mindestanteils werden als „Sonstige“ gebündelt. "
            + "Unkategorisierte Einnahmen und Ausgaben bleiben immer als eigene Blöcke sichtbar.");
        description.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Composite row = new Composite(content, SWT.NONE);
        row.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        row.setLayout(new GridLayout(3, false));
        new Label(row, SWT.NONE).setText("Mindestanteil:");
        threshold = new Spinner(row, SWT.BORDER);
        threshold.setMinimum(0);
        threshold.setMaximum(1000);
        threshold.setDigits(1);
        threshold.setIncrement(5);
        threshold.setPageIncrement(10);
        threshold.setSelection(initialThresholdTenths);
        new Label(row, SWT.NONE).setText("%");
    }

    private static Composite tab(TabFolder folder, String title)
    {
        TabItem item = new TabItem(folder, SWT.NONE);
        item.setText(title);
        Composite content = new Composite(folder, SWT.NONE);
        content.setLayout(new GridLayout(1, false));
        item.setControl(content);
        return content;
    }

    private void setAll(boolean included)
    {
        if (table == null || table.isDisposed())
            return;
        for (TableItem item : table.getItems())
        {
            AccountInfo account = (AccountInfo) item.getData();
            item.setChecked(included && account.isEuro());
        }
    }

    private static void column(Table owner, String name, int width)
    {
        TableColumn column = new TableColumn(owner, SWT.LEFT);
        column.setText(name);
        column.setWidth(width);
    }

    private static void nativeButton(Composite parent, String text, Runnable action)
    {
        Button button = new Button(parent, SWT.PUSH);
        button.setText(text);
        button.addListener(SWT.Selection, event -> action.run());
    }

    @Override
    protected Result getData()
    {
        return result;
    }
}
