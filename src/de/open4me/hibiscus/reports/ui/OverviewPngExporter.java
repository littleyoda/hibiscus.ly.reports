package de.open4me.hibiscus.reports.ui;

import java.time.LocalDate;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.widgets.Display;

import de.open4me.hibiscus.reports.model.AggregationInterval;
import de.open4me.hibiscus.reports.model.PeriodBalance;

final class OverviewPngExporter
{
    private static final int SCALE = 2;
    private static final int HEADER = 110;
    private static final int MAX_LOGICAL_WIDTH = 4000;

    private OverviewPngExporter()
    {
    }

    static void save(Display display, List<PeriodBalance> periods, LocalDate from, LocalDate to,
                     AggregationInterval interval, String filename)
    {
        int width = Math.min(MAX_LOGICAL_WIDTH, BalanceChartGeometry.preferredWidth(periods));
        int height = HEADER + BalanceChartGeometry.HEIGHT;
        Image image = new Image(display, width * SCALE, height * SCALE);
        try
        {
            GC gc = new GC(image);
            Transform transform = new Transform(display);
            Font titleFont = null;
            try
            {
                gc.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
                gc.fillRectangle(image.getBounds());
                transform.scale(SCALE, SCALE);
                gc.setTransform(transform);
                gc.setForeground(display.getSystemColor(SWT.COLOR_BLACK));
                FontData[] titleData = display.getSystemFont().getFontData();
                for (FontData data : titleData)
                {
                    data.setHeight(data.getHeight() + 6);
                    data.setStyle(SWT.BOLD);
                }
                titleFont = new Font(display, titleData);
                Font normal = gc.getFont();
                gc.setFont(titleFont);
                gc.drawText("Monatsübersicht", 24, 12, true);
                gc.setFont(normal);
                gc.drawText(OverviewExportText.period(from, to, interval), 24, 48, true);
                gc.drawText(OverviewExportText.summary(periods, interval), 24, 72, true);
                BalanceChartSwtRenderer.paint(gc, display, periods, width, HEADER);
            }
            finally
            {
                gc.setTransform(null);
                if (titleFont != null)
                    titleFont.dispose();
                transform.dispose();
                gc.dispose();
            }
            ImageLoader loader = new ImageLoader();
            loader.data = new org.eclipse.swt.graphics.ImageData[] { image.getImageData() };
            loader.save(filename, SWT.IMAGE_PNG);
        }
        finally
        {
            image.dispose();
        }
    }
}
