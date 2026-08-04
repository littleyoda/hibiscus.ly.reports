package de.open4me.hibiscus.reports;

import java.time.LocalDate;
import java.util.List;

import de.open4me.hibiscus.reports.data.FlowAggregator;
import de.open4me.hibiscus.reports.model.BookingRecord;
import de.open4me.hibiscus.reports.model.CategoryInfo;
import de.open4me.hibiscus.reports.model.FlowReport;

final class FlowAggregatorTests
{
    static void run()
    {
        aggregatesAndNetsCategoryPaths();
        aggregatesExpensesByFirstChildCategory();
        filtersPendingAndSkippedAndShowsUnassigned();
        calculatesInclusiveMonthCount();
    }

    private static void aggregatesAndNetsCategoryPaths()
    {
        CategoryInfo income = category("30", "30 Einnahmen", false, 0x123456);
        CategoryInfo salary = category("31", "Gehalt", false, null);
        CategoryInfo living = category("45", "45 Lebenshaltung", false, 0x654321);
        CategoryInfo groceries = category("451", "Supermarkt", false, null);
        CategoryInfo leisure = category("47", "47 Freizeit", false, null);

        FlowReport report = new FlowAggregator().aggregate(List.of(
            booking(3000, income, salary),
            booking(-200, income, salary),
            booking(-800, living, groceries),
            booking(100, living, groceries),
            booking(-250, leisure)),
            LocalDate.of(2025, 1, 15), LocalDate.of(2025, 12, 20));

        checkEquals(2800d, report.incomeTotal(), "income total");
        checkEquals(950d, report.expenseTotal(), "expense total");
        checkEquals("Gehalt", report.incomes().get(0).name(), "income label");
        checkEquals(2, report.expenses().size(), "expense groups");
        checkEquals("45 Lebenshaltung", report.expenses().get(0).name(), "expense ordering");
        checkEquals("Supermarkt", report.expenses().get(0).children().get(0).name(), "child label");
        checkEquals(0x654321, report.expenses().get(0).color(), "custom category color");
        checkEquals(12, report.monthCount(), "month count");
    }

    private static void aggregatesExpensesByFirstChildCategory()
    {
        CategoryInfo housing = category("44", "44 Wohnen", false, 0x654321);
        CategoryInfo consumption = category("441", "Verbrauch", false, 0x123456);
        CategoryInfo electricity = category("4411", "Strom", false, null);
        CategoryInfo water = category("4412", "Wasser", false, null);
        CategoryInfo garden = category("442", "Garten", false, null);

        FlowReport report = new FlowAggregator().aggregate(List.of(
            booking(-40, housing, consumption, electricity),
            booking(-30, housing, consumption, water),
            booking(-20, housing, garden),
            booking(-10, housing)),
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        FlowReport.ExpenseGroup group = report.expenses().get(0);
        checkEquals("44 Wohnen", group.name(), "collapsed group name");
        checkEquals(100d, group.amount(), "collapsed group total");
        checkEquals(3, group.children().size(), "collapsed first child count");
        checkChild(group, "441", "Verbrauch", 70d, true);
        checkChild(group, "442", "Garten", 20d, true);
        checkChild(group, "44", "Sonstiges", 10d, false);
    }

    private static void filtersPendingAndSkippedAndShowsUnassigned()
    {
        CategoryInfo neutral = category("99", "99 Neutral", false, null);
        CategoryInfo root = category("40", "Kosten", false, null);
        CategoryInfo skipped = category("401", "Ignorieren", true, null);
        CategoryInfo accepted = category("402", "Akzeptiert", false, null);

        FlowReport report = new FlowAggregator().aggregate(List.of(
            new BookingRecord(-10, List.of(), false),
            new BookingRecord(25, List.of(), false),
            booking(-20, neutral),
            new BookingRecord(-30, List.of(root, accepted), true),
            booking(-40, root, skipped),
            booking(-50, root, accepted)),
            LocalDate.of(2025, 5, 10), LocalDate.of(2025, 5, 20));

        checkEquals(25d, report.incomeTotal(), "unassigned income total");
        checkEquals("Unkategorisierte Einnahmen", report.incomes().get(0).name(), "unassigned income label");
        checkEquals(80d, report.expenseTotal(), "filtered expense total");
        checkEquals(3, report.expenses().size(), "filtered group count");
        check(report.expenses().stream().anyMatch(group -> group.name().equals("99 Neutral")),
            "category names must not trigger exclusions");
        FlowReport.ExpenseGroup unassigned = report.expenses().stream()
            .filter(group -> group.key().equals("__unassigned_expense__")).findFirst()
            .orElseThrow(() -> new AssertionError("missing unassigned expense group"));
        checkEquals(10d, unassigned.amount(), "unassigned expense amount");
        checkEquals("Diverse", unassigned.children().get(0).name(), "unassigned expense child");
    }

    private static void calculatesInclusiveMonthCount()
    {
        checkEquals(1, FlowAggregator.monthCount(LocalDate.of(2025, 3, 15), LocalDate.of(2025, 3, 15)),
            "one month");
        checkEquals(14, FlowAggregator.monthCount(LocalDate.of(2024, 12, 31), LocalDate.of(2026, 1, 1)),
            "cross-year months");
        boolean failed = false;
        try
        {
            FlowAggregator.monthCount(LocalDate.of(2025, 2, 2), LocalDate.of(2025, 2, 1));
        }
        catch (IllegalArgumentException expected)
        {
            failed = true;
        }
        check(failed, "reverse range must fail");
    }

    private static BookingRecord booking(double amount, CategoryInfo... path)
    {
        return new BookingRecord(amount, List.of(path), false);
    }

    private static CategoryInfo category(String id, String name, boolean skip, Integer color)
    {
        return new CategoryInfo(id, name, skip, color);
    }

    private static void checkChild(FlowReport.ExpenseGroup group, String key, String name, double amount,
                                   boolean includeChildren)
    {
        FlowReport.Value child = group.children().stream().filter(value -> value.key().equals(key)).findFirst()
            .orElseThrow(() -> new AssertionError("missing child " + key));
        checkEquals(name, child.name(), "child name " + key);
        checkEquals(amount, child.amount(), "child amount " + key);
        checkEquals(includeChildren, child.includeChildren(), "child include children " + key);
    }

    static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }

    static void checkEquals(double expected, double actual, String message)
    {
        if (Math.abs(expected - actual) > 0.00001d)
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }

    static void checkEquals(Object expected, Object actual, String message)
    {
        if (!expected.equals(actual))
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
}
