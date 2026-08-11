package de.open4me.hibiscus.reports.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.open4me.hibiscus.reports.model.FlowReport;
import de.open4me.hibiscus.reports.model.SankeyGraph;

public class SankeyGraphBuilder
{
    public static final int CENTRAL_COLOR = 0x2ca02c;
    public static final int SURPLUS_COLOR = 0x1f77b4;
    public static final int DEFICIT_COLOR = 0xb22222;
    public static final int OTHER_COLOR = 0x777777;

    public SankeyGraph build(FlowReport report, Set<String> expandedCategories, double thresholdPercent)
    {
        Set<String> expanded = expandedCategories == null ? Set.of() : new HashSet<>(expandedCategories);
        double threshold = Math.max(0d, thresholdPercent) / 100d;
        double incomeTotal = report.incomeTotal();
        double expenseTotal = report.expenseTotal();
        double available = Math.max(incomeTotal, expenseTotal);

        List<SankeyGraph.Node> nodes = new ArrayList<>();
        List<SankeyGraph.Link> links = new ArrayList<>();
        if (available <= 0d)
            return new SankeyGraph(nodes, links, report.monthCount(), incomeTotal, expenseTotal);
        List<VisibleValue> incomes = bundleValues(report.incomes(), incomeTotal, threshold,
            "__other__", "Sonstige Einnahmen", OTHER_COLOR);

        for (VisibleValue income : incomes)
        {
            String id = "income:" + income.key();
            SankeyGraph.TransactionFilter filter = transactionFilter(income.key(), false, 1,
                income.categoryRules());
            nodes.add(new SankeyGraph.Node(id, income.name(), income.amount(), incomeTotal,
                income.color(), 0, null, filter));
            links.add(new SankeyGraph.Link(id, "available", income.amount()));
        }

        double deficit = Math.max(0d, expenseTotal - incomeTotal);
        if (deficit > 0d)
        {
            nodes.add(new SankeyGraph.Node("deficit", "Defizit", deficit, available,
                DEFICIT_COLOR, 0, null, null));
            links.add(new SankeyGraph.Link("deficit", "available", deficit));
        }
        String centralName = deficit > 0d ? "Benötigte Mittel" : "Verfügbare Mittel";
        nodes.add(new SankeyGraph.Node("available", centralName, available, available,
            CENTRAL_COLOR, 1, null, null));

        List<VisibleExpenseGroup> visibleGroups = bundleGroups(report.expenses(), expenseTotal, threshold);
        for (VisibleExpenseGroup group : visibleGroups)
        {
            String id = "expense:" + group.key();
            String expandable = group.key().equals("__other__") ? null : group.key();
            SankeyGraph.TransactionFilter filter = transactionFilter(group.key(), true, -1,
                group.categoryRules());
            nodes.add(new SankeyGraph.Node(id, group.name(), group.amount(), available,
                group.color(), 2, expandable, filter));
            links.add(new SankeyGraph.Link("available", id, group.amount()));

            if (expandable != null && expanded.contains(group.key()))
            {
                List<VisibleValue> children = bundleValues(group.children(), expenseTotal, threshold,
                    group.key() + ":__other__", "Sonstige", OTHER_COLOR);
                for (VisibleValue child : children)
                {
                    String childId = "sub:" + child.key();
                    SankeyGraph.TransactionFilter childFilter = transactionFilter(child.key(),
                        child.includeChildren(), -1, child.categoryRules());
                    nodes.add(new SankeyGraph.Node(childId, child.name(), child.amount(), available,
                        child.color(), 3, null, childFilter));
                    links.add(new SankeyGraph.Link(id, childId, child.amount()));
                }
            }
        }

        double surplus = Math.max(0d, incomeTotal - expenseTotal);
        if (surplus > 0d)
        {
            nodes.add(new SankeyGraph.Node("surplus", "Überschuss", surplus, incomeTotal,
                SURPLUS_COLOR, 2, null, null));
            links.add(new SankeyGraph.Link("available", "surplus", surplus));
        }
        return new SankeyGraph(nodes, links, report.monthCount(), incomeTotal, expenseTotal);
    }

    private static SankeyGraph.TransactionFilter transactionFilter(String key, boolean includeChildren,
                                                                   int sign,
                                                                   List<SankeyGraph.CategoryRule> categoryRules)
    {
        if (categoryRules != null && !categoryRules.isEmpty())
            return new SankeyGraph.TransactionFilter(null, false, false, sign, categoryRules);
        if (key == null || key.contains("__other__"))
            return null;
        if (key.startsWith("__unassigned_"))
            return new SankeyGraph.TransactionFilter(null, false, true, sign);
        return new SankeyGraph.TransactionFilter(key, includeChildren, false, 0);
    }

    private static List<VisibleValue> bundleValues(List<FlowReport.Value> values, double base,
                                                    double threshold, String otherKey,
                                                    String otherName, int otherColor)
    {
        if (threshold <= 0d || base <= 0d)
            return values.stream().map(VisibleValue::from).toList();
        List<VisibleValue> result = new ArrayList<>();
        List<SankeyGraph.CategoryRule> otherRules = new ArrayList<>();
        double other = 0d;
        for (FlowReport.Value value : values)
        {
            if (!isUnassigned(value.key()) && value.amount() / base < threshold)
            {
                other += value.amount();
                otherRules.add(new SankeyGraph.CategoryRule(value.key(), value.includeChildren()));
            }
            else
                result.add(VisibleValue.from(value));
        }
        if (other > 0d)
            result.add(new VisibleValue(otherKey, otherName, other, otherColor, false, otherRules));
        result.sort(Comparator.comparingDouble(VisibleValue::amount).reversed()
            .thenComparing(VisibleValue::name));
        return result;
    }

    private static List<VisibleExpenseGroup> bundleGroups(List<FlowReport.ExpenseGroup> groups,
                                                           double base, double threshold)
    {
        if (threshold <= 0d || base <= 0d)
            return groups.stream().map(VisibleExpenseGroup::from).toList();
        List<VisibleExpenseGroup> result = new ArrayList<>();
        List<SankeyGraph.CategoryRule> otherRules = new ArrayList<>();
        double other = 0d;
        for (FlowReport.ExpenseGroup group : groups)
        {
            if (!isUnassigned(group.key()) && group.amount() / base < threshold)
            {
                other += group.amount();
                otherRules.add(new SankeyGraph.CategoryRule(group.key(), true));
            }
            else
                result.add(VisibleExpenseGroup.from(group));
        }
        if (other > 0d)
            result.add(new VisibleExpenseGroup("__other__", "Sonstige Ausgaben", other,
                OTHER_COLOR, List.of(), otherRules));
        result.sort(Comparator.comparingDouble(VisibleExpenseGroup::amount).reversed()
            .thenComparing(VisibleExpenseGroup::name));
        return result;
    }

    private static boolean isUnassigned(String key)
    {
        return key != null && key.startsWith("__unassigned_");
    }

    private record VisibleValue(String key, String name, double amount, int color, boolean includeChildren,
                                List<SankeyGraph.CategoryRule> categoryRules)
    {
        private VisibleValue
        {
            categoryRules = categoryRules == null ? List.of() : List.copyOf(categoryRules);
        }

        private static VisibleValue from(FlowReport.Value value)
        {
            return new VisibleValue(value.key(), value.name(), value.amount(), value.color(),
                value.includeChildren(), List.of());
        }
    }

    private record VisibleExpenseGroup(String key, String name, double amount, int color,
                                       List<FlowReport.Value> children,
                                       List<SankeyGraph.CategoryRule> categoryRules)
    {
        private VisibleExpenseGroup
        {
            children = children == null ? List.of() : List.copyOf(children);
            categoryRules = categoryRules == null ? List.of() : List.copyOf(categoryRules);
        }

        private static VisibleExpenseGroup from(FlowReport.ExpenseGroup group)
        {
            return new VisibleExpenseGroup(group.key(), group.name(), group.amount(), group.color(),
                group.children(), List.of());
        }
    }
}
