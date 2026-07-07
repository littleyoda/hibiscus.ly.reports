package de.open4me.hibiscus.reports.ui;

import java.time.LocalDate;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.widgets.Display;

import de.open4me.hibiscus.reports.model.SankeyGraph;

final class SankeyPngExporter
{
    private static final int SCALE = 2;

    private SankeyPngExporter()
    {
    }

    static void save(Display display, SankeyGraph graph, LocalDate from, LocalDate to, String filename)
    {
        int chartHeight = SankeyLayout.preferredHeight(graph);
        int logicalHeight = SankeySvgExporter.HEADER_HEIGHT + chartHeight;
        Image image = new Image(display, SankeyLayout.WIDTH * SCALE, logicalHeight * SCALE);
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
                Font defaultFont = gc.getFont();
                gc.setFont(titleFont);
                gc.drawText("Geldfluss", 24, 14, true);
                gc.setFont(defaultFont);
                gc.drawText(SankeySvgExporter.period(from, to), 24, 48, true);
                gc.drawText(SankeySvgExporter.summary(graph), 24, 72, true);

                SankeyLayout.Scene scene = SankeyLayout.create(graph, chartHeight);
                SankeySwtRenderer.paint(gc, display, graph, scene,
                    SankeySvgExporter.HEADER_HEIGHT, false, true);
            }
            finally
            {
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

    static int outputWidth()
    {
        return SankeyLayout.WIDTH * SCALE;
    }

    static int outputHeight(SankeyGraph graph)
    {
        return (SankeySvgExporter.HEADER_HEIGHT + SankeyLayout.preferredHeight(graph)) * SCALE;
    }
}
