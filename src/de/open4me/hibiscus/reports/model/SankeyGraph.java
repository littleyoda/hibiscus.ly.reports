package de.open4me.hibiscus.reports.model;

import java.util.List;

public record SankeyGraph(List<Node> nodes, List<Link> links, int monthCount,
                          double incomeTotal, double expenseTotal)
{
    public SankeyGraph
    {
        nodes = List.copyOf(nodes);
        links = List.copyOf(links);
    }

    public record Node(String id, String name, double amount, double percentageBase,
                       int color, int layer, String expandableKey, TransactionFilter transactionFilter)
    {
    }

    public record TransactionFilter(String categoryId, boolean includeChildren, boolean unassigned,
                                    int sign)
    {
        public boolean canOpen()
        {
            return unassigned || (categoryId != null && !categoryId.isBlank());
        }
    }

    public record Link(String sourceId, String targetId, double amount)
    {
    }
}
