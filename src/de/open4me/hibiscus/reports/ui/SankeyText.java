package de.open4me.hibiscus.reports.ui;

import java.text.NumberFormat;
import java.util.Locale;

import de.open4me.hibiscus.reports.model.SankeyGraph;

final class SankeyText
{
    private SankeyText()
    {
    }

    static String detailLine(SankeyGraph graph, SankeyGraph.Node node)
    {
        NumberFormat percentFormat = NumberFormat.getNumberInstance(Locale.GERMANY);
        percentFormat.setMinimumFractionDigits(1);
        percentFormat.setMaximumFractionDigits(1);
        double percent = node.percentageBase() == 0d ? 0d : node.amount() / node.percentageBase() * 100d;
        double average = node.amount() / Math.max(1, graph.monthCount());
        return percentFormat.format(percent) + "%   ∑" + euro(node.amount()) + " €   ∅"
            + euro(average) + " €/M";
    }

    static String euro(double value)
    {
        return NumberFormat.getIntegerInstance(Locale.GERMANY).format(value);
    }
}

