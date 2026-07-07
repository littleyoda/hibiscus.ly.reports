package de.open4me.hibiscus.reports.data;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.open4me.hibiscus.reports.model.BookingRecord;
import de.open4me.hibiscus.reports.model.CategoryInfo;
import de.open4me.hibiscus.reports.model.FlowReport;

public class FlowAggregator
{
    public static final int DEFAULT_INCOME_COLOR = 0x239b56;
    public static final int DEFAULT_EXPENSE_COLOR = 0xe67e22;
    public static final int DEFAULT_SUB_EXPENSE_COLOR = 0xf39c12;
    public static final int UNASSIGNED_COLOR = 0x6c757d;

    public FlowReport aggregate(List<BookingRecord> bookings, LocalDate from, LocalDate to)
    {
        Map<String, PathTotal> totals = new LinkedHashMap<>();
        double unassignedIncome = 0d;
        double unassignedExpense = 0d;
        for (BookingRecord booking : bookings)
        {
            if (booking.pending())
                continue;

            if (booking.categoryPath().isEmpty())
            {
                if (booking.amount() > 0d)
                    unassignedIncome += booking.amount();
                else if (booking.amount() < 0d)
                    unassignedExpense += Math.abs(booking.amount());
                continue;
            }

            List<CategoryInfo> path = booking.categoryPath();
            if (path.stream().anyMatch(CategoryInfo::skipReports))
                continue;

            String key = path.stream().map(CategoryInfo::id).reduce((a, b) -> a + "/" + b).orElse("");
            totals.computeIfAbsent(key, ignored -> new PathTotal(path)).amount += booking.amount();
        }

        Map<String, MutableValue> incomes = new LinkedHashMap<>();
        Map<String, MutableExpense> expenses = new LinkedHashMap<>();
        for (Map.Entry<String, PathTotal> entry : totals.entrySet())
        {
            PathTotal total = entry.getValue();
            if (Math.abs(total.amount) < 0.000001d)
                continue;

            List<CategoryInfo> path = total.path;
            CategoryInfo root = path.get(0);
            CategoryInfo leaf = path.get(path.size() - 1);
            if (total.amount > 0)
            {
                String key = leaf.id();
                String name = path.size() == 1
                    ? root.name()
                    : path.subList(1, path.size()).stream().map(CategoryInfo::name)
                        .reduce((a, b) -> a + " > " + b).orElse(root.name());
                MutableValue value = incomes.computeIfAbsent(key,
                    ignored -> new MutableValue(key, name, color(leaf, DEFAULT_INCOME_COLOR)));
                value.amount += total.amount;
            }
            else
            {
                String rootKey = root.id();
                MutableExpense group = expenses.computeIfAbsent(rootKey,
                    ignored -> new MutableExpense(rootKey, root.name(), color(root, DEFAULT_EXPENSE_COLOR)));
                String childName = path.size() == 1
                    ? "Sonstiges"
                    : path.subList(1, path.size()).stream().map(CategoryInfo::name)
                        .reduce((a, b) -> a + " > " + b).orElse("Sonstiges");
                String childKey = leaf.id();
                MutableValue child = group.children.computeIfAbsent(childKey,
                    ignored -> new MutableValue(childKey, childName, color(leaf, DEFAULT_SUB_EXPENSE_COLOR)));
                child.amount += Math.abs(total.amount);
            }
        }

        if (unassignedIncome > 0d)
        {
            MutableValue value = new MutableValue("__unassigned_income__",
                "Unkategorisierte Einnahmen", UNASSIGNED_COLOR);
            value.amount = unassignedIncome;
            incomes.put(value.key, value);
        }
        if (unassignedExpense > 0d)
        {
            MutableExpense group = new MutableExpense("__unassigned_expense__",
                "Unkategorisierte Ausgaben", UNASSIGNED_COLOR);
            MutableValue child = new MutableValue("__unassigned_expense__/diverse",
                "Diverse", UNASSIGNED_COLOR);
            child.amount = unassignedExpense;
            group.children.put(child.key, child);
            expenses.put(group.key, group);
        }

        Comparator<FlowReport.Value> byAmount = Comparator.comparingDouble(FlowReport.Value::amount).reversed()
            .thenComparing(FlowReport.Value::name);
        List<FlowReport.Value> incomeValues = incomes.values().stream().map(MutableValue::freeze).sorted(byAmount).toList();
        List<FlowReport.ExpenseGroup> expenseGroups = new ArrayList<>();
        for (MutableExpense group : expenses.values())
        {
            List<FlowReport.Value> children = group.children.values().stream()
                .map(MutableValue::freeze).sorted(byAmount).toList();
            double amount = children.stream().mapToDouble(FlowReport.Value::amount).sum();
            expenseGroups.add(new FlowReport.ExpenseGroup(group.key, group.name, amount, group.color, children));
        }
        expenseGroups.sort(Comparator.comparingDouble(FlowReport.ExpenseGroup::amount).reversed()
            .thenComparing(FlowReport.ExpenseGroup::name));
        return new FlowReport(incomeValues, expenseGroups, monthCount(from, to));
    }

    public static int monthCount(LocalDate from, LocalDate to)
    {
        if (from == null || to == null || to.isBefore(from))
            throw new IllegalArgumentException("Das Enddatum muss am oder nach dem Startdatum liegen");
        return Math.toIntExact(ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) + 1);
    }

    private static int color(CategoryInfo category, int fallback)
    {
        return category.color() == null ? fallback : category.color();
    }

    private static final class PathTotal
    {
        private final List<CategoryInfo> path;
        private double amount;

        private PathTotal(List<CategoryInfo> path)
        {
            this.path = List.copyOf(path);
        }
    }

    private static final class MutableValue
    {
        private final String key;
        private final String name;
        private final int color;
        private double amount;

        private MutableValue(String key, String name, int color)
        {
            this.key = key;
            this.name = name;
            this.color = color;
        }

        private FlowReport.Value freeze()
        {
            return new FlowReport.Value(key, name, amount, color);
        }
    }

    private static final class MutableExpense
    {
        private final String key;
        private final String name;
        private final int color;
        private final Map<String, MutableValue> children = new LinkedHashMap<>();

        private MutableExpense(String key, String name, int color)
        {
            this.key = key;
            this.name = name;
            this.color = color;
        }
    }
}
