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
        List<FlowReport.Value> incomes = bundleValues(report.incomes(), incomeTotal, threshold,
            "__other__", "Sonstige Einnahmen", OTHER_COLOR);

        for (FlowReport.Value income : incomes)
        {
            String id = "income:" + income.key();
            SankeyGraph.TransactionFilter filter = transactionFilter(income.key(), false, 1);
            nodes.add(new SankeyGraph.Node(id, income.name(), income.amount(), incomeTotal,
                income.color(), 0, null, filter));
            links.add(new SankeyGraph.Link(id, "available", income.amount()));
        }

        double deficit = Math.max(0d, expenseTotal - incomeTotal);
        if (deficit > 0d)
        {
            nodes.add(new SankeyGraph.Node("deficit", "Defizit", deficit, incomeTotal,
                DEFICIT_COLOR, 0, null, null));
            links.add(new SankeyGraph.Link("deficit", "available", deficit));
        }
        String centralName = deficit > 0d ? "Benötigte Mittel" : "Verfügbare Mittel";
        nodes.add(new SankeyGraph.Node("available", centralName, available, available,
            CENTRAL_COLOR, 1, null, null));

        List<FlowReport.ExpenseGroup> visibleGroups = bundleGroups(report.expenses(), expenseTotal, threshold);
        for (FlowReport.ExpenseGroup group : visibleGroups)
        {
            String id = "expense:" + group.key();
            String expandable = group.key().equals("__other__") ? null : group.key();
            SankeyGraph.TransactionFilter filter = transactionFilter(group.key(), true, -1);
            nodes.add(new SankeyGraph.Node(id, group.name(), group.amount(), expenseTotal,
                group.color(), 2, expandable, filter));
            links.add(new SankeyGraph.Link("available", id, group.amount()));

            if (expandable != null && expanded.contains(group.key()))
            {
                List<FlowReport.Value> children = bundleValues(group.children(), expenseTotal, threshold,
                    group.key() + ":__other__", "Sonstige", OTHER_COLOR);
                for (FlowReport.Value child : children)
                {
                    String childId = "sub:" + child.key();
                    SankeyGraph.TransactionFilter childFilter = transactionFilter(child.key(), false, -1);
                    nodes.add(new SankeyGraph.Node(childId, child.name(), child.amount(), expenseTotal,
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
                                                                   int sign)
    {
        if (key == null || key.contains("__other__"))
            return null;
        if (key.startsWith("__unassigned_"))
            return new SankeyGraph.TransactionFilter(null, false, true, sign);
        return new SankeyGraph.TransactionFilter(key, includeChildren, false, 0);
    }

    private static List<FlowReport.Value> bundleValues(List<FlowReport.Value> values, double base,
                                                        double threshold, String otherKey,
                                                        String otherName, int otherColor)
    {
        if (threshold <= 0d || base <= 0d)
            return values;
        List<FlowReport.Value> result = new ArrayList<>();
        double other = 0d;
        for (FlowReport.Value value : values)
        {
            if (!isUnassigned(value.key()) && value.amount() / base < threshold)
                other += value.amount();
            else
                result.add(value);
        }
        if (other > 0d)
            result.add(new FlowReport.Value(otherKey, otherName, other, otherColor));
        result.sort(Comparator.comparingDouble(FlowReport.Value::amount).reversed()
            .thenComparing(FlowReport.Value::name));
        return result;
    }

    private static List<FlowReport.ExpenseGroup> bundleGroups(List<FlowReport.ExpenseGroup> groups,
                                                               double base, double threshold)
    {
        if (threshold <= 0d || base <= 0d)
            return groups;
        List<FlowReport.ExpenseGroup> result = new ArrayList<>();
        double other = 0d;
        for (FlowReport.ExpenseGroup group : groups)
        {
            if (!isUnassigned(group.key()) && group.amount() / base < threshold)
                other += group.amount();
            else
                result.add(group);
        }
        if (other > 0d)
            result.add(new FlowReport.ExpenseGroup("__other__", "Sonstige Ausgaben", other,
                OTHER_COLOR, List.of()));
        result.sort(Comparator.comparingDouble(FlowReport.ExpenseGroup::amount).reversed()
            .thenComparing(FlowReport.ExpenseGroup::name));
        return result;
    }

    private static boolean isUnassigned(String key)
    {
        return key != null && key.startsWith("__unassigned_");
    }
}
