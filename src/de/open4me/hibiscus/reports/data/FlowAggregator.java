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
        Map<String, MutableExpenseNode> expenses = new LinkedHashMap<>();
        for (PathTotal total : totals.values())
        {
            if (Math.abs(total.amount) < 0.000001d)
                continue;
            List<CategoryInfo> path = total.path;
            CategoryInfo root = path.get(0);
            CategoryInfo leaf = path.get(path.size() - 1);
            if (total.amount > 0)
            {
                String key = leaf.id();
                String name = path.size() == 1 ? root.name() : path.subList(1, path.size()).stream()
                    .map(CategoryInfo::name).reduce((a, b) -> a + " > " + b).orElse(root.name());
                MutableValue value = incomes.computeIfAbsent(key,
                    ignored -> new MutableValue(key, name, color(leaf, DEFAULT_INCOME_COLOR)));
                value.amount += total.amount;
            }
            else
                addExpense(expenses, path, Math.abs(total.amount));
        }

        if (unassignedIncome > 0d)
        {
            MutableValue value = new MutableValue("__unassigned_income__", "Unkategorisierte Einnahmen", UNASSIGNED_COLOR);
            value.amount = unassignedIncome;
            incomes.put(value.key, value);
        }
        if (unassignedExpense > 0d)
            expenses.put("__unassigned_expense__", MutableExpenseNode.unassigned(unassignedExpense));

        Comparator<FlowReport.Value> valueOrder = Comparator.comparingDouble(FlowReport.Value::amount).reversed()
            .thenComparing(FlowReport.Value::name);
        Comparator<FlowReport.ExpenseNode> expenseOrder = Comparator.comparingDouble(FlowReport.ExpenseNode::amount)
            .reversed().thenComparing(FlowReport.ExpenseNode::name);
        List<FlowReport.Value> incomeValues = incomes.values().stream().map(MutableValue::freeze).sorted(valueOrder).toList();
        List<FlowReport.ExpenseNode> expenseNodes = expenses.values().stream().map(MutableExpenseNode::freeze)
            .sorted(expenseOrder).toList();
        return new FlowReport(incomeValues, expenseNodes, monthCount(from, to));
    }

    public static int monthCount(LocalDate from, LocalDate to)
    {
        if (from == null || to == null || to.isBefore(from))
            throw new IllegalArgumentException("Das Enddatum muss am oder nach dem Startdatum liegen");
        return Math.toIntExact(ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) + 1);
    }

    private static void addExpense(Map<String, MutableExpenseNode> roots, List<CategoryInfo> path, double amount)
    {
        MutableExpenseNode current = roots.computeIfAbsent(path.get(0).id(),
            ignored -> new MutableExpenseNode(path.get(0), path.get(0).id(), DEFAULT_EXPENSE_COLOR));
        current.amount += amount;
        for (int index = 1; index < path.size(); index++)
        {
            CategoryInfo category = path.get(index);
            String key = current.key + "/" + category.id();
            current = current.children.computeIfAbsent(category.id(),
                ignored -> new MutableExpenseNode(category, key, DEFAULT_SUB_EXPENSE_COLOR));
            current.amount += amount;
        }
        current.directAmount += amount;
    }

    private static int color(CategoryInfo category, int fallback)
    {
        return category == null || category.color() == null ? fallback : category.color();
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
            return new FlowReport.Value(key, name, amount, color, false);
        }
    }

    private static final class MutableExpenseNode
    {
        private final CategoryInfo category;
        private final String key;
        private final int fallbackColor;
        private final Map<String, MutableExpenseNode> children = new LinkedHashMap<>();
        private double amount;
        private double directAmount;

        private MutableExpenseNode(CategoryInfo category, String key, int fallbackColor)
        {
            this.category = category;
            this.key = key;
            this.fallbackColor = fallbackColor;
        }

        private static MutableExpenseNode unassigned(double amount)
        {
            MutableExpenseNode node = new MutableExpenseNode(null, "__unassigned_expense__", UNASSIGNED_COLOR);
            node.amount = amount;
            node.directAmount = amount;
            return node;
        }

        private FlowReport.ExpenseNode freeze()
        {
            Comparator<FlowReport.ExpenseNode> order = Comparator.comparingDouble(FlowReport.ExpenseNode::amount)
                .reversed().thenComparing(FlowReport.ExpenseNode::name);
            List<FlowReport.ExpenseNode> frozenChildren = new ArrayList<>();
            for (MutableExpenseNode child : children.values())
                frozenChildren.add(child.freeze());
            if (directAmount > 0d && !children.isEmpty())
            {
                frozenChildren.add(new FlowReport.ExpenseNode(key + "/__direct__", category == null ? null : category.id(),
                    "Sonstiges", directAmount, color(category, fallbackColor), false, List.of()));
            }
            frozenChildren.sort(order);
            String name = category == null ? "Unkategorisierte Ausgaben" : category.name();
            String categoryId = category == null && key.startsWith("__unassigned_") ? key
                : category == null ? null : category.id();
            return new FlowReport.ExpenseNode(key, categoryId, name, amount, color(category, fallbackColor),
                !frozenChildren.isEmpty(), frozenChildren);
        }
    }
}
