package de.open4me.hibiscus.reports;

import java.util.HashMap;
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
        createsBalancedSurplusGraph();
        createsBalancedDeficitGraph();
        bundlesSmallFlowsWithoutLosingValue();
        keepsUnassignedFlowsVisibleBelowThreshold();
        leavesEmptyReportsEmpty();
    }

    private static void createsBalancedSurplusGraph()
    {
        FlowReport report = report(3000, 2000);
        SankeyGraph graph = new SankeyGraphBuilder().build(report, Set.of("housing"), 0d);

        checkNode(graph, "surplus", 1000d);
        checkNode(graph, "available", 3000d);
        checkNodeName(graph, "available", "Verfügbare Mittel");
        checkNode(graph, "sub:rent", 2000d);
        checkPercentageBase(graph, "expense:housing", 3000d);
        checkPercentageBase(graph, "surplus", 3000d);
        checkPercentageBase(graph, "sub:rent", 2000d);
        checkFilter(graph, "expense:housing", "housing", true, false);
        checkFilter(graph, "sub:rent", "rent", false, false);
        checkConservation(graph);
    }

    private static void createsBalancedDeficitGraph()
    {
        FlowReport report = report(1500, 2000);
        SankeyGraph graph = new SankeyGraphBuilder().build(report, Set.of(), 0d);

        checkNode(graph, "deficit", 500d);
        checkNode(graph, "available", 2000d);
        checkNodeName(graph, "available", "Benötigte Mittel");
        checkPercentageBase(graph, "deficit", 2000d);
        checkPercentageBase(graph, "expense:housing", 2000d);
        FlowAggregatorTests.check(graph.links().stream().anyMatch(link ->
            link.sourceId().equals("deficit") && link.targetId().equals("available") && link.amount() == 500d),
            "deficit must flow into available funds");
        checkConservation(graph);
    }

    private static void bundlesSmallFlowsWithoutLosingValue()
    {
        FlowReport report = new FlowReport(
            List.of(value("salary", "Gehalt", 990), value("bonus", "Bonus", 10)),
            List.of(
                group("housing", "Wohnen", 950, value("rent", "Miete", 950)),
                group("fees", "Gebühren", 10, value("bank", "Bank", 10))),
            1);
        SankeyGraph graph = new SankeyGraphBuilder().build(report, Set.of("housing"), 2d);

        checkNode(graph, "income:__other__", 10d);
        checkNode(graph, "expense:__other__", 10d);
        checkNoFilter(graph, "income:__other__");
        checkNoFilter(graph, "expense:__other__");
        FlowAggregatorTests.checkEquals(1000d,
            graph.links().stream().filter(link -> link.targetId().equals("available")).mapToDouble(SankeyGraph.Link::amount).sum(),
            "bundled income links");
        FlowAggregatorTests.checkEquals(960d,
            graph.links().stream().filter(link -> link.sourceId().equals("available") && !link.targetId().equals("surplus"))
                .mapToDouble(SankeyGraph.Link::amount).sum(), "bundled expense links");
        checkConservation(graph);
    }

    private static void leavesEmptyReportsEmpty()
    {
        SankeyGraph graph = new SankeyGraphBuilder().build(new FlowReport(List.of(), List.of(), 12), Set.of(), 2d);
        FlowAggregatorTests.check(graph.nodes().isEmpty(), "empty report nodes");
        FlowAggregatorTests.check(graph.links().isEmpty(), "empty report links");
    }

    private static void keepsUnassignedFlowsVisibleBelowThreshold()
    {
        FlowReport report = new FlowReport(
            List.of(value("salary", "Gehalt", 990),
                value("__unassigned_income__", "Unkategorisierte Einnahmen", 10)),
            List.of(
                group("housing", "Wohnen", 950, value("rent", "Miete", 950)),
                group("__unassigned_expense__", "Unkategorisierte Ausgaben", 10,
                    value("__unassigned_expense__/diverse", "Diverse", 10))),
            1);
        SankeyGraph graph = new SankeyGraphBuilder().build(report, Set.of(), 2d);

        checkNode(graph, "income:__unassigned_income__", 10d);
        checkNode(graph, "expense:__unassigned_expense__", 10d);
        checkFilter(graph, "income:__unassigned_income__", null, false, true);
        checkFilter(graph, "expense:__unassigned_expense__", null, false, true);
    }

    private static FlowReport report(double income, double expense)
    {
        return new FlowReport(List.of(value("salary", "Gehalt", income)),
            List.of(group("housing", "Wohnen", expense, value("rent", "Miete", expense))), 12);
    }

    private static FlowReport.Value value(String key, String name, double amount)
    {
        return new FlowReport.Value(key, name, amount, 0x123456);
    }

    private static FlowReport.ExpenseGroup group(String key, String name, double amount, FlowReport.Value... values)
    {
        return new FlowReport.ExpenseGroup(key, name, amount, 0x654321, List.of(values));
    }

    private static void checkNode(SankeyGraph graph, String id, double amount)
    {
        SankeyGraph.Node node = graph.nodes().stream().filter(candidate -> candidate.id().equals(id)).findFirst()
            .orElseThrow(() -> new AssertionError("missing node " + id));
        FlowAggregatorTests.checkEquals(amount, node.amount(), "node " + id);
    }

    private static void checkNodeName(SankeyGraph graph, String id, String name)
    {
        SankeyGraph.Node node = graph.nodes().stream().filter(candidate -> candidate.id().equals(id)).findFirst()
            .orElseThrow(() -> new AssertionError("missing node " + id));
        FlowAggregatorTests.checkEquals(name, node.name(), "node name " + id);
    }

    private static void checkPercentageBase(SankeyGraph graph, String id, double percentageBase)
    {
        SankeyGraph.Node node = graph.nodes().stream().filter(candidate -> candidate.id().equals(id)).findFirst()
            .orElseThrow(() -> new AssertionError("missing node " + id));
        FlowAggregatorTests.checkEquals(percentageBase, node.percentageBase(), "percentage base " + id);
    }

    private static void checkFilter(SankeyGraph graph, String id, String categoryId,
                                    boolean includeChildren, boolean unassigned)
    {
        SankeyGraph.Node node = graph.nodes().stream().filter(candidate -> candidate.id().equals(id)).findFirst()
            .orElseThrow(() -> new AssertionError("missing node " + id));
        SankeyGraph.TransactionFilter filter = node.transactionFilter();
        FlowAggregatorTests.check(filter != null && filter.canOpen(), "missing transaction filter " + id);
        FlowAggregatorTests.check(Objects.equals(categoryId, filter.categoryId()), "filter category " + id);
        FlowAggregatorTests.check(filter.includeChildren() == includeChildren, "include children " + id);
        FlowAggregatorTests.check(filter.unassigned() == unassigned, "unassigned " + id);
    }

    private static void checkNoFilter(SankeyGraph graph, String id)
    {
        SankeyGraph.Node node = graph.nodes().stream().filter(candidate -> candidate.id().equals(id)).findFirst()
            .orElseThrow(() -> new AssertionError("missing node " + id));
        FlowAggregatorTests.check(node.transactionFilter() == null, "unexpected transaction filter " + id);
    }

    private static void checkConservation(SankeyGraph graph)
    {
        Map<String, Double> incoming = new HashMap<>();
        Map<String, Double> outgoing = new HashMap<>();
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
