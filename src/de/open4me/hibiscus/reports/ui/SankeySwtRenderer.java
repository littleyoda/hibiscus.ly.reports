package de.open4me.hibiscus.reports.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.widgets.Display;

import de.open4me.hibiscus.reports.model.SankeyGraph;

final class SankeySwtRenderer
{
    private SankeySwtRenderer()
    {
    }

    static void paint(GC gc, Display display, SankeyGraph graph, SankeyLayout.Scene scene,
                      int yOffset, boolean showExpansionMarker, boolean exportColors)
    {
        paint(gc, display, graph, scene, yOffset, showExpansionMarker, exportColors,
            SankeyText.DetailOptions.DEFAULT);
    }

    static void paint(GC gc, Display display, SankeyGraph graph, SankeyLayout.Scene scene,
                      int yOffset, boolean showExpansionMarker, boolean exportColors,
                      SankeyText.DetailOptions detailOptions)
    {
        gc.setAntialias(SWT.ON);
        if (graph == null || graph.nodes().isEmpty())
        {
            gc.drawText("Für die gewählten Filter sind keine kategorisierten Umsätze vorhanden.",
                24, yOffset + 30, true);
            return;
        }
        paintLinks(gc, display, scene, yOffset);
        paintNodes(gc, display, graph, scene, yOffset, showExpansionMarker, exportColors, detailOptions);
    }

    private static void paintLinks(GC gc, Display display, SankeyLayout.Scene scene, int yOffset)
    {
        gc.setAlpha(90);
        try
        {
            for (SankeyLayout.LinkPlacement link : scene.links())
            {
                Path path = new Path(display);
                Color linkColor = color(display, link.color());
                try
                {
                    gc.setBackground(linkColor);
                    float sy1 = link.sourceTop() + yOffset;
                    float sy2 = link.sourceBottom() + yOffset;
                    float ty1 = link.targetTop() + yOffset;
                    float ty2 = link.targetBottom() + yOffset;
                    path.moveTo(link.sourceX(), sy1);
                    path.cubicTo(link.sourceX() + link.bend(), sy1,
                        link.targetX() - link.bend(), ty1, link.targetX(), ty1);
                    path.lineTo(link.targetX(), ty2);
                    path.cubicTo(link.targetX() - link.bend(), ty2,
                        link.sourceX() + link.bend(), sy2, link.sourceX(), sy2);
                    path.close();
                    gc.fillPath(path);
                }
                finally
                {
                    path.dispose();
                    linkColor.dispose();
                }
            }
        }
        finally
        {
            gc.setAlpha(255);
        }
    }

    private static void paintNodes(GC gc, Display display, SankeyGraph graph,
                                   SankeyLayout.Scene scene, int yOffset,
                                   boolean showExpansionMarker, boolean exportColors,
                                   SankeyText.DetailOptions detailOptions)
    {
        for (SankeyLayout.NodePlacement placement : scene.nodes())
        {
            SankeyLayout.Bounds bounds = placement.bounds();
            Color color = color(display, placement.node().color());
            try
            {
                gc.setBackground(color);
                gc.fillRoundRectangle(Math.round(bounds.x()), Math.round(bounds.y()) + yOffset,
                    Math.round(bounds.width()), Math.round(bounds.height()), 5, 5);
                gc.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
                gc.drawRoundRectangle(Math.round(bounds.x()), Math.round(bounds.y()) + yOffset,
                    Math.round(bounds.width()), Math.round(bounds.height()), 5, 5);
            }
            finally
            {
                color.dispose();
            }

            String marker = showExpansionMarker && placement.node().expandableKey() != null ? "  [+/-]" : "";
            String title = placement.node().name() + marker;
            String details = SankeyText.detailLine(graph, placement.node(), detailOptions);
            int textX = placement.node().layer() == 0 ? Math.max(8, Math.round(bounds.x()) - 235)
                : Math.round(bounds.x()) + 28;
            int lineHeight = gc.getFontMetrics().getHeight();
            int lineAdvance = Math.max(1, lineHeight - 4);
            int textY = Math.max(yOffset + 4, Math.round(bounds.y() + bounds.height() / 2)
                + yOffset - (lineHeight + lineAdvance) / 2);
            gc.setForeground(display.getSystemColor(exportColors
                ? SWT.COLOR_BLACK : SWT.COLOR_WIDGET_FOREGROUND));
            gc.drawText(title, textX, textY, true);
            gc.drawText(details, textX, textY + lineAdvance, true);
        }
    }

    private static Color color(Display display, int rgb)
    {
        return new Color(display, (rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff);
    }
}
