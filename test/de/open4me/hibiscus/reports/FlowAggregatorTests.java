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
        preservesAllExpenseLevelsAndDirectBookings();
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
            booking(3000, income, salary), booking(-200, income, salary),
            booking(-800, living, groceries), booking(100, living, groceries), booking(-250, leisure)),
            LocalDate.of(2025, 1, 15), LocalDate.of(2025, 12, 20));

        checkEquals(2800d, report.incomeTotal(), "income total");
        checkEquals(950d, report.expenseTotal(), "expense total");
        checkEquals("Gehalt", report.incomes().get(0).name(), "income label");
        checkEquals("45 Lebenshaltung", report.expenses().get(0).name(), "expense ordering");
        checkEquals("Supermarkt", report.expenses().get(0).children().get(0).name(), "child label");
        checkEquals(0x654321, report.expenses().get(0).color(), "custom category color");
        checkEquals(12, report.monthCount(), "month count");
    }

    private static void preservesAllExpenseLevelsAndDirectBookings()
    {
        CategoryInfo housing = category("44", "44 Wohnen", false, 0x654321);
        CategoryInfo consumption = category("441", "Verbrauch", false, 0x123456);
        CategoryInfo electricity = category("4411", "Strom", false, null);
        CategoryInfo provider = category("44111", "Anbieter", false, null);
        CategoryInfo garden = category("442", "Garten", false, null);
        FlowReport report = new FlowAggregator().aggregate(List.of(
            booking(-40, housing, consumption, electricity, provider), booking(-20, housing, garden),
            booking(-10, housing)), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        FlowReport.ExpenseNode group = report.expenses().get(0);
        checkEquals(70d, group.amount(), "root total");
        FlowReport.ExpenseNode consumptionNode = child(group, "44/441");
        checkEquals(40d, consumptionNode.amount(), "second level total");
        checkEquals(40d, child(child(consumptionNode, "44/441/4411"), "44/441/4411/44111").amount(),
            "deep leaf total");
        FlowReport.ExpenseNode direct = child(group, "44/__direct__");
        checkEquals("Sonstiges", direct.name(), "direct booking label");
        checkEquals("44", direct.categoryId(), "direct booking category");
        check(!direct.includeChildren(), "direct booking must not include children");
    }

    private static void filtersPendingAndSkippedAndShowsUnassigned()
    {
        CategoryInfo neutral = category("99", "99 Neutral", false, null);
        CategoryInfo root = category("40", "Kosten", false, null);
        CategoryInfo skipped = category("401", "Ignorieren", true, null);
        CategoryInfo accepted = category("402", "Akzeptiert", false, null);
        FlowReport report = new FlowAggregator().aggregate(List.of(
            new BookingRecord(-10, List.of(), false), new BookingRecord(25, List.of(), false),
            booking(-20, neutral), new BookingRecord(-30, List.of(root, accepted), true),
            booking(-40, root, skipped), booking(-50, root, accepted)),
            LocalDate.of(2025, 5, 10), LocalDate.of(2025, 5, 20));

        checkEquals(25d, report.incomeTotal(), "unassigned income total");
        checkEquals(80d, report.expenseTotal(), "filtered expense total");
        FlowReport.ExpenseNode unassigned = report.expenses().stream()
            .filter(group -> group.key().equals("__unassigned_expense__")).findFirst()
            .orElseThrow(() -> new AssertionError("missing unassigned expense group"));
        checkEquals(10d, unassigned.amount(), "unassigned expense amount");
    }

    private static void calculatesInclusiveMonthCount()
    {
        checkEquals(1, FlowAggregator.monthCount(LocalDate.of(2025, 3, 15), LocalDate.of(2025, 3, 15)), "one month");
        checkEquals(14, FlowAggregator.monthCount(LocalDate.of(2024, 12, 31), LocalDate.of(2026, 1, 1)), "cross-year months");
    }

    private static FlowReport.ExpenseNode child(FlowReport.ExpenseNode parent, String key)
    {
        return parent.children().stream().filter(node -> node.key().equals(key)).findFirst()
            .orElseThrow(() -> new AssertionError("missing child " + key));
    }

    private static BookingRecord booking(double amount, CategoryInfo... path)
    {
        return new BookingRecord(amount, List.of(path), false);
    }

    private static CategoryInfo category(String id, String name, boolean skip, Integer color)
    {
        return new CategoryInfo(id, name, skip, color);
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
