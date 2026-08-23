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

        for (VisibleValue income : bundleValues(report.incomes(), incomeTotal, threshold,
            "__other__", "Sonstige Einnahmen", OTHER_COLOR))
        {
            String id = "income:" + income.key();
            nodes.add(new SankeyGraph.Node(id, income.name(), income.amount(), incomeTotal,
                income.color(), 0, null, transactionFilter(income.key(), false, 1, income.categoryRules())));
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

        appendExpenses("available", report.expenses(), expenseTotal, threshold, expanded, 2, available,
            "__other__", "Sonstige Ausgaben", nodes, links);

        double surplus = Math.max(0d, incomeTotal - expenseTotal);
        if (surplus > 0d)
        {
            nodes.add(new SankeyGraph.Node("surplus", "Überschuss", surplus, incomeTotal,
                SURPLUS_COLOR, 2, null, null));
            links.add(new SankeyGraph.Link("available", "surplus", surplus));
        }
        return new SankeyGraph(nodes, links, report.monthCount(), incomeTotal, expenseTotal);
    }

    private static void appendExpenses(String parentId, List<FlowReport.ExpenseNode> expenses, double base,
                                       double threshold, Set<String> expanded, int layer, double percentageBase,
                                       String otherKey, String otherName, List<SankeyGraph.Node> nodes,
                                       List<SankeyGraph.Link> links)
    {
        for (VisibleExpenseNode expense : bundleExpenses(expenses, base, threshold, otherKey, otherName))
        {
            String id = "expense:" + expense.key();
            boolean expandable = !expense.children().isEmpty() && !expense.other();
            SankeyGraph.TransactionFilter filter = transactionFilter(expense.categoryId(), expense.includeChildren(),
                -1, expense.categoryRules());
            nodes.add(new SankeyGraph.Node(id, expense.name(), expense.amount(), percentageBase, expense.color(),
                layer, expandable ? expense.key() : null, filter));
            links.add(new SankeyGraph.Link(parentId, id, expense.amount()));
            if (expandable && expanded.contains(expense.key()))
                appendExpenses(id, expense.children(), expense.amount(), threshold, expanded, layer + 1,
                    percentageBase, expense.key() + "/__other__", "Sonstige", nodes, links);
        }
    }

    private static SankeyGraph.TransactionFilter transactionFilter(String categoryId, boolean includeChildren,
                                                                   int sign,
                                                                   List<SankeyGraph.CategoryRule> categoryRules)
    {
        if (categoryRules != null && !categoryRules.isEmpty())
            return new SankeyGraph.TransactionFilter(null, false, false, sign, categoryRules);
        if (categoryId == null || categoryId.isBlank())
            return null;
        if (categoryId.startsWith("__unassigned_"))
            return new SankeyGraph.TransactionFilter(null, false, true, sign);
        return new SankeyGraph.TransactionFilter(categoryId, includeChildren, false, 0);
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
        result.sort(Comparator.comparingDouble(VisibleValue::amount).reversed().thenComparing(VisibleValue::name));
        return result;
    }

    private static List<VisibleExpenseNode> bundleExpenses(List<FlowReport.ExpenseNode> values, double base,
                                                            double threshold, String otherKey, String otherName)
    {
        if (threshold <= 0d || base <= 0d)
            return values.stream().map(VisibleExpenseNode::from).toList();
        List<VisibleExpenseNode> result = new ArrayList<>();
        List<SankeyGraph.CategoryRule> otherRules = new ArrayList<>();
        double other = 0d;
        for (FlowReport.ExpenseNode value : values)
        {
            if (!isUnassigned(value.key()) && value.amount() / base < threshold)
            {
                other += value.amount();
                otherRules.add(new SankeyGraph.CategoryRule(value.categoryId(), value.includeChildren()));
            }
            else
            {
                result.add(VisibleExpenseNode.from(value));
            }
        }
        if (other > 0d)
            result.add(new VisibleExpenseNode(otherKey, null, otherName, other, OTHER_COLOR, false,
                List.of(), otherRules, true));
        result.sort(Comparator.comparingDouble(VisibleExpenseNode::amount).reversed()
            .thenComparing(VisibleExpenseNode::name));
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

    private record VisibleExpenseNode(String key, String categoryId, String name, double amount, int color,
                                      boolean includeChildren, List<FlowReport.ExpenseNode> children,
                                      List<SankeyGraph.CategoryRule> categoryRules, boolean other)
    {
        private VisibleExpenseNode
        {
            children = children == null ? List.of() : List.copyOf(children);
            categoryRules = categoryRules == null ? List.of() : List.copyOf(categoryRules);
        }

        private static VisibleExpenseNode from(FlowReport.ExpenseNode value)
        {
            return new VisibleExpenseNode(value.key(), value.categoryId(), value.name(), value.amount(), value.color(),
                value.includeChildren(), value.children(), List.of(), false);
        }
    }
}
