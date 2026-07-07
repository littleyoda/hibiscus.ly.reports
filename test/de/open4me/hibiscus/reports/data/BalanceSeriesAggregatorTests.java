package de.open4me.hibiscus.reports.data;

import java.util.Date;
import java.util.List;

import de.willuhn.jameica.hbci.server.Value;

public final class BalanceSeriesAggregatorTests
{
    private BalanceSeriesAggregatorTests()
    {
    }

    public static void run()
    {
        sumsMatchingDays();
        alignsDifferentDateRanges();
        ignoresMissingSeries();
    }

    private static void sumsMatchingDays()
    {
        List<Value> result = new BalanceSeriesAggregator().sum(List.of(
            List.of(value(1, 100d), value(2, 120d)),
            List.of(value(1, 40d), value(2, 30d))));
        check(result.size() == 2, "daily point count");
        check(result.get(0).getValue() == 140d, "first daily sum");
        check(result.get(1).getValue() == 150d, "second daily sum");
    }

    private static void alignsDifferentDateRanges()
    {
        List<Value> result = new BalanceSeriesAggregator().sum(List.of(
            List.of(value(1, 100d), value(3, 120d)),
            List.of(value(2, 40d), value(3, 30d))));
        check(result.size() == 3, "union of dates");
        check(result.get(0).getValue() == 100d, "first unmatched day");
        check(result.get(1).getValue() == 40d, "second unmatched day");
        check(result.get(2).getValue() == 150d, "matching last day");
    }

    private static void ignoresMissingSeries()
    {
        List<Value> result = new BalanceSeriesAggregator().sum(java.util.Arrays.asList(null, List.of()));
        check(result.isEmpty(), "missing data must stay empty");
    }

    private static Value value(long day, double amount)
    {
        return new Value(new Date(day * 86_400_000L), amount);
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
