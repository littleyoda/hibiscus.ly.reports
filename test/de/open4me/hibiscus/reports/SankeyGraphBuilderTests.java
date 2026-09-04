package de.open4me.hibiscus.reports;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import de.open4me.hibiscus.reports.data.SankeyGraphBuilder;
import de.open4me.hibiscus.reports.model.FlowReport;
import de.open4me.hibiscus.reports.model.SankeyGraph;

final class SankeyGraphBuilderTests
{
    static void run()
    {
        createsDeepExpandableExpenseGraph();
        bundlesSmallFlowsOnTheCurrentLevel();
        excludesFlowsBeforeRecalculatingTheGraph();
        excludesAllFlowsRepresentedByAnOtherNode();
        keepsUnassignedFlowsVisibleBelowThreshold();
        leavesEmptyReportsEmpty();
    }

    private static void createsDeepExpandableExpenseGraph()
    {
        FlowReport.ExpenseNode provider = expense("housing/utility/electricity/provider", "provider", "Anbieter", 700);
        FlowReport.ExpenseNode electricity = expense("housing/utility/electricity", "electricity", "Strom", 700, provider);
        FlowReport.ExpenseNode utility = expense("housing/utility", "utility", "Verbrauch", 700, electricity);
        FlowReport.ExpenseNode direct = expense("housing/__direct__", "housing", "Sonstiges", 100, false);
        FlowReport.ExpenseNode housing = expense("housing", "housing", "Wohnen", 800, utility, direct);
        FlowReport report = new FlowReport(List.of(value("salary", "Gehalt", 1000)), List.of(housing), 1);
        SankeyGraph graph = new SankeyGraphBuilder().build(report,
            Set.of("housing", "housing/utility", "housing/utility/electricity"), 0d);

        checkNode(graph, "expense:housing", 2, true);
        checkNode(graph, "expense:housing/utility", 3, true);
        checkNode(graph, "expense:housing/utility/electricity", 4, true);
        checkNode(graph, "expense:housing/utility/electricity/provider", 5, false);
        checkFilter(graph, "expense:housing/utility", "utility", true, false);
        checkFilter(graph, "expense:housing/__direct__", "housing", false, false);
        checkConservation(graph);
    }

    private static void bundlesSmallFlowsOnTheCurrentLevel()
    {
        FlowReport.ExpenseNode large = expense("housing/large", "large", "Groß", 97);
        FlowReport.ExpenseNode small = expense("housing/small", "small", "Klein", 3);
        FlowReport.ExpenseNode housing = expense("housing", "housing", "Wohnen", 100, large, small);
        FlowReport.ExpenseNode food = expense("food", "food", "Lebensmittel", 900);
        FlowReport report = new FlowReport(List.of(value("salary", "Gehalt", 1000)), List.of(housing, food), 1);
        SankeyGraph graph = new SankeyGraphBuilder().build(report, Set.of("housing"), 4d);

        checkNode(graph, "expense:housing/large", 3, false);
        checkNode(graph, "expense:housing/__other__", 3, false);
        SankeyGraph.Node other = node(graph, "expense:housing/__other__");
        FlowAggregatorTests.checkEquals(List.of("expense:housing/small"), other.exclusionKeys(),
            "local other exclusion keys");
        FlowAggregatorTests.checkEquals(List.of(new SankeyGraph.CategoryRule("small", false)),
            other.transactionFilter().categoryRules(), "local other filter");
        SankeyGraph filtered = new SankeyGraphBuilder().build(report, Set.of("housing"), 4d,
            Set.copyOf(other.exclusionKeys()));
        FlowAggregatorTests.checkEquals(97d, node(filtered, "expense:housing").amount(),
            "other exclusion adjusts parent");
        checkConservation(graph);
    }

    private static void keepsUnassignedFlowsVisibleBelowThreshold()
    {
        FlowReport.ExpenseNode unassigned = expense("__unassigned_expense__", "__unassigned_expense__",
            "Unkategorisierte Ausgaben", 10);
        FlowReport report = new FlowReport(List.of(value("salary", "Gehalt", 990),
            value("__unassigned_income__", "Unkategorisierte Einnahmen", 10)),
            List.of(expense("housing", "housing", "Wohnen", 950), unassigned), 1);
        SankeyGraph graph = new SankeyGraphBuilder().build(report, Set.of(), 2d);

        checkFilter(graph, "income:__unassigned_income__", null, false, true);
        checkFilter(graph, "expense:__unassigned_expense__", null, false, true);
        FlowAggregatorTests.checkEquals(List.of("income:__unassigned_income__"),
            node(graph, "income:__unassigned_income__").exclusionKeys(), "unassigned income exclusion key");
        FlowAggregatorTests.checkEquals(List.of("expense:__unassigned_expense__"),
            node(graph, "expense:__unassigned_expense__").exclusionKeys(), "unassigned expense exclusion key");
        SankeyGraph filtered = new SankeyGraphBuilder().build(report, Set.of(), 2d,
            Set.of("income:__unassigned_income__", "expense:__unassigned_expense__"));
        FlowAggregatorTests.checkEquals(990d, filtered.incomeTotal(), "filtered unassigned income");
        FlowAggregatorTests.checkEquals(950d, filtered.expenseTotal(), "filtered unassigned expense");
    }

    private static void excludesFlowsBeforeRecalculatingTheGraph()
    {
        FlowReport.ExpenseNode holiday = expense("leisure/holiday", "holiday", "Urlaub", 70);
        FlowReport.ExpenseNode books = expense("leisure/books", "books", "Bücher", 30);
        FlowReport.ExpenseNode leisure = expense("leisure", "leisure", "Freizeit", 100, holiday, books);
        FlowReport report = new FlowReport(List.of(value("salary", "Gehalt", 100)), List.of(leisure), 1);

        SankeyGraph graph = new SankeyGraphBuilder().build(report, Set.of("leisure"), 0d,
            Set.of("expense:leisure/holiday"));

        FlowAggregatorTests.checkEquals(30d, graph.expenseTotal(), "filtered expense total");
        FlowAggregatorTests.checkEquals(30d, node(graph, "expense:leisure").amount(), "filtered parent amount");
        FlowAggregatorTests.checkEquals(70d, node(graph, "surplus").amount(), "recalculated surplus");
        FlowAggregatorTests.check(graph.nodes().stream()
            .noneMatch(candidate -> candidate.id().equals("expense:leisure/holiday")), "hidden child node");
    }

    private static void excludesAllFlowsRepresentedByAnOtherNode()
    {
        FlowReport report = new FlowReport(List.of(value("salary", "Gehalt", 950),
            value("bonus", "Bonus", 30), value("refund", "Erstattung", 20)), List.of(), 1);
        SankeyGraph graph = new SankeyGraphBuilder().build(report, Set.of(), 5d);
        SankeyGraph.Node other = node(graph, "income:__other__");

        FlowAggregatorTests.checkEquals(List.of("income:bonus", "income:refund"), other.exclusionKeys(),
            "other exclusion keys");
        SankeyGraph filtered = new SankeyGraphBuilder().build(report, Set.of(), 5d,
            Set.copyOf(other.exclusionKeys()));
        FlowAggregatorTests.checkEquals(950d, filtered.incomeTotal(), "filtered bundled income total");
        FlowAggregatorTests.check(filtered.nodes().stream()
            .noneMatch(candidate -> candidate.id().equals("income:__other__")), "hidden other node");
    }

    private static void leavesEmptyReportsEmpty()
    {
        SankeyGraph graph = new SankeyGraphBuilder().build(new FlowReport(List.of(), List.of(), 12), Set.of(), 2d);
        FlowAggregatorTests.check(graph.nodes().isEmpty(), "empty report nodes");
        FlowAggregatorTests.check(graph.links().isEmpty(), "empty report links");
    }

    private static FlowReport.Value value(String key, String name, double amount)
    {
        return new FlowReport.Value(key, name, amount, 0x123456);
    }

    private static FlowReport.ExpenseNode expense(String key, String categoryId, String name, double amount,
                                                  FlowReport.ExpenseNode... children)
    {
        return expense(key, categoryId, name, amount, children.length > 0, children);
    }

    private static FlowReport.ExpenseNode expense(String key, String categoryId, String name, double amount,
                                                  boolean includeChildren, FlowReport.ExpenseNode... children)
    {
        return new FlowReport.ExpenseNode(key, categoryId, name, amount, 0x654321, includeChildren, List.of(children));
    }

    private static void checkNode(SankeyGraph graph, String id, int layer, boolean expandable)
    {
        SankeyGraph.Node node = node(graph, id);
        FlowAggregatorTests.checkEquals(layer, node.layer(), "node layer " + id);
        FlowAggregatorTests.check((node.expandableKey() != null) == expandable, "node expansion " + id);
    }

    private static void checkFilter(SankeyGraph graph, String id, String categoryId,
                                    boolean includeChildren, boolean unassigned)
    {
        SankeyGraph.TransactionFilter filter = node(graph, id).transactionFilter();
        FlowAggregatorTests.check(filter != null && filter.canOpen(), "missing transaction filter " + id);
        FlowAggregatorTests.check(Objects.equals(categoryId, filter.categoryId()), "filter category " + id);
        FlowAggregatorTests.check(filter.includeChildren() == includeChildren, "include children " + id);
        FlowAggregatorTests.check(filter.unassigned() == unassigned, "unassigned " + id);
    }

    private static SankeyGraph.Node node(SankeyGraph graph, String id)
    {
        return graph.nodes().stream().filter(candidate -> candidate.id().equals(id)).findFirst()
            .orElseThrow(() -> new AssertionError("missing node " + id));
    }

    private static void checkConservation(SankeyGraph graph)
    {
        Map<String, Double> incoming = new java.util.HashMap<>();
        Map<String, Double> outgoing = new java.util.HashMap<>();
        for (SankeyGraph.Link link : graph.links())
        {
            outgoing.merge(link.sourceId(), link.amount(), Double::sum);
            incoming.merge(link.targetId(), link.amount(), Double::sum);
        }
        for (SankeyGraph.Node node : graph.nodes())
        {
            double in = incoming.getOrDefault(node.id(), 0d);
            double out = outgoing.getOrDefault(node.id(), 0d);
            if (in > 0d && out > 0d)
                FlowAggregatorTests.checkEquals(in, out, "flow conservation for " + node.id());
            FlowAggregatorTests.checkEquals(node.amount(), Math.max(in, out), "node value for " + node.id());
        }
    }
}
