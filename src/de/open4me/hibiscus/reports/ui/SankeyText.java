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
        return detailLine(graph, node, DetailOptions.DEFAULT);
    }

    static String detailLine(SankeyGraph graph, SankeyGraph.Node node, DetailOptions options)
    {
        NumberFormat percentFormat = NumberFormat.getNumberInstance(Locale.GERMANY);
        percentFormat.setMinimumFractionDigits(1);
        percentFormat.setMaximumFractionDigits(1);
        double percent = node.percentageBase() == 0d ? 0d : node.amount() / node.percentageBase() * 100d;
        DetailOptions effectiveOptions = options == null ? DetailOptions.DEFAULT : options;
        StringBuilder result = new StringBuilder(percentFormat.format(percent)).append('%');
        if (effectiveOptions.showTotal())
            result.append("   ∑").append(euro(node.amount())).append(" €");
        if (effectiveOptions.showMonthlyAverage())
        {
            double average = node.amount() / Math.max(1, graph.monthCount());
            result.append("   ∅").append(euro(average)).append(" €/M");
        }
        return result.toString();
    }

    static String euro(double value)
    {
        return NumberFormat.getIntegerInstance(Locale.GERMANY).format(value);
    }

    record DetailOptions(boolean showTotal, boolean showMonthlyAverage)
    {
        static final DetailOptions DEFAULT = new DetailOptions(true, true);
    }
}
