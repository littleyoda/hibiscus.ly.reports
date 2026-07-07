package de.open4me.hibiscus.reports.ui;

public final class AccountBalanceConsistencyTests
{
    private AccountBalanceConsistencyTests()
    {
    }

    public static void run()
    {
        check(AccountBalanceConsistency.hasDisabledAccountMismatch(false, true, 0d, 64_500d),
            "disabled account mismatch");
        check(!AccountBalanceConsistency.hasDisabledAccountMismatch(true, true, 0d, 64_500d),
            "active-only suppresses warning");
        check(!AccountBalanceConsistency.hasDisabledAccountMismatch(false, false, 0d, 64_500d),
            "active account suppresses warning");
        check(!AccountBalanceConsistency.hasDisabledAccountMismatch(false, true, 10d, 64_500d),
            "non-zero account balance is not this inconsistency");
        check(!AccountBalanceConsistency.hasDisabledAccountMismatch(false, true, 0d, 0d),
            "zero last balance");
        check(!AccountBalanceConsistency.hasDisabledAccountMismatch(false, true, 0d, null),
            "missing booked transaction");
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
