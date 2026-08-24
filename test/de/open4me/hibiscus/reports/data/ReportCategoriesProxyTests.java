package de.open4me.hibiscus.reports.data;

import java.util.List;

import de.open4me.hibiscus.reports.model.CategoryInfo;
import de.open4me.hibiscus.reports.model.ReportCategory;

public final class ReportCategoriesProxyTests
{
    private ReportCategoriesProxyTests() { }

    public static void run()
    {
        exposesMetadataAndHierarchy();
        promotesOrphansAndBreaksCycles();
    }

    private static void exposesMetadataAndHierarchy()
    {
        CategoryInfo root = category("1", "Ausgaben", "10", "Root-Notiz", false);
        CategoryInfo child = category("2", "Lebensmittel", "20", "Bio bevorzugen", false);
        CategoryInfo skipped = category("3", "Ignoriert", "30", "", true);
        CategoryInfo descendant = category("4", "Kind", "40", "", false);
        ReportCategoriesProxy proxy = new ReportCategoriesProxy(() -> List.of(
            List.of(root), List.of(root, child), List.of(skipped), List.of(skipped, descendant)));

        checkEquals(4, proxy.size(), "category count");
        checkEquals(2, proxy.getWurzeln().size(), "root count");
        ReportCategory result = proxy.byId("2");
        checkEquals("Bio bevorzugen", result.getNotiz(), "category note");
        checkEquals("rechnung.*", result.getSuchbegriff(), "category pattern");
        check(result.isRegex(), "regex flag");
        checkEquals("Ausgaben > Lebensmittel", result.getPath(), "category path");
        checkEquals(List.of("1", "2"), result.getPathIds(), "category path ids");
        checkEquals("#123456", result.getColorHex(), "category hex color");
        check(proxy.byId("4").isSkipReportsEffective(), "inherited skip flag");
    }

    private static void promotesOrphansAndBreaksCycles()
    {
        CategoryInfo first = category("a", "A", "", "", false);
        CategoryInfo second = category("b", "B", "", "", false);
        ReportCategoriesProxy proxy = new ReportCategoriesProxy(() -> List.of(
            List.of(first, second, first), List.of(second, first, second)));

        checkEquals(2, proxy.size(), "cyclic categories retained");
        check(!proxy.getWurzeln().isEmpty(), "cycle produces a root");
    }

    private static CategoryInfo category(String id, String name, String number, String note, boolean skipped)
    {
        return new CategoryInfo(id, name, number, note, "rechnung.*", true, "ausgabe", 0,
            "17", "Girokonto", null, skipped, true, 0x123456);
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }

    private static void checkEquals(Object expected, Object actual, String message)
    {
        if (!java.util.Objects.equals(expected, actual))
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
    }
}
