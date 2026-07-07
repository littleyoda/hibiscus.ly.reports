package de.open4me.hibiscus.reports.ui;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
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

final class OverviewSettingsDialog extends AbstractDialog<Set<String>>
{
    private final List<AccountInfo> accounts;
    private final Set<String> excludedIds;
    private final List<ExcludedCategoryInfo> excludedCategories;
    private Table accountsTable;
    private Set<String> result;

    OverviewSettingsDialog(List<AccountInfo> accounts, Set<String> excludedIds,
                           List<ExcludedCategoryInfo> excludedCategories)
    {
        super(POSITION_CENTER);
        this.accounts = List.copyOf(accounts);
        this.excludedIds = Set.copyOf(excludedIds);
        this.excludedCategories = List.copyOf(excludedCategories);
        setTitle("Einstellungen der Monatsübersicht");
        setSize(720, 500);
    }

    @Override
    protected void paint(Composite parent) throws Exception
    {
        Container container = new SimpleContainer(parent, true, 1);
        TabFolder tabs = new TabFolder(container.getComposite(), SWT.NONE);
        tabs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        createAccountTab(tabs);
        createCategoryTab(tabs);

        ButtonArea buttons = new ButtonArea();
        buttons.addButton("Übernehmen", new Action()
        {
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                Set<String> excluded = new HashSet<>();
                for (TableItem item : accountsTable.getItems())
                {
                    AccountInfo account = (AccountInfo) item.getData();
                    if (account.isEuro() && !item.getChecked())
                        excluded.add(account.id());
                }
                result = Set.copyOf(excluded);
                close();
            }
        }, null, true, "ok.png");
        buttons.addButton(new Cancel());
        container.addButtonArea(buttons);
        getShell().setMinimumSize(getShell().computeSize(720, 500));
    }

    private void createAccountTab(TabFolder tabs)
    {
        Composite content = tab(tabs, "Konten");
        Label description = new Label(content, SWT.WRAP);
        description.setText("Alle EUR-Konten sind einbezogen, solange sie hier nicht explizit abgewählt werden. "
            + "Die Auswahl ist unabhängig von der Geldfluss-Auswertung.");
        description.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Composite buttons = new Composite(content, SWT.NONE);
        buttons.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        buttons.setLayout(new GridLayout(2, false));
        button(buttons, "Alle einbeziehen", () -> setAll(true));
        button(buttons, "Alle ausschließen", () -> setAll(false));

        accountsTable = new Table(content, SWT.CHECK | SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
        accountsTable.setHeaderVisible(true);
        accountsTable.setLinesVisible(true);
        accountsTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        column(accountsTable, "Gruppe", 180);
        column(accountsTable, "Konto", 360);
        column(accountsTable, "Status", 130);
        for (AccountInfo account : accounts)
        {
            TableItem item = new TableItem(accountsTable, SWT.NONE);
            item.setData(account);
            item.setText(0, account.groupLabel());
            item.setText(1, account.name());
            item.setText(2, account.disabled() ? "Deaktiviert" : "");
            item.setChecked(account.isEuro() && !excludedIds.contains(account.id()));
            if (!account.isEuro())
                item.setForeground(accountsTable.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
        }
        accountsTable.addListener(SWT.Selection, event -> {
            if (event.detail == SWT.CHECK && event.item instanceof TableItem item
                && !((AccountInfo) item.getData()).isEuro())
                item.setChecked(false);
        });
    }

    private void createCategoryTab(TabFolder tabs)
    {
        Composite content = tab(tabs, "Ausgeschlossene Kategorien");
        Label description = new Label(content, SWT.WRAP);
        description.setText("Diese in Hibiscus hinterlegten Ausschlüsse gelten auch für die Monatsübersicht. "
            + "Ein Ausschluss einer Oberkategorie umfasst ihre Unterkategorien.");
        description.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Table table = new Table(content, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        column(table, "Kategoriepfad", 430);
        column(table, "Grund", 220);
        if (excludedCategories.isEmpty())
        {
            new TableItem(table, SWT.NONE).setText(0, "Keine Kategorien ausgeschlossen");
        }
        else
        {
            for (ExcludedCategoryInfo category : excludedCategories)
            {
                TableItem item = new TableItem(table, SWT.NONE);
                item.setText(0, category.path());
                item.setText(1, category.reason());
            }
        }
    }

    private void setAll(boolean included)
    {
        for (TableItem item : accountsTable.getItems())
            item.setChecked(((AccountInfo) item.getData()).isEuro() && included);
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

    private static void column(Table table, String text, int width)
    {
        TableColumn column = new TableColumn(table, SWT.LEFT);
        column.setText(text);
        column.setWidth(width);
    }

    private static void button(Composite parent, String text, Runnable action)
    {
        org.eclipse.swt.widgets.Button button = new org.eclipse.swt.widgets.Button(parent, SWT.PUSH);
        button.setText(text);
        button.addListener(SWT.Selection, event -> action.run());
    }

    @Override
    protected Set<String> getData()
    {
        return result;
    }
}
