package de.open4me.hibiscus.reports.ui;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.widgets.Display;

import de.open4me.hibiscus.reports.model.PeriodBalance;

final class BalanceChartSwtRenderer
{
    static final int INCOME = 0x28a745;
    static final int EXPENSE = 0xdc3545;
    static final int BALANCE = 0xe0a800;

    private BalanceChartSwtRenderer()
    {
    }

    static void paint(GC gc, Display display, List<PeriodBalance> periods, int width, int originY)
    {
        BalanceChartGeometry.Scene scene = BalanceChartGeometry.create(periods, width);
        Color income = color(display, INCOME);
        Color expense = color(display, EXPENSE);
        Color balance = color(display, BALANCE);
        Color grid = color(display, 0xd9d9d9);
        Transform originalTransform = new Transform(display);
        gc.getTransform(originalTransform);
        try
        {
            gc.setAntialias(SWT.ON);
            gc.setTextAntialias(SWT.ON);
            gc.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
            gc.drawText("Einnahmen, Ausgaben und Bilanz", BalanceChartGeometry.plotLeft(), originY + 12, true);
            drawLegend(gc, income, expense, balance, width, originY);

            NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.GERMANY);
            for (int i = -scene.tickCount(); i <= scene.tickCount(); i++)
            {
                double value = scene.tickStep() * i;
                int y = originY + BalanceChartGeometry.valueY(value, scene);
                gc.setForeground(i == 0 ? display.getSystemColor(SWT.COLOR_BLACK) : grid);
                gc.drawLine(BalanceChartGeometry.plotLeft(), y, BalanceChartGeometry.plotRight(width), y);
                gc.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
                String label = numbers.format(value) + " €";
                Point extent = gc.textExtent(label);
                gc.drawText(label, BalanceChartGeometry.plotLeft() - extent.x - 8,
                    y - extent.y / 2, true);
            }

            BalanceChartGeometry.Item previous = null;
            gc.setLineWidth(3);
            for (int i = 0; i < scene.items().size(); i++)
            {
                BalanceChartGeometry.Item item = scene.items().get(i);
                fill(gc, income, item.income(), originY);
                fill(gc, expense, item.expenses(), originY);
                if (previous != null)
                {
                    gc.setForeground(balance);
                    gc.drawLine(previous.centerX(), originY + previous.balanceY(),
                        item.centerX(), originY + item.balanceY());
                }
                gc.setBackground(balance);
                gc.fillOval(item.centerX() - 4, originY + item.balanceY() - 4, 8, 8);

                gc.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
                drawRotatedLabel(gc, display, originalTransform, item.value().label(),
                    item.centerX(), originY + BalanceChartGeometry.plotBottom() + 12);
                previous = item;
            }
            gc.setLineWidth(1);
        }
        finally
        {
            gc.setTransform(originalTransform);
            originalTransform.dispose();
            income.dispose();
            expense.dispose();
            balance.dispose();
            grid.dispose();
        }
    }

    private static void drawRotatedLabel(GC gc, Display display, Transform original,
                                         String label, int x, int y)
    {
        Point extent = gc.textExtent(label);
        float[] elements = new float[6];
        original.getElements(elements);
        Transform rotated = new Transform(display, elements);
        try
        {
            rotated.translate(x + extent.y / 2f, y);
            rotated.rotate(90f);
            gc.setTransform(rotated);
            gc.drawText(label, 0, 0, true);
        }
        finally
        {
            gc.setTransform(original);
            rotated.dispose();
        }
    }

    private static void drawLegend(GC gc, Color income, Color expense, Color balance,
                                   int width, int originY)
    {
        String[] labels = { "Einnahmen", "Ausgaben", "Bilanz" };
        Color[] colors = { income, expense, balance };
        int x = Math.max(BalanceChartGeometry.plotLeft() + 300, width - 350);
        for (int i = 0; i < labels.length; i++)
        {
            gc.setBackground(colors[i]);
            gc.fillRectangle(x, originY + 14, 14, 14);
            gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_DARK_GRAY));
            gc.drawText(labels[i], x + 20, originY + 11, true);
            x += gc.textExtent(labels[i]).x + 48;
        }
    }

    private static void fill(GC gc, Color color, BalanceChartGeometry.Rect rect, int originY)
    {
        gc.setBackground(color);
        gc.fillRectangle(rect.x(), originY + rect.y(), rect.width(), rect.height());
    }

    private static Color color(Display display, int rgb)
    {
        return new Color(display, (rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff);
    }
}
