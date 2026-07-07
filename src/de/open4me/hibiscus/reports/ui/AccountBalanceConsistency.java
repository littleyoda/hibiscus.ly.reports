package de.open4me.hibiscus.reports.ui;

final class AccountBalanceConsistency
{
    private static final double HALF_CENT = 0.005d;

    private AccountBalanceConsistency()
    {
    }

    static boolean hasDisabledAccountMismatch(boolean onlyActive, boolean disabled,
                                              double accountBalance, Double lastBookingBalance)
    {
        return !onlyActive && disabled && isZero(accountBalance) && lastBookingBalance != null
            && !isZero(lastBookingBalance);
    }

    private static boolean isZero(double value)
    {
        return Math.abs(value) < HALF_CENT;
    }
}
