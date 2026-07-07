package de.open4me.hibiscus.reports.ui;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import de.open4me.hibiscus.reports.model.AggregationInterval;
import de.open4me.hibiscus.reports.model.PeriodBalance;

public final class OverviewSvgExporter
{
    private static final int HEADER = 110;

    private OverviewSvgExporter()
    {
    }

    public static String create(List<PeriodBalance> periods, LocalDate from, LocalDate to,
                                AggregationInterval interval)
    {
        int width = BalanceChartGeometry.preferredWidth(periods);
        int height = HEADER + BalanceChartGeometry.HEIGHT;
        BalanceChartGeometry.Scene scene = BalanceChartGeometry.create(periods, width);
        StringBuilder svg = new StringBuilder(32_768);
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            .append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width)
            .append("\" height=\"").append(height).append("\" viewBox=\"0 0 ")
            .append(width).append(' ').append(height).append("\">\n")
            .append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>\n")
            .append("<g font-family=\"sans-serif\" fill=\"#222\">\n")
            .append("<text x=\"24\" y=\"30\" font-size=\"22\" font-weight=\"bold\">Monatsübersicht</text>\n")
            .append("<text x=\"24\" y=\"58\" font-size=\"14\">")
            .append(escape(OverviewExportText.period(from, to, interval))).append("</text>\n")
            .append("<text x=\"24\" y=\"82\" font-size=\"14\">")
            .append(escape(OverviewExportText.summary(periods, interval))).append("</text>\n")
            .append("<text x=\"").append(BalanceChartGeometry.plotLeft()).append("\" y=\"")
            .append(HEADER + 28).append("\" font-size=\"16\" font-weight=\"bold\">Einnahmen, Ausgaben und Bilanz</text>\n")
            .append("</g>\n");

        for (int i = -scene.tickCount(); i <= scene.tickCount(); i++)
        {
            double value = scene.tickStep() * i;
            int y = HEADER + BalanceChartGeometry.valueY(value, scene);
            svg.append("<line x1=\"").append(BalanceChartGeometry.plotLeft()).append("\" y1=\"")
                .append(y).append("\" x2=\"").append(BalanceChartGeometry.plotRight(width))
                .append("\" y2=\"").append(y).append("\" stroke=\"")
                .append(i == 0 ? "#222" : "#d9d9d9").append("\"/>\n");
            svg.append("<text x=\"").append(BalanceChartGeometry.plotLeft() - 8).append("\" y=\"")
                .append(y + 4).append("\" text-anchor=\"end\" font-family=\"sans-serif\" font-size=\"11\" fill=\"#555\">")
                .append(escape(OverviewExportText.euro(value))).append("</text>\n");
        }

        StringBuilder points = new StringBuilder();
        for (int i = 0; i < scene.items().size(); i++)
        {
            BalanceChartGeometry.Item item = scene.items().get(i);
            rect(svg, item.income(), HEADER, BalanceChartSwtRenderer.INCOME);
            rect(svg, item.expenses(), HEADER, BalanceChartSwtRenderer.EXPENSE);
            if (points.length() > 0)
                points.append(' ');
            points.append(item.centerX()).append(',').append(HEADER + item.balanceY());
            svg.append("<circle cx=\"").append(item.centerX()).append("\" cy=\"")
                .append(HEADER + item.balanceY()).append("\" r=\"4\" fill=\"")
                .append(color(BalanceChartSwtRenderer.BALANCE)).append("\"/>\n");
            int labelY = HEADER + BalanceChartGeometry.plotBottom() + 12;
            svg.append("<text x=\"").append(item.centerX() + 4).append("\" y=\"")
                .append(labelY).append("\" transform=\"rotate(90 ")
                .append(item.centerX() + 4).append(' ').append(labelY)
                .append(")\" font-family=\"sans-serif\" font-size=\"11\" fill=\"#555\">")
                .append(escape(item.value().label())).append("</text>\n");
        }
        svg.append("<polyline points=\"").append(points).append("\" fill=\"none\" stroke=\"")
            .append(color(BalanceChartSwtRenderer.BALANCE)).append("\" stroke-width=\"3\"/>\n")
            .append("<g font-family=\"sans-serif\" font-size=\"12\" fill=\"#333\">")
            .append(legend(width - 340, HEADER + 18, "Einnahmen", BalanceChartSwtRenderer.INCOME))
            .append(legend(width - 220, HEADER + 18, "Ausgaben", BalanceChartSwtRenderer.EXPENSE))
            .append(legend(width - 110, HEADER + 18, "Bilanz", BalanceChartSwtRenderer.BALANCE))
            .append("</g>\n</svg>\n");
        return svg.toString();
    }

    private static void rect(StringBuilder svg, BalanceChartGeometry.Rect rect, int originY, int rgb)
    {
        svg.append("<rect x=\"").append(rect.x()).append("\" y=\"").append(originY + rect.y())
            .append("\" width=\"").append(rect.width()).append("\" height=\"")
            .append(rect.height()).append("\" fill=\"").append(color(rgb)).append("\"/>\n");
    }

    private static String legend(int x, int y, String label, int rgb)
    {
        return "<rect x=\"" + x + "\" y=\"" + (y - 12) + "\" width=\"14\" height=\"14\" fill=\""
            + color(rgb) + "\"/><text x=\"" + (x + 20) + "\" y=\"" + y + "\">" + label + "</text>";
    }

    private static String color(int rgb)
    {
        return String.format(Locale.ROOT, "#%06x", rgb & 0xffffff);
    }

    private static String escape(String value)
    {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
