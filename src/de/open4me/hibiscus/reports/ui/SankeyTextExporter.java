package de.open4me.hibiscus.reports.ui;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import de.open4me.hibiscus.reports.model.SankeyGraph;

final class SankeyTextExporter
{
    private static final Pattern AMOUNT_TOKEN = Pattern.compile("\\[(\\d+(?:\\.\\d+)?)\\]");
    private static final Pattern COLOR_TOKEN = Pattern.compile("#(?i:[0-9a-f]{3}|[0-9a-f]{6})\\b");

    private SankeyTextExporter()
    {
    }

    static String create(SankeyGraph graph, LocalDate from, LocalDate to)
    {
        Map<String, SankeyGraph.Node> nodesById = new HashMap<>();
        Map<String, String> labelsById = labelsById(graph);
        for (SankeyGraph.Node node : graph.nodes())
            nodesById.put(node.id(), node);

        StringBuilder text = new StringBuilder(16_384);
        text.append("// Geldfluss\n");
        text.append("// Zeitraum: ").append(SankeySvgExporter.period(from, to)).append('\n');
        text.append("// ").append(SankeySvgExporter.summary(graph)).append("\n\n");

        for (SankeyGraph.Link link : graph.links())
        {
            if (link.amount() <= 0d)
                continue;
            String source = labelsById.get(link.sourceId());
            String target = labelsById.get(link.targetId());
            if (source == null || target == null)
                continue;
            text.append(source).append(" [").append(amount(link.amount())).append("] ")
                .append(target).append(' ')
                .append(color(linkColor(nodesById.get(link.sourceId()), nodesById.get(link.targetId()))))
                .append('\n');
        }

        if (!graph.nodes().isEmpty())
            text.append('\n');
        for (SankeyGraph.Node node : graph.nodes())
            text.append(':').append(labelsById.get(node.id())).append(' ')
                .append(color(node.color())).append('\n');

        return text.toString();
    }

    private static Map<String, String> labelsById(SankeyGraph graph)
    {
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (SankeyGraph.Node node : graph.nodes())
        {
            String label = label(node.name());
            int count = counts.merge(label, 1, Integer::sum);
            result.put(node.id(), count == 1 ? label : label + " (" + count + ")");
        }
        return result;
    }

    private static String label(String value)
    {
        String label = value == null || value.isBlank() ? "Unbenannt" : value.strip();
        label = label.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
        label = AMOUNT_TOKEN.matcher(label).replaceAll("($1)");
        label = COLOR_TOKEN.matcher(label).replaceAll(match -> match.group().replace("#", "Nr. "));
        return label;
    }

    private static String amount(double value)
    {
        return Long.toString(Math.round(value));
    }

    private static String color(int rgb)
    {
        return String.format(Locale.ROOT, "#%06x", rgb & 0xffffff);
    }

    private static int linkColor(SankeyGraph.Node source, SankeyGraph.Node target)
    {
        if (source == null || target == null)
            return SankeyLayout.DEFAULT_LINK_COLOR;
        return target.layer() == 1 ? source.color() : target.color();
    }
}
