package de.open4me.hibiscus.reports.ui;

import java.util.Date;
import java.util.List;

import de.willuhn.jameica.hbci.server.Value;

public final class BalanceSeriesDetailsTests
{
    private BalanceSeriesDetailsTests()
    {
    }

    public static void run()
    {
        long first = 86_400_000L;
        long second = 2 * first;
        BalanceSeriesDetails details = new BalanceSeriesDetails(List.of(
            new BalanceSeriesDetails.AccountSeries("1", "Giro",
                List.of(value(first, 100d), value(second, 120d))),
            new BalanceSeriesDetails.AccountSeries("2", "Tagesgeld", List.of(value(first, 300d))),
            new BalanceSeriesDetails.AccountSeries("3", "Leer", List.of(value(first, 0d))),
            new BalanceSeriesDetails.AccountSeries("4", "Rundungsrest", List.of(value(first, 0.0004d)))));

        List<BalanceSeriesDetails.AccountValue> values = details.at(first);
        check(values.size() == 4, "account count at first date");
        check(values.stream().anyMatch(value -> value.accountId().equals("1")
            && value.accountName().equals("Giro") && value.value() == 100d),
            "first account value");
        check(values.stream().anyMatch(value -> value.accountId().equals("2")
            && value.accountName().equals("Tagesgeld") && value.value() == 300d),
            "second account value");
        check(details.at(second).size() == 4, "previous balances carried to second date");
        check(details.at(0L).isEmpty(), "unknown date");
        List<BalanceSeriesDetails.AccountValue> visible = details.nonZeroAt(first);
        check(visible.size() == 2, "zero balances hidden");
        check(visible.stream().noneMatch(value -> value.accountName().equals("Leer")),
            "zero balance account omitted");
        check(visible.stream().noneMatch(value -> value.accountName().equals("Rundungsrest")),
            "sub-cent balance omitted");

        BalanceSeriesDetails closedAccount = new BalanceSeriesDetails(List.of(
            new BalanceSeriesDetails.AccountSeries("300", "Umbuchung", List.of(
                value(first, 64_500d), value(second, 10_500d), value(3 * first, 0d)))));
        check(closedAccount.at(10 * first).get(0).accountId().equals("300"),
            "account resolved by id");
        check(closedAccount.at(10 * first).get(0).value() == 0d,
            "last balance carried forward by account id");
        check(closedAccount.nonZeroAt(10 * first).isEmpty(),
            "closed account hidden after zero balance");
    }

    private static Value value(long time, double amount)
    {
        return new Value(new Date(time), amount);
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
