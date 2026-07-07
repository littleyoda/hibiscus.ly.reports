package de.open4me.hibiscus.reports.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

import de.willuhn.jameica.hbci.report.balance.AccountBalanceProvider;
import de.willuhn.jameica.hbci.report.balance.BookingAccountBalanceProvider;
import de.willuhn.jameica.hbci.server.Value;

final class DepotBalanceValidation
{
    private DepotBalanceValidation()
    {
    }

    static boolean isDepotAccountType(Integer accountType)
    {
        if (accountType == null)
            return false;
        int type = accountType;
        return (type >= 30 && type <= 39) || (type >= 60 && type <= 69);
    }

    static boolean isUsableSeries(List<Value> values)
    {
        if (values == null || values.isEmpty())
            return false;
        for (Value value : values)
        {
            if (value == null || value.getDate() == null || !Double.isFinite(value.getValue()))
                return false;
        }
        return true;
    }

    static boolean hasSpecializedProvider(AccountBalanceProvider provider)
    {
        return provider != null && !(provider instanceof BookingAccountBalanceProvider);
    }

    static Double valueAtOrBefore(List<Value> values, Date date)
    {
        if (values == null || date == null)
            return null;
        Value latest = null;
        for (Value value : values)
        {
            if (value == null || value.getDate() == null || value.getDate().after(date))
                continue;
            if (latest == null || value.getDate().after(latest.getDate()))
                latest = value;
        }
        return latest == null ? null : latest.getValue();
    }

    static boolean differsAtCent(double left, double right)
    {
        if (!Double.isFinite(left) || !Double.isFinite(right))
            return true;
        return cents(left).compareTo(cents(right)) != 0;
    }

    static boolean isZeroAtCent(double value)
    {
        return Double.isFinite(value) && cents(value).compareTo(BigDecimal.ZERO.setScale(2)) == 0;
    }

    static boolean differsMoreThanOnePercent(double providerBalance, double accountBalance)
    {
        if (!Double.isFinite(providerBalance) || !Double.isFinite(accountBalance))
            return true;
        if (isZeroAtCent(accountBalance))
            return !isZeroAtCent(providerBalance);

        BigDecimal difference = cents(providerBalance).subtract(cents(accountBalance)).abs();
        BigDecimal base = cents(accountBalance).abs();
        return difference.compareTo(base.multiply(BigDecimal.valueOf(0.01))) > 0;
    }

    static Double relativeDifferencePercent(double providerBalance, double accountBalance)
    {
        if (!Double.isFinite(providerBalance) || !Double.isFinite(accountBalance)
            || isZeroAtCent(accountBalance))
            return null;

        BigDecimal difference = cents(providerBalance).subtract(cents(accountBalance)).abs();
        BigDecimal base = cents(accountBalance).abs();
        return difference.multiply(BigDecimal.valueOf(100)).divide(base, 6, RoundingMode.HALF_UP)
            .doubleValue();
    }

    static double difference(double providerBalance, double accountBalance)
    {
        return cents(providerBalance).subtract(cents(accountBalance)).doubleValue();
    }

    private static BigDecimal cents(double value)
    {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
