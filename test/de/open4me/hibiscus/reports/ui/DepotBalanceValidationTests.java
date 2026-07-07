package de.open4me.hibiscus.reports.ui;

import java.util.Date;
import java.util.List;

import de.willuhn.jameica.hbci.gui.chart.AbstractChartDataSaldo;
import de.willuhn.jameica.hbci.report.balance.AccountBalanceProvider;
import de.willuhn.jameica.hbci.report.balance.BookingAccountBalanceProvider;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.server.Value;

public final class DepotBalanceValidationTests
{
    private DepotBalanceValidationTests()
    {
    }

    public static void run()
    {
        recognizesDepotAccountTypes();
        recognizesSpecializedProvider();
        validatesProviderSeries();
        selectsLastValueAtDate();
        comparesAtCentPrecision();
        detectsZeroAtCentPrecision();
        detectsRelativeDifference();
    }

    private static void recognizesSpecializedProvider()
    {
        check(!DepotBalanceValidation.hasSpecializedProvider(null), "missing provider");
        check(!DepotBalanceValidation.hasSpecializedProvider(new BookingAccountBalanceProvider()),
            "booking provider is not suitable for depots");
        check(DepotBalanceValidation.hasSpecializedProvider(new TestDepotProvider()),
            "specialized depot provider");
    }

    private static void recognizesDepotAccountTypes()
    {
        check(DepotBalanceValidation.isDepotAccountType(30), "securities depot");
        check(DepotBalanceValidation.isDepotAccountType(39), "securities depot range");
        check(DepotBalanceValidation.isDepotAccountType(60), "fund depot");
        check(DepotBalanceValidation.isDepotAccountType(69), "fund depot range");
        check(!DepotBalanceValidation.isDepotAccountType(1), "checking account");
        check(!DepotBalanceValidation.isDepotAccountType(null), "missing account type");
    }

    private static void validatesProviderSeries()
    {
        Date date = new Date(1_000L);
        check(DepotBalanceValidation.isUsableSeries(List.of(new Value(date, 0d))),
            "zero depot series is valid");
        check(!DepotBalanceValidation.isUsableSeries(null), "null series");
        check(!DepotBalanceValidation.isUsableSeries(List.of()), "empty series");
        check(!DepotBalanceValidation.isUsableSeries(List.of(new Value(date, Double.NaN))),
            "NaN series");
        check(!DepotBalanceValidation.isUsableSeries(List.of(new Value(date, Double.POSITIVE_INFINITY))),
            "infinite series");
    }

    private static void selectsLastValueAtDate()
    {
        List<Value> values = List.of(new Value(new Date(1_000L), 10d),
            new Value(new Date(2_000L), 20d), new Value(new Date(3_000L), 30d));
        check(DepotBalanceValidation.valueAtOrBefore(values, new Date(2_500L)) == 20d,
            "latest value before date");
        check(DepotBalanceValidation.valueAtOrBefore(values, new Date(500L)) == null,
            "no value before date");
    }

    private static void comparesAtCentPrecision()
    {
        check(!DepotBalanceValidation.differsAtCent(100.001d, 100.004d),
            "sub-cent differences ignored");
        check(DepotBalanceValidation.differsAtCent(100d, 100.01d),
            "one cent difference detected");
        check(DepotBalanceValidation.difference(90d, 100d) == -10d,
            "signed difference");
    }

    private static void detectsZeroAtCentPrecision()
    {
        check(DepotBalanceValidation.isZeroAtCent(0.004d),
            "sub-cent values are treated as zero");
        check(!DepotBalanceValidation.isZeroAtCent(0.005d),
            "half-cent values round to one cent");
        check(!DepotBalanceValidation.isZeroAtCent(Double.NaN),
            "NaN is not zero");
    }

    private static void detectsRelativeDifference()
    {
        check(!DepotBalanceValidation.differsMoreThanOnePercent(99d, 100d),
            "one percent difference is accepted");
        check(DepotBalanceValidation.differsMoreThanOnePercent(98.99d, 100d),
            "more than one percent difference is detected");
        check(!DepotBalanceValidation.differsMoreThanOnePercent(0d, 0d),
            "zero values match");
        check(DepotBalanceValidation.differsMoreThanOnePercent(1d, 0d),
            "non-zero calculated value differs from zero bank value");
        check(DepotBalanceValidation.relativeDifferencePercent(98d, 100d) == 2d,
            "relative difference percent");
        check(DepotBalanceValidation.relativeDifferencePercent(1d, 0d) == null,
            "relative difference against zero is undefined");
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }

    private static final class TestDepotProvider implements AccountBalanceProvider
    {
        @Override
        public boolean supports(Konto account)
        {
            return true;
        }

        @Override
        public List<Value> getBalanceData(Konto account, Date start, Date end)
        {
            return List.of();
        }

        @Override
        public AbstractChartDataSaldo getBalanceChartData(Konto account, Date start, Date end)
        {
            return null;
        }

        @Override
        public String getName()
        {
            return "Test depot provider";
        }
    }
}
