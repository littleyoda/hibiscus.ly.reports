package de.open4me.hibiscus.reports.automation.runtime;

import java.util.List;
import java.util.Map;

import de.open4me.hibiscus.reports.automation.runtime.AutomationSync.ErrorStrategy;
import de.open4me.hibiscus.reports.model.ReportAccount;

public final class AutomationSyncTests
{
    private AutomationSyncTests()
    {
    }

    public static void run()
    {
        parsesDefaultOptions();
        parsesExplicitErrorStrategy();
        rejectsUnknownErrorStrategy();
        ignoresOptionsAsAccounts();
    }

    private static void parsesDefaultOptions()
    {
        AutomationSync.SyncOptions options = AutomationSync.options(account("1"));

        checkEquals(ErrorStrategy.STOPPEN, options.strategy(), "default error strategy");
        check(!options.explicit(), "default options are implicit");
    }

    private static void parsesExplicitErrorStrategy()
    {
        AutomationSync.SyncOptions options = AutomationSync.options(account("1"), Map.of("beiFehler", "fortsetzen"));

        checkEquals(ErrorStrategy.FORTSETZEN, options.strategy(), "explicit continue strategy");
        check(options.explicit(), "options are explicit");

        AutomationSync.SyncOptions ask = AutomationSync.options(Map.of("beiFehler", "nachfragen"));
        checkEquals(ErrorStrategy.NACHFRAGEN, ask.strategy(), "explicit ask strategy");
    }

    private static void rejectsUnknownErrorStrategy()
    {
        try
        {
            AutomationSync.options(Map.of("beiFehler", "weiter"));
            throw new AssertionError("unknown strategy should fail");
        }
        catch (IllegalArgumentException expected)
        {
            check(expected.getMessage().contains("stoppen"), "error message contains allowed values");
        }
    }

    private static void ignoresOptionsAsAccounts()
    {
        ReportAccount first = account("1");
        ReportAccount second = account("2");

        List<ReportAccount> accounts = AutomationSync.accounts(first, List.of(second));
        checkEquals(List.of(first, second), accounts, "accounts without options");

        AutomationSync.SyncOptions options = AutomationSync.options(first, second, Map.of("beiFehler", "fortsetzen"));
        Object[] values = AutomationSync.valuesWithoutOptions(
            new Object[] { first, second, Map.of("beiFehler", "fortsetzen") }, options);
        checkEquals(List.of(first, second), AutomationSync.accounts(values), "accounts with options removed");
    }

    private static ReportAccount account(String id)
    {
        return new ReportAccount(id, 0, 0, null, "Konto " + id, "12030000",
            "DE0212030000000020205" + id, "Privat", true, false, null);
    }

    private static void check(boolean value, String message)
    {
        if (!value)
            throw new AssertionError(message);
    }

    private static void checkEquals(Object expected, Object actual, String message)
    {
        if (!java.util.Objects.equals(expected, actual))
            throw new AssertionError(message + " expected <" + expected + "> but was <" + actual + ">");
    }
}
