package de.open4me.hibiscus.reports.ui;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import de.open4me.hibiscus.reports.model.AggregationInterval;
import de.open4me.hibiscus.reports.model.PeriodBalance;

public final class OverviewExportTests
{
    private OverviewExportTests()
    {
    }

    public static void run()
    {
        exportsWellFormedSvgWithAllSeries();
        calculatesDynamicSummaryAverage();
        alignsIncomeAndExpenseBars();
        usesReadableAxisSteps();
        keepsPeriodBarsCloseTogether();
    }

    private static void exportsWellFormedSvgWithAllSeries()
    {
        List<PeriodBalance> periods = periods();
        String svg = OverviewSvgExporter.create(periods, LocalDate.of(2025, 1, 15),
            LocalDate.of(2025, 6, 20), AggregationInterval.QUARTERLY);
        check(svg.contains("15.01.2025 – 20.06.2025"), "exact period");
        check(svg.contains("Gruppierung: Quartalsweise"), "grouping");
        check(svg.contains("Q1 2025"), "period label");
        check(svg.contains("#28a745"), "income color");
        check(svg.contains("#dc3545"), "expense color");
        check(svg.contains("#e0a800"), "balance color");
        try
        {
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
            check("svg".equals(document.getDocumentElement().getNodeName()), "SVG root");
            check(document.getElementsByTagName("polyline").getLength() == 1, "balance line");
        }
        catch (Exception e)
        {
            throw new AssertionError("overview SVG must be well-formed", e);
        }
    }

    private static void calculatesDynamicSummaryAverage()
    {
        String summary = OverviewExportText.summary(periods(), AggregationInterval.QUARTERLY);
        check(summary.contains("Einnahmen: 3.000 €"), "income total");
        check(summary.contains("Ausgaben: 1.500 €"), "expense total");
        check(summary.contains("pro Quartal: 750 €"), "quarterly balance average");
    }

    private static void alignsIncomeAndExpenseBars()
    {
        BalanceChartGeometry.Scene scene = BalanceChartGeometry.create(periods(), 1100);
        for (BalanceChartGeometry.Item item : scene.items())
            check(item.income().x() == item.expenses().x(), "income and expense bar x position");
    }

    private static void usesReadableAxisSteps()
    {
        check(BalanceChartGeometry.niceStep(4413.5d) == 5000d, "5,000 euro step");
        check(BalanceChartGeometry.niceStep(1800d) == 2000d, "2,000 euro step");
        BalanceChartGeometry.Scene scene = BalanceChartGeometry.create(List.of(
            new PeriodBalance(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31),
                "Jan. 2025", 17654d, 100d)), 1100);
        check(scene.maximum() == 20000d, "rounded axis maximum");
        BalanceChartGeometry.Scene larger = BalanceChartGeometry.create(List.of(
            new PeriodBalance(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31),
                "2025", 79000d, 83000d)), 1100);
        check(larger.tickStep() == 20000d, "20,000 euro step");
        check(larger.maximum() == 100000d, "100,000 euro axis maximum");
        check(larger.tickCount() == 5, "five positive axis ticks");
    }

    private static void keepsPeriodBarsCloseTogether()
    {
        BalanceChartGeometry.Scene scene = BalanceChartGeometry.create(periods(), 2000);
        int distance = scene.items().get(1).centerX() - scene.items().get(0).centerX();
        check(distance <= 45, "maximum period spacing");
        check(scene.items().get(0).income().width() == 36, "bar width with reduced spacing");
    }

    private static List<PeriodBalance> periods()
    {
        return List.of(
            new PeriodBalance(LocalDate.of(2025, 1, 15), LocalDate.of(2025, 3, 31),
                "Q1 2025", 1000d, 800d),
            new PeriodBalance(LocalDate.of(2025, 4, 1), LocalDate.of(2025, 6, 20),
                "Q2 2025", 2000d, 700d));
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
