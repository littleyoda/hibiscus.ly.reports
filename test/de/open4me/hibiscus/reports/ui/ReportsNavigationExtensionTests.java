package de.open4me.hibiscus.reports.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import de.open4me.hibiscus.reports.automation.model.Automation;
import de.open4me.hibiscus.reports.automation.model.MissedTriggerPolicy;
import de.open4me.hibiscus.reports.automation.model.RunMode;
import de.open4me.hibiscus.reports.model.DynamicReport;
import de.willuhn.jameica.gui.Item;
import de.willuhn.jameica.gui.NavigationItem;

public final class ReportsNavigationExtensionTests
{
    private ReportsNavigationExtensionTests()
    {
    }

    public static void run()
    {
        try
        {
            buildsNestedReportNavigation();
            buildsNestedAutomationNavigation();
        }
        catch (Exception e)
        {
            throw new AssertionError("reports navigation tests failed", e);
        }
    }

    private static void buildsNestedReportNavigation() throws Exception
    {
        ReportNavigationItem root = new ReportNavigationItem(null, "Reports",
            ReportsNavigationExtension.REPORTS_ROOT_ID, "folder.png", "folder-open.png",
            new OpenDynamicReportsViewAction(), true);

        ReportsNavigationExtension.addReportChildren(root, List.of(
            report("zebra"),
            report("steuer/2026/salden"),
            report("steuer/2026/details"),
            report("foo"),
            report("steuer/2025/archiv"),
            report("alpha")));

        List<NavigationItem> rootChildren = children(root);
        checkEquals(List.of("alpha", "foo", "steuer", "zebra"), names(rootChildren), "root children");
        check(rootChildren.get(0).getAction() != null, "root report has action");
        check(rootChildren.get(2).getAction() == null, "folder has no action");

        List<NavigationItem> steuerChildren = children(rootChildren.get(2));
        checkEquals(List.of("2025", "2026"), names(steuerChildren), "steuer children");

        List<NavigationItem> year2025 = children(steuerChildren.get(0));
        checkEquals(List.of("archiv"), names(year2025), "2025 reports");
        check(year2025.get(0).getAction() != null, "nested report has action");

        List<NavigationItem> year2026 = children(steuerChildren.get(1));
        checkEquals(List.of("details", "salden"), names(year2026), "2026 reports");
        check(year2026.stream().allMatch(item -> {
            try
            {
                return item.getAction() != null;
            }
            catch (Exception e)
            {
                throw new AssertionError(e);
            }
        }), "all 2026 reports have actions");
    }

    private static void buildsNestedAutomationNavigation() throws Exception
    {
        ReportNavigationItem root = new ReportNavigationItem(null, "Automatisierung",
            ReportsNavigationExtension.AUTOMATION_ROOT_ID, "folder.png", "folder-open.png",
            new OpenAutomationViewAction(), true);

        ReportsNavigationExtension.addAutomationChildren(root, List.of(
            automation("1", "zebra"),
            automation("2", "sync/depot/scalable"),
            automation("3", "sync/banken"),
            automation("4", "alpha")));

        List<NavigationItem> rootChildren = children(root);
        checkEquals(List.of("alpha", "sync", "zebra"), names(rootChildren), "automation root children");
        check(rootChildren.get(0).getAction() != null, "root automation has action");
        check(rootChildren.get(1).getAction() == null, "automation folder has no action");

        List<NavigationItem> syncChildren = children(rootChildren.get(1));
        checkEquals(List.of("banken", "depot"), names(syncChildren), "sync automation children");
        check(syncChildren.get(0).getAction() != null, "nested automation has action");

        List<NavigationItem> depotChildren = children(syncChildren.get(1));
        checkEquals(List.of("scalable"), names(depotChildren), "depot automation children");
        check(depotChildren.get(0).getAction() != null, "deep automation has action");
    }

    private static DynamicReport report(String displayName)
    {
        return new DynamicReport(Path.of("/tmp/reports", displayName + ".html"), displayName);
    }

    private static Automation automation(String id, String name)
    {
        return new Automation(id, name, "", true, RunMode.SINGLE, MissedTriggerPolicy.IGNORIEREN, "", 100);
    }

    private static List<NavigationItem> children(NavigationItem item) throws Exception
    {
        List<NavigationItem> result = new ArrayList<>();
        for (Item child : ((ReportNavigationItem) item).children())
            result.add((NavigationItem) child);
        return result;
    }

    private static List<String> names(List<NavigationItem> items) throws Exception
    {
        List<String> result = new ArrayList<>();
        for (NavigationItem item : items)
            result.add(item.getName());
        return result;
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }

    private static void checkEquals(Object expected, Object actual, String message)
    {
        if (!java.util.Objects.equals(expected, actual))
            throw new AssertionError(message + " expected <" + expected + "> but was <" + actual + ">");
    }
}
