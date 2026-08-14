package de.open4me.hibiscus.reports.ui;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.Navigation;
import de.willuhn.jameica.gui.NavigationItem;
import de.willuhn.jameica.gui.util.Color;
import de.willuhn.jameica.gui.util.Font;
import de.willuhn.logging.Logger;

final class NavigationTreeFilter
{
    private static final String NAVIGATION_DATA_KEY = "jameica.item.navigation";
    private static final Set<String> PROTECTED_IDS = Set.of("hibiscus.navi");
    private static final Map<String, HiddenNode> HIDDEN_NODES = new HashMap<>();

    private NavigationTreeFilter()
    {
    }

    static void apply()
    {
        try
        {
            Map<String, TreeItem> itemLookup = itemLookup();
            Set<String> hiddenIds = NavigationTreeFilterSettings.hiddenIds();
            restoreVisible(itemLookup, hiddenIds);
            hideSelected(itemLookup, hiddenIds);
            hideEmptyFolders(itemLookup);
            redraw(itemLookup);
        }
        catch (Exception e)
        {
            Logger.warn("unable to apply navigation tree filter: " + e.getMessage());
        }
    }

    static void restoreForRefresh(String id)
    {
        try
        {
            Map<String, TreeItem> itemLookup = itemLookup();
            restoreNode(itemLookup, id);
        }
        catch (Exception e)
        {
            Logger.warn("unable to restore navigation item " + id + " for refresh: " + e.getMessage());
        }
    }

    static List<Entry> entries()
    {
        try
        {
            Map<String, TreeItem> itemLookup = itemLookup();
            Map<String, Entry> entries = new LinkedHashMap<>();
            Tree tree = tree(itemLookup);
            if (tree != null && !tree.isDisposed())
            {
                for (TreeItem item : tree.getItems())
                    addVisible(entries, item, 0);
            }
            for (HiddenNode node : HIDDEN_NODES.values())
                addHidden(entries, node, depth(entries, node.parentId()));
            return List.copyOf(entries.values());
        }
        catch (Exception e)
        {
            Logger.warn("unable to read navigation tree entries: " + e.getMessage());
            return List.of();
        }
    }

    static void saveHiddenIds(Set<String> hiddenIds)
    {
        Set<String> filtered = new HashSet<>();
        if (hiddenIds != null)
        {
            for (String id : hiddenIds)
            {
                if (id != null && !id.isBlank() && !isProtected(id))
                    filtered.add(id.trim());
            }
        }
        NavigationTreeFilterSettings.save(filtered);
        apply();
    }

    static boolean isProtected(String id)
    {
        return id == null || id.isBlank() || PROTECTED_IDS.contains(id);
    }

    private static void restoreVisible(Map<String, TreeItem> itemLookup, Set<String> hiddenIds) throws Exception
    {
        boolean restored;
        do
        {
            restored = false;
            for (HiddenNode node : List.copyOf(HIDDEN_NODES.values()))
            {
                if (hiddenIds.contains(node.id()))
                    continue;
                restored = restoreNode(itemLookup, node.id()) || restored;
            }
        }
        while (restored);
    }

    private static boolean restoreNode(Map<String, TreeItem> itemLookup, String id) throws Exception
    {
        HiddenNode node = HIDDEN_NODES.get(id);
        if (node == null)
            return false;
        if (itemLookup.containsKey(node.id()))
        {
            HIDDEN_NODES.remove(node.id());
            return true;
        }
        if (node.parentId() != null && HIDDEN_NODES.containsKey(node.parentId()))
            restoreNode(itemLookup, node.parentId());
        TreeItem parent = parent(itemLookup, node);
        if (parent == null || parent.isDisposed())
            return false;
        TreeItem restoredItem = new TreeItem(parent, org.eclipse.swt.SWT.NONE,
            Math.min(node.index(), parent.getItemCount()));
        initialize(restoredItem, node.item(), itemLookup);
        createChildren(restoredItem, node.item(), itemLookup);
        HIDDEN_NODES.remove(node.id());
        return true;
    }

    private static void hideSelected(Map<String, TreeItem> itemLookup, Set<String> hiddenIds)
    {
        for (String id : hiddenIds)
        {
            if (isProtected(id))
                continue;
            TreeItem item = itemLookup.get(id);
            if (item != null && !item.isDisposed())
                hide(item, itemLookup, false);
        }
    }

    private static void hideEmptyFolders(Map<String, TreeItem> itemLookup)
    {
        boolean changed;
        do
        {
            changed = false;
            for (TreeItem item : List.copyOf(itemLookup.values()))
            {
                if (item == null || item.isDisposed())
                    continue;
                NavigationItem navigationItem = navigationItem(item);
                if (navigationItem == null || isProtected(id(navigationItem)) || !isFolder(navigationItem))
                    continue;
                if (item.getItemCount() == 0)
                {
                    hide(item, itemLookup, true);
                    changed = true;
                }
            }
        }
        while (changed);
    }

    private static void hide(TreeItem item, Map<String, TreeItem> itemLookup, boolean automatic)
    {
        NavigationItem navigationItem = navigationItem(item);
        if (navigationItem == null)
            return;
        String id = id(navigationItem);
        if (isProtected(id))
            return;

        TreeItem parent = item.getParentItem();
        HIDDEN_NODES.put(id, new HiddenNode(navigationItem, id, parentId(parent), index(item), automatic));
        removeFromLookup(item, itemLookup);
        item.dispose();
    }

    private static TreeItem parent(Map<String, TreeItem> itemLookup, HiddenNode node)
    {
        if (node.parentId() == null)
            return null;
        TreeItem parent = itemLookup.get(node.parentId());
        return parent == null || parent.isDisposed() ? null : parent;
    }

    private static void addVisible(Map<String, Entry> entries, TreeItem item, int depth)
    {
        NavigationItem navigationItem = navigationItem(item);
        if (navigationItem == null)
            return;
        String id = id(navigationItem);
        String parentId = parentId(item.getParentItem());
        entries.put(id, new Entry(id, name(navigationItem), parentId, depth, true,
            isProtected(id) || parentId == null));
        for (TreeItem child : item.getItems())
            addVisible(entries, child, depth + 1);
    }

    private static void addHidden(Map<String, Entry> entries, HiddenNode node, int depth)
    {
        entries.putIfAbsent(node.id(), new Entry(node.id(), name(node.item()), node.parentId(), depth, false,
            isProtected(node.id()) || node.parentId() == null));
        try
        {
            de.willuhn.datasource.GenericIterator children = node.item().getChildren();
            while (children != null && children.hasNext())
            {
                NavigationItem child = (NavigationItem) children.next();
                addHidden(entries, new HiddenNode(child, id(child), node.id(), 0, node.automatic()), depth + 1);
            }
        }
        catch (Exception e)
        {
            Logger.warn("unable to inspect hidden navigation item " + node.id() + ": " + e.getMessage());
        }
    }

    private static int depth(Map<String, Entry> entries, String parentId)
    {
        Entry parent = entries.get(parentId);
        return parent == null ? 0 : parent.depth() + 1;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, TreeItem> itemLookup() throws ReflectiveOperationException
    {
        Navigation navigation = GUI.getNavigation();
        if (navigation == null)
            return Map.of();
        Field field = Navigation.class.getDeclaredField("itemLookup");
        field.setAccessible(true);
        return (Map<String, TreeItem>) field.get(navigation);
    }

    private static Tree tree(Map<String, TreeItem> itemLookup)
    {
        for (TreeItem item : itemLookup.values())
        {
            if (item != null && !item.isDisposed())
                return item.getParent();
        }
        return null;
    }

    private static void redraw(Map<String, TreeItem> itemLookup)
    {
        Tree tree = tree(itemLookup);
        if (tree != null && !tree.isDisposed())
            tree.redraw();
    }

    private static void createChildren(TreeItem parent, NavigationItem navigationItem,
                                       Map<String, TreeItem> itemLookup) throws Exception
    {
        de.willuhn.datasource.GenericIterator children = navigationItem.getChildren();
        while (children != null && children.hasNext())
        {
            NavigationItem child = (NavigationItem) children.next();
            TreeItem childItem = new TreeItem(parent, org.eclipse.swt.SWT.NONE);
            initialize(childItem, child, itemLookup);
            createChildren(childItem, child, itemLookup);
        }
    }

    private static void initialize(TreeItem treeItem, NavigationItem item, Map<String, TreeItem> itemLookup)
        throws Exception
    {
        treeItem.setFont(Font.DEFAULT.getSWTFont());
        treeItem.setData(NAVIGATION_DATA_KEY, item);
        treeItem.setText(item.getName() == null ? "" : item.getName());
        treeItem.setExpanded(item.isExpanded());
        treeItem.setImage(treeItem.getExpanded() ? item.getIconOpen() : item.getIconClose());
        if (!item.isEnabled())
        {
            treeItem.setGrayed(true);
            treeItem.setForeground(Color.COMMENT.getSWTColor());
        }
        itemLookup.put(item.getID(), treeItem);
    }

    private static void removeFromLookup(TreeItem item, Map<String, TreeItem> itemLookup)
    {
        for (TreeItem child : item.getItems())
            removeFromLookup(child, itemLookup);

        NavigationItem navigationItem = navigationItem(item);
        if (navigationItem != null)
            itemLookup.remove(id(navigationItem));
    }

    private static int index(TreeItem item)
    {
        TreeItem parent = item.getParentItem();
        TreeItem[] siblings = parent == null ? item.getParent().getItems() : parent.getItems();
        for (int i = 0; i < siblings.length; i++)
        {
            if (siblings[i] == item)
                return i;
        }
        return siblings.length;
    }

    private static String parentId(TreeItem parent)
    {
        NavigationItem parentItem = navigationItem(parent);
        return parentItem == null ? null : id(parentItem);
    }

    private static NavigationItem navigationItem(TreeItem item)
    {
        if (item == null || item.isDisposed())
            return null;
        Object data = item.getData(NAVIGATION_DATA_KEY);
        return data instanceof NavigationItem navigationItem ? navigationItem : null;
    }

    private static boolean isFolder(NavigationItem item)
    {
        try
        {
            de.willuhn.datasource.GenericIterator children = item.getChildren();
            return children != null && children.size() > 0;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private static String id(NavigationItem item)
    {
        try
        {
            return item == null ? "" : item.getID();
        }
        catch (Exception e)
        {
            return "";
        }
    }

    private static String name(NavigationItem item)
    {
        try
        {
            return item == null ? "" : item.getName();
        }
        catch (Exception e)
        {
            return "";
        }
    }

    record Entry(String id, String name, String parentId, int depth, boolean visible, boolean protectedEntry)
    {
    }

    private record HiddenNode(NavigationItem item, String id, String parentId, int index, boolean automatic)
    {
    }
}
