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
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.internal.buttons.Cancel;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.util.ApplicationException;

final class GroupBalanceSettingsDialog extends AbstractDialog<Set<String>>
{
    private final List<AccountGroupChoice> groups;
    private final Set<String> excludedKeys;
    private Table table;
    private Set<String> result;

    GroupBalanceSettingsDialog(List<AccountGroupChoice> groups, Set<String> excludedKeys)
    {
        super(POSITION_CENTER);
        this.groups = List.copyOf(groups);
        this.excludedKeys = Set.copyOf(excludedKeys);
        setTitle("Einstellungen – Saldo nach Gruppen");
        setSize(560, 430);
    }

    @Override
    protected void paint(Composite parent) throws Exception
    {
        Container container = new SimpleContainer(parent, true, 1);
        Composite content = container.getComposite();

        Label description = new Label(content, SWT.WRAP);
        description.setText("Alle Gruppen sind einbezogen, solange sie hier nicht explizit abgewählt werden. "
            + "Ausgeschlossene Gruppen erscheinen nicht in der Gruppenauswahl und fließen nicht in die Gesamtsumme ein.");
        description.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Composite selectionButtons = new Composite(content, SWT.NONE);
        selectionButtons.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        selectionButtons.setLayout(new GridLayout(2, false));
        button(selectionButtons, "Alle einbeziehen", () -> setAll(true));
        button(selectionButtons, "Alle ausschließen", () -> setAll(false));

        table = new Table(content, SWT.CHECK | SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        TableColumn column = new TableColumn(table, SWT.LEFT);
        column.setText("Gruppe");
        column.setWidth(500);
        for (AccountGroupChoice group : groups)
        {
            TableItem item = new TableItem(table, SWT.NONE);
            item.setData(group);
            item.setText(group.label());
            item.setChecked(!excludedKeys.contains(group.storageKey()));
        }

        ButtonArea buttons = new ButtonArea();
        buttons.addButton("Übernehmen", new Action()
        {
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                Set<String> excluded = new HashSet<>();
                for (TableItem item : table.getItems())
                {
                    if (!item.getChecked())
                        excluded.add(((AccountGroupChoice) item.getData()).storageKey());
                }
                result = Set.copyOf(excluded);
                close();
            }
        }, null, true, "ok.png");
        buttons.addButton(new Cancel());
        container.addButtonArea(buttons);
        getShell().setMinimumSize(getShell().computeSize(560, 430));
    }

    private void setAll(boolean included)
    {
        for (TableItem item : table.getItems())
            item.setChecked(included);
    }

    private static void button(Composite parent, String text, Runnable action)
    {
        Button button = new Button(parent, SWT.PUSH);
        button.setText(text);
        button.addListener(SWT.Selection, event -> action.run());
    }

    @Override
    protected Set<String> getData()
    {
        return result;
    }
}
