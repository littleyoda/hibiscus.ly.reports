package de.open4me.hibiscus.reports.ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.internal.buttons.Cancel;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.util.ApplicationException;

final class NavigationTreeSettingsDialog extends AbstractDialog<Set<String>>
{
    private Tree tree;
    private Set<String> result;

    NavigationTreeSettingsDialog()
    {
        super(POSITION_CENTER);
        setTitle("Navigation-Baum anpassen");
        setSize(720, 620);
    }

    @Override
    protected void paint(org.eclipse.swt.widgets.Composite parent) throws Exception
    {
        Container container = new SimpleContainer(parent, true, 1);

        Label description = new Label(container.getComposite(), SWT.WRAP);
        description.setText("Abgewählte Einträge werden im linken Navigation-Baum ausgeblendet. "
            + "Die Auswahl wird anhand technischer Navigation-IDs gespeichert.");
        description.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        tree = new Tree(container.getComposite(), SWT.CHECK | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        tree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        tree.addListener(SWT.Selection, event -> {
            if (event.detail == SWT.CHECK && event.item instanceof TreeItem item)
            {
                if (protectedEntry(item))
                {
                    item.setChecked(true);
                    return;
                }
                setChildrenChecked(item, item.getChecked());
            }
        });
        fillTree();

        ButtonArea buttons = new ButtonArea();
        buttons.addButton("Übernehmen", new Action()
        {
            @Override
            public void handleAction(Object context) throws ApplicationException
            {
                result = collectHiddenIds();
                close();
            }
        }, null, true, "ok.png");
        buttons.addButton(new Cancel());
        container.addButtonArea(buttons);
        getShell().setMinimumSize(getShell().computeSize(720, 620));
    }

    private void fillTree()
    {
        Set<String> hiddenIds = NavigationTreeFilterSettings.hiddenIds();
        List<NavigationTreeFilter.Entry> entries = NavigationTreeFilter.entries().stream()
            .sorted(Comparator.comparingInt(NavigationTreeFilter.Entry::depth))
            .toList();
        Map<String, TreeItem> items = new HashMap<>();
        for (NavigationTreeFilter.Entry entry : entries)
        {
            TreeItem parent = items.get(entry.parentId());
            TreeItem item = parent == null ? new TreeItem(tree, SWT.NONE) : new TreeItem(parent, SWT.NONE);
            item.setData(entry);
            item.setText(label(entry));
            item.setChecked(!hiddenIds.contains(entry.id()));
            item.setGrayed(entry.protectedEntry());
            if (entry.protectedEntry())
                item.setChecked(true);
            items.put(entry.id(), item);
        }
        for (TreeItem item : tree.getItems())
            item.setExpanded(true);
    }

    private String label(NavigationTreeFilter.Entry entry)
    {
        String name = entry.name() == null || entry.name().isBlank() ? entry.id() : entry.name();
        return name + " [" + entry.id() + "]";
    }

    private void setChildrenChecked(TreeItem parent, boolean checked)
    {
        for (TreeItem child : parent.getItems())
        {
            if (!protectedEntry(child))
                child.setChecked(checked);
            setChildrenChecked(child, checked);
        }
    }

    private Set<String> collectHiddenIds()
    {
        Set<String> hiddenIds = new HashSet<>();
        for (TreeItem item : tree.getItems())
            collectHiddenIds(item, hiddenIds);
        return Set.copyOf(hiddenIds);
    }

    private void collectHiddenIds(TreeItem item, Set<String> hiddenIds)
    {
        Object data = item.getData();
        if (data instanceof NavigationTreeFilter.Entry entry && !entry.protectedEntry() && !item.getChecked())
            hiddenIds.add(entry.id());
        for (TreeItem child : item.getItems())
            collectHiddenIds(child, hiddenIds);
    }

    private boolean protectedEntry(TreeItem item)
    {
        Object data = item.getData();
        return data instanceof NavigationTreeFilter.Entry entry && entry.protectedEntry();
    }

    @Override
    protected Set<String> getData()
    {
        return result;
    }
}
