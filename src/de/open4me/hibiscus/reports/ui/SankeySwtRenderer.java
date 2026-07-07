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
        gc.setAntialias(SWT.ON);
        if (graph == null || graph.nodes().isEmpty())
        {
            gc.drawText("Für die gewählten Filter sind keine kategorisierten Umsätze vorhanden.",
                24, yOffset + 30, true);
            return;
        }
        paintLinks(gc, display, scene, yOffset);
        paintNodes(gc, display, graph, scene, yOffset, showExpansionMarker, exportColors);
    }

    private static void paintLinks(GC gc, Display display, SankeyLayout.Scene scene, int yOffset)
    {
        Color linkColor = new Color(display, 145, 145, 145);
        gc.setBackground(linkColor);
        gc.setAlpha(90);
        try
        {
            for (SankeyLayout.LinkPlacement link : scene.links())
            {
                Path path = new Path(display);
                try
                {
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
                }
            }
        }
        finally
        {
            gc.setAlpha(255);
            linkColor.dispose();
        }
    }

    private static void paintNodes(GC gc, Display display, SankeyGraph graph,
                                   SankeyLayout.Scene scene, int yOffset,
                                   boolean showExpansionMarker, boolean exportColors)
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
            String text = placement.node().name() + marker + "\n" + SankeyText.detailLine(graph, placement.node());
            int textX = placement.node().layer() == 0 ? Math.max(8, Math.round(bounds.x()) - 235)
                : Math.round(bounds.x()) + 28;
            int textY = Math.max(yOffset + 4,
                Math.round(bounds.y() + bounds.height() / 2) + yOffset - 16);
            gc.setForeground(display.getSystemColor(exportColors
                ? SWT.COLOR_BLACK : SWT.COLOR_WIDGET_FOREGROUND));
            gc.drawText(text, textX, textY, true);
        }
    }

    private static Color color(Display display, int rgb)
    {
        return new Color(display, (rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff);
    }
}
