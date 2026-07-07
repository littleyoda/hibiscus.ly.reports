package de.open4me.hibiscus.reports.ui;

import java.util.ArrayList;
import java.util.List;

import de.open4me.hibiscus.reports.model.PeriodBalance;

final class BalanceChartGeometry
{
    static final int HEIGHT = 560;
    static final int MIN_WIDTH = 1100;
    private static final int LEFT = 92;
    private static final int RIGHT = 28;
    private static final int TOP = 68;
    private static final int BOTTOM = 108;
    private static final int PERIOD_WIDTH = 26;
    private static final int MAX_PERIOD_SPACING = 45;

    private BalanceChartGeometry()
    {
    }

    static int preferredWidth(List<PeriodBalance> periods)
    {
        return Math.max(MIN_WIDTH,
            LEFT + RIGHT + Math.max(1, periods == null ? 0 : periods.size()) * PERIOD_WIDTH);
    }

    static Scene create(List<PeriodBalance> periods, int width)
    {
        List<PeriodBalance> values = periods == null ? List.of() : periods;
        int plotWidth = Math.max(100, width - LEFT - RIGHT);
        int zeroY = TOP + (HEIGHT - TOP - BOTTOM) / 2;
        double maximum = 1d;
        for (PeriodBalance period : values)
        {
            maximum = Math.max(maximum, period.income());
            maximum = Math.max(maximum, period.expenses());
            maximum = Math.max(maximum, Math.abs(period.balance()));
        }
        double tickStep = niceStep(maximum / 4d);
        maximum = Math.ceil(maximum / tickStep) * tickStep;
        int tickCount = Math.max(1, (int) Math.round(maximum / tickStep));
        double pixelsPerEuro = (zeroY - TOP - 10d) / maximum;
        int dataWidth = values.isEmpty() ? plotWidth
            : Math.min(plotWidth, values.size() * MAX_PERIOD_SPACING);
        int dataStart = LEFT + (plotWidth - dataWidth) / 2;
        double step = values.isEmpty() ? dataWidth : (double) dataWidth / values.size();
        int barWidth = Math.max(3, Math.min(40, (int) Math.round(step * 0.8d)));
        List<Item> items = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++)
        {
            PeriodBalance value = values.get(i);
            int center = dataStart + (int) Math.round((i + 0.5d) * step);
            int incomeY = y(value.income(), zeroY, pixelsPerEuro);
            int expenseY = y(-value.expenses(), zeroY, pixelsPerEuro);
            int balanceY = y(value.balance(), zeroY, pixelsPerEuro);
            int barX = center - barWidth / 2;
            items.add(new Item(value, center,
                new Rect(barX, incomeY, barWidth, Math.max(1, zeroY - incomeY)),
                new Rect(barX, zeroY, barWidth, Math.max(1, expenseY - zeroY)), balanceY));
        }
        return new Scene(width, maximum, tickStep, tickCount, zeroY, step, List.copyOf(items));
    }

    static int plotLeft()
    {
        return LEFT;
    }

    static int plotRight(int width)
    {
        return width - RIGHT;
    }

    static int plotTop()
    {
        return TOP;
    }

    static int plotBottom()
    {
        return HEIGHT - BOTTOM;
    }

    static int valueY(double value, Scene scene)
    {
        double scale = (scene.zeroY() - TOP - 10d) / scene.maximum();
        return y(value, scene.zeroY(), scale);
    }

    private static int y(double value, int zeroY, double scale)
    {
        return (int) Math.round(zeroY - value * scale);
    }

    static double niceStep(double rawStep)
    {
        if (!Double.isFinite(rawStep) || rawStep <= 0d)
            return 1d;
        double magnitude = Math.pow(10d, Math.floor(Math.log10(rawStep)));
        double normalized = rawStep / magnitude;
        double nice = normalized < 1.5d ? 1d
            : normalized < 3d ? 2d
            : normalized < 7d ? 5d : 10d;
        return nice * magnitude;
    }

    record Rect(int x, int y, int width, int height)
    {
    }

    record Item(PeriodBalance value, int centerX, Rect income, Rect expenses, int balanceY)
    {
    }

    record Scene(int width, double maximum, double tickStep, int tickCount,
                 int zeroY, double step, List<Item> items)
    {
        Item at(int x)
        {
            for (Item item : items)
            {
                if (Math.abs(x - item.centerX()) <= step / 2d)
                    return item;
            }
            return null;
        }
    }
}
