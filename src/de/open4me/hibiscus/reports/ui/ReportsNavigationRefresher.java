package de.open4me.hibiscus.reports.ui;

import java.lang.reflect.Field;
import java.util.Map;

import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import de.open4me.hibiscus.reports.data.DynamicReportRepository;
import de.open4me.hibiscus.reports.model.DynamicReport;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.Item;
import de.willuhn.jameica.gui.Navigation;
import de.willuhn.jameica.gui.NavigationItem;
import de.willuhn.jameica.gui.util.Color;
import de.willuhn.jameica.gui.util.Font;
import de.willuhn.logging.Logger;

final class ReportsNavigationRefresher
{
    private static final String NAVIGATION_DATA_KEY = "jameica.item.navigation";

    private ReportsNavigationRefresher()
    {
    }

    static void refresh()
    {
        try
        {
            Navigation navigation = GUI.getNavigation();
            if (navigation == null)
                return;

            Map<String, TreeItem> itemLookup = itemLookup(navigation);
            TreeItem reportsTreeItem = itemLookup.get(ReportsNavigationExtension.REPORTS_ROOT_ID);
            if (reportsTreeItem == null || reportsTreeItem.isDisposed())
                return;

            NavigationItem oldRoot = (NavigationItem) reportsTreeItem.getData(NAVIGATION_DATA_KEY);
            if (oldRoot == null)
                return;

            Item parent = oldRoot.getParent() instanceof Item item ? item : null;
            ReportNavigationItem root = new ReportNavigationItem(parent, oldRoot.getName(),
                oldRoot.getID(), "folder.png", "folder-open.png",
                new OpenDynamicReportsViewAction(), oldRoot.isExpanded());
            DynamicReportRepository repository = DynamicReportRepository.jameica();
            repository.initialize();
            for (DynamicReport report : repository.listReports())
                root.addChild(ReportsNavigationExtension.createReportItem(root, report));

            removeChildren(reportsTreeItem, itemLookup);
            reportsTreeItem.setData(NAVIGATION_DATA_KEY, root);
            reportsTreeItem.setText(root.getName());
            reportsTreeItem.setImage(reportsTreeItem.getExpanded() ? root.getIconOpen() : root.getIconClose());
            itemLookup.put(root.getID(), reportsTreeItem);

            for (TreeItem child : createReportChildren(reportsTreeItem, root, itemLookup))
                child.setExpanded(false);

            Tree tree = reportsTreeItem.getParent();
            if (tree != null && !tree.isDisposed())
                tree.redraw();
        }
        catch (Exception e)
        {
            Logger.warn("unable to refresh reports navigation: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, TreeItem> itemLookup(Navigation navigation) throws ReflectiveOperationException
    {
        Field field = Navigation.class.getDeclaredField("itemLookup");
        field.setAccessible(true);
        return (Map<String, TreeItem>) field.get(navigation);
    }

    private static TreeItem[] createReportChildren(TreeItem parent, ReportNavigationItem root,
                                                   Map<String, TreeItem> itemLookup) throws Exception
    {
        de.willuhn.datasource.GenericIterator children = root.getChildren();
        TreeItem[] created = new TreeItem[children == null ? 0 : children.size()];
        int index = 0;
        while (children != null && children.hasNext())
        {
            NavigationItem item = (NavigationItem) children.next();
            TreeItem treeItem = new TreeItem(parent, org.eclipse.swt.SWT.NONE);
            treeItem.setFont(Font.DEFAULT.getSWTFont());
            treeItem.setData(NAVIGATION_DATA_KEY, item);
            treeItem.setText(item.getName() == null ? "" : item.getName());
            treeItem.setImage(item.getIconClose());
            if (!item.isEnabled())
            {
                treeItem.setGrayed(true);
                treeItem.setForeground(Color.COMMENT.getSWTColor());
            }
            itemLookup.put(item.getID(), treeItem);
            created[index++] = treeItem;
        }
        return created;
    }

    private static void removeChildren(TreeItem parent, Map<String, TreeItem> itemLookup)
    {
        for (TreeItem child : parent.getItems())
        {
            removeFromLookup(child, itemLookup);
            child.dispose();
        }
    }

    private static void removeFromLookup(TreeItem item, Map<String, TreeItem> itemLookup)
    {
        for (TreeItem child : item.getItems())
            removeFromLookup(child, itemLookup);

        Object data = item.getData(NAVIGATION_DATA_KEY);
        try
        {
            if (data instanceof NavigationItem navigationItem)
                itemLookup.remove(navigationItem.getID());
        }
        catch (Exception e)
        {
            Logger.warn("unable to remove old reports navigation item from lookup: " + e.getMessage());
        }
    }
}
