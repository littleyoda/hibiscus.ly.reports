package de.open4me.hibiscus.reports.ui;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import de.open4me.hibiscus.reports.model.SankeyGraph;

public final class SankeySvgExporter
{
    public static final int HEADER_HEIGHT = 100;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private SankeySvgExporter()
    {
    }

    public static String create(SankeyGraph graph, LocalDate from, LocalDate to)
    {
        int chartHeight = SankeyLayout.preferredHeight(graph);
        int height = HEADER_HEIGHT + chartHeight;
        SankeyLayout.Scene scene = SankeyLayout.create(graph, chartHeight);
        StringBuilder svg = new StringBuilder(32_768);
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
            .append(SankeyLayout.WIDTH).append("\" height=\"").append(height)
            .append("\" viewBox=\"0 0 ").append(SankeyLayout.WIDTH).append(' ').append(height)
            .append("\">\n");
        svg.append("  <rect width=\"100%\" height=\"100%\" fill=\"white\"/>\n");
        svg.append("  <g font-family=\"sans-serif\" fill=\"#222222\">\n");
        svg.append("    <text x=\"24\" y=\"30\" font-size=\"22\" font-weight=\"bold\">Geldfluss</text>\n");
        svg.append("    <text x=\"24\" y=\"56\" font-size=\"14\">")
            .append(escape(period(from, to))).append("</text>\n");
        svg.append("    <text x=\"24\" y=\"80\" font-size=\"14\">")
            .append(escape(summary(graph))).append("</text>\n");
        svg.append("  </g>\n");

        svg.append("  <g fill=\"#919191\" fill-opacity=\"0.35\">\n");
        for (SankeyLayout.LinkPlacement link : scene.links())
        {
            float sy1 = link.sourceTop() + HEADER_HEIGHT;
            float sy2 = link.sourceBottom() + HEADER_HEIGHT;
            float ty1 = link.targetTop() + HEADER_HEIGHT;
            float ty2 = link.targetBottom() + HEADER_HEIGHT;
            svg.append("    <path d=\"M ").append(number(link.sourceX())).append(' ').append(number(sy1))
                .append(" C ").append(number(link.sourceX() + link.bend())).append(' ').append(number(sy1))
                .append(' ').append(number(link.targetX() - link.bend())).append(' ').append(number(ty1))
                .append(' ').append(number(link.targetX())).append(' ').append(number(ty1))
                .append(" L ").append(number(link.targetX())).append(' ').append(number(ty2))
                .append(" C ").append(number(link.targetX() - link.bend())).append(' ').append(number(ty2))
                .append(' ').append(number(link.sourceX() + link.bend())).append(' ').append(number(sy2))
                .append(' ').append(number(link.sourceX())).append(' ').append(number(sy2))
                .append(" Z\"/>\n");
        }
        svg.append("  </g>\n");

        svg.append("  <g font-family=\"sans-serif\" font-size=\"12\" fill=\"#222222\">\n");
        for (SankeyLayout.NodePlacement placement : scene.nodes())
        {
            SankeyLayout.Bounds bounds = placement.bounds();
            float y = bounds.y() + HEADER_HEIGHT;
            svg.append("    <rect x=\"").append(number(bounds.x())).append("\" y=\"")
                .append(number(y)).append("\" width=\"").append(number(bounds.width()))
                .append("\" height=\"").append(number(bounds.height()))
                .append("\" rx=\"5\" fill=\"").append(color(placement.node().color()))
                .append("\" stroke=\"#666666\"/>\n");
            float textX = placement.node().layer() == 0 ? Math.max(8, bounds.x() - 235) : bounds.x() + 28;
            float textY = Math.max(HEADER_HEIGHT + 4, y + bounds.height() / 2 - 9);
            svg.append("    <text x=\"").append(number(textX)).append("\" y=\"")
                .append(number(textY)).append("\">")
                .append("<tspan>").append(escape(placement.node().name())).append("</tspan>")
                .append("<tspan x=\"").append(number(textX)).append("\" dy=\"16\">")
                .append(escape(SankeyText.detailLine(graph, placement.node())))
                .append("</tspan></text>\n");
        }
        svg.append("  </g>\n</svg>\n");
        return svg.toString();
    }

    static String summary(SankeyGraph graph)
    {
        double difference = graph.incomeTotal() - graph.expenseTotal();
        String differenceName = difference >= 0d ? "Überschuss" : "Defizit";
        return "Einnahmen: " + SankeyText.euro(graph.incomeTotal()) + " €     Ausgaben: "
            + SankeyText.euro(graph.expenseTotal()) + " €     " + differenceName + ": "
            + SankeyText.euro(Math.abs(difference)) + " €";
    }

    static String period(LocalDate from, LocalDate to)
    {
        return formatDate(from) + " – " + formatDate(to);
    }

    static String formatDate(LocalDate date)
    {
        return date == null ? "" : DATE_FORMAT.format(date);
    }

    private static String color(int rgb)
    {
        return String.format(Locale.ROOT, "#%06x", rgb & 0xffffff);
    }

    private static String number(float value)
    {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String escape(String value)
    {
        if (value == null)
            return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
