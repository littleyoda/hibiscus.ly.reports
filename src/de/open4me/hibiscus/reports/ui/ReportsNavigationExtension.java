package de.open4me.hibiscus.reports.ui;

import java.io.IOException;
import java.rmi.RemoteException;
import java.text.Collator;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.open4me.hibiscus.reports.data.DynamicReportRepository;
import de.open4me.hibiscus.reports.model.DynamicReport;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.Item;
import de.willuhn.jameica.gui.extension.Extendable;
import de.willuhn.jameica.gui.extension.Extension;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;

public class ReportsNavigationExtension implements Extension
{
    static final String REPORTS_ROOT_ID = "hibiscus.navi.reports";
    private static final Collator COLLATOR = Collator.getInstance(Locale.GERMANY);
    private static final Comparator<Item> ITEM_NAME_ORDER = ReportsNavigationExtension::compareItemsByName;

    @Override
    public void extend(Extendable extendable)
    {
        if (!(extendable instanceof Item parent))
            return;

        ReportNavigationItem reports = new ReportNavigationItem(parent, "Reports",
            REPORTS_ROOT_ID, "folder.png", "folder-open.png",
            new OpenDynamicReportsViewAction(), true);
        try
        {
            addReportChildren(reports);
            parent.addChild(reports);
        }
        catch (RemoteException e)
        {
            throw new IllegalStateException("Reports-Navigation konnte nicht erweitert werden", e);
        }
    }

    static void addReportChildren(ReportNavigationItem parent)
    {
        try
        {
            DynamicReportRepository repository = DynamicReportRepository.jameica();
            repository.initialize();
            addReportChildren(parent, repository.listReports());
        }
        catch (IOException e)
        {
            Logger.error("unable to load dynamic reports for navigation", e);
        }
    }

    static void addReportChildren(ReportNavigationItem parent, List<DynamicReport> reports)
    {
        Map<String, ReportNavigationItem> folders = new LinkedHashMap<>();
        for (DynamicReport report : reports)
            addReport(parent, folders, report);
        sortRecursively(parent);
    }

    private static void addReport(ReportNavigationItem root, Map<String, ReportNavigationItem> folders,
                                  DynamicReport report)
    {
        String[] segments = segments(report);
        ReportNavigationItem parent = root;
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < segments.length - 1; i++)
        {
            if (path.length() > 0)
                path.append('/');
            path.append(segments[i]);
            String key = path.toString();
            ReportNavigationItem folder = folders.get(key);
            if (folder == null)
            {
                folder = createFolderItem(parent, segments[i], key);
                folders.put(key, folder);
                parent.addChild(folder);
            }
            parent = folder;
        }
        parent.addChild(createReportItem(parent, report, segments[segments.length - 1]));
    }

    private static String[] segments(DynamicReport report)
    {
        String name = report == null ? "" : report.displayName();
        String[] raw = name == null ? new String[0] : name.replace('\\', '/').split("/");
        String[] segments = java.util.Arrays.stream(raw)
            .filter(segment -> segment != null && !segment.isBlank())
            .toArray(String[]::new);
        if (segments.length == 0)
            return new String[] { "report" };
        return segments;
    }

    private static ReportNavigationItem createFolderItem(Item parent, String name, String path)
    {
        return new ReportNavigationItem(parent, name,
            "hibiscus.navi.reports.folder." + id(path),
            "folder.png", "folder-open.png", null, true);
    }

    private static void sortRecursively(ReportNavigationItem item)
    {
        for (Item child : item.children())
        {
            if (child instanceof ReportNavigationItem reportNavigationItem)
                sortRecursively(reportNavigationItem);
        }
        item.sortChildren(ITEM_NAME_ORDER);
    }

    private static int compareItemsByName(Item left, Item right)
    {
        try
        {
            int result = COLLATOR.compare(name(left), name(right));
            if (result != 0)
                return result;
            return id(left).compareTo(id(right));
        }
        catch (RemoteException e)
        {
            throw new IllegalStateException("Reports-Navigation konnte nicht sortiert werden", e);
        }
    }

    private static String name(Item item) throws RemoteException
    {
        String name = item == null ? null : item.getName();
        return name == null ? "" : name;
    }

    private static String id(Item item) throws RemoteException
    {
        String id = item == null ? null : item.getID();
        return id == null ? "" : id;
    }

    static ReportNavigationItem createReportItem(Item parent, DynamicReport report)
    {
        return createReportItem(parent, report, report.displayName());
    }

    private static ReportNavigationItem createReportItem(Item parent, DynamicReport report, String name)
    {
        return new ReportNavigationItem(parent, name,
            "hibiscus.navi.reports.report." + id(report),
            "office-chart-area.png", new OpenReportAction(report));
    }

    static String id(DynamicReport report)
    {
        String name = report == null ? null : report.displayName();
        String normalized = name == null ? "report" : name.toLowerCase(Locale.ROOT)
            .replace('\\', '-').replace('/', '-')
            .replaceAll("[^a-z0-9._-]+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
        if (normalized.isBlank())
            normalized = "report";
        String source = report == null || report.path() == null ? normalized : report.path().toString();
        return normalized + "-" + Integer.toHexString(source.hashCode());
    }

    private static String id(String value)
    {
        String normalized = value == null ? "folder" : value.toLowerCase(Locale.ROOT)
            .replace('\\', '-').replace('/', '-')
            .replaceAll("[^a-z0-9._-]+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
        if (normalized.isBlank())
            normalized = "folder";
        return normalized + "-" + Integer.toHexString((value == null ? normalized : value).hashCode());
    }

    private static final class OpenReportAction implements Action
    {
        private final DynamicReport report;

        private OpenReportAction(DynamicReport report)
        {
            this.report = report;
        }

        @Override
        public void handleAction(Object context) throws ApplicationException
        {
            GUI.startView(DynamicReportsView.class, report);
        }
    }
}
