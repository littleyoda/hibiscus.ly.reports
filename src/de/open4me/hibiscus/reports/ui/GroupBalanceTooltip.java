package de.open4me.hibiscus.reports.ui;

import java.util.Date;
import java.util.Map;
import java.util.function.Supplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swtchart.Chart;
import org.eclipse.swtchart.IAxis;
import org.eclipse.swtchart.ILineSeries;
import org.eclipse.swtchart.ISeries;

import de.willuhn.jameica.hbci.HBCI;
import de.willuhn.jameica.hbci.gui.chart.ChartFeature;

final class GroupBalanceTooltip implements ChartFeature
{
    private final Supplier<Map<String, NamedBalanceSeries>> seriesSupplier;
    private String tooltip;
    private int highlightX;
    private int highlightY;
    private ISeries highlightedSeries;

    GroupBalanceTooltip(Supplier<Map<String, NamedBalanceSeries>> seriesSupplier)
    {
        this.seriesSupplier = seriesSupplier;
    }

    @Override
    public boolean onEvent(Event event)
    {
        return event == Event.PAINT;
    }

    @Override
    public void handleEvent(Event event, Context context)
    {
        Chart chart = context.chart.getChart();
        Control control = chart.getPlotArea().getControl();
        control.addMouseTrackListener(new MouseTrackAdapter()
        {
            @Override
            public void mouseHover(MouseEvent event)
            {
                Point point = closestPoint(chart, event.x, event.y);
                if (point == null)
                    return;
                NamedBalanceSeries details = seriesSupplier.get().get(point.series().getId());
                if (details == null)
                    return;

                tooltip = tooltipText(details, point.time(), point.value());
                highlightX = point.pixelX();
                highlightY = point.pixelY();
                highlightedSeries = point.series();
                control.redraw();
            }
        });
        control.addMouseMoveListener(new MouseMoveListener()
        {
            @Override
            public void mouseMove(MouseEvent event)
            {
                if (tooltip == null)
                    return;
                tooltip = null;
                highlightedSeries = null;
                chart.getPlotArea().setToolTipText(null);
                control.redraw();
            }
        });
        control.addPaintListener(paintEvent -> {
            if (tooltip == null || highlightedSeries == null)
                return;
            chart.getPlotArea().setToolTipText(tooltip);
            paintPoint(paintEvent.gc, highlightX, highlightY, highlightedSeries);
            chart.layout(true);
        });
    }

    private static Point closestPoint(Chart chart, int mouseX, int mouseY)
    {
        IAxis xAxis = chart.getAxisSet().getXAxis(0);
        IAxis yAxis = chart.getAxisSet().getYAxis(0);
        Point closest = null;
        long minimumDistance = Long.MAX_VALUE;
        for (ISeries series : chart.getSeriesSet().getSeries())
        {
            if (!series.isVisible())
                continue;
            double[] xValues = series.getXSeries();
            double[] yValues = series.getYSeries();
            int length = Math.min(xValues.length, yValues.length);
            for (int i = 0; i < length; i++)
            {
                int pixelX = xAxis.getPixelCoordinate(xValues[i]);
                int pixelY = yAxis.getPixelCoordinate(yValues[i]);
                long dx = pixelX - mouseX;
                long dy = pixelY - mouseY;
                long distance = dx * dx + dy * dy;
                if (distance < minimumDistance)
                {
                    minimumDistance = distance;
                    closest = new Point(series, (long) xValues[i], yValues[i], pixelX, pixelY);
                }
            }
        }
        return closest;
    }

    private static String tooltipText(NamedBalanceSeries series, long time, double sum)
    {
        StringBuilder text = new StringBuilder();
        text.append(series.getLabel());
        text.append('\n').append("Datum: ").append(HBCI.DATEFORMAT.format(new Date(time)));
        text.append('\n').append("Summe: ").append(HBCI.DECIMALFORMAT.format(sum)).append(" EUR");
        var accountValues = series.detailsAt(time);
        if (!accountValues.isEmpty())
        {
            text.append('\n').append("Konten:");
            for (BalanceSeriesDetails.AccountValue account : accountValues)
            {
                text.append('\n').append("  ").append(account.accountName()).append(": ")
                    .append(HBCI.DECIMALFORMAT.format(account.value())).append(" EUR");
            }
        }
        return text.toString();
    }

    private static void paintPoint(GC gc, int x, int y, ISeries series)
    {
        Color color = Display.getDefault().getSystemColor(SWT.COLOR_BLUE);
        if (series instanceof ILineSeries line)
            color = line.getLineColor();
        gc.setBackground(color);
        gc.setAlpha(128);
        gc.fillOval(x - 5, y - 5, 10, 10);
    }

    private record Point(ISeries series, long time, double value, int pixelX, int pixelY)
    {
    }
}
