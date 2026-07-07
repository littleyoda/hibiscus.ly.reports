package de.open4me.hibiscus.reports.ui;

import java.util.List;
import java.util.Set;

import de.open4me.hibiscus.reports.model.AccountInfo;

public final class AccountSelectionSettingsTests
{
    private AccountSelectionSettingsTests()
    {
    }

    public static void run()
    {
        List<AccountInfo> accounts = List.of(
            new AccountInfo("1", "Privat", "Giro", "EUR", false),
            new AccountInfo("2", null, "Tagesgeld", "EUR", false));

        Set<String> explicitEmpty = AccountSelectionSettings.resolve(accounts, null, true, "2", true);
        check(explicitEmpty.isEmpty(), "initialized empty exclusions must survive restart");

        Set<String> migrated = AccountSelectionSettings.resolve(accounts, null, false, "2", true);
        check(migrated.equals(Set.of("1")), "legacy included accounts migration");

        Set<String> stored = AccountSelectionSettings.resolve(accounts, new String[] { "2" }, false,
            "1,2", true);
        check(stored.equals(Set.of("2")), "stored exclusions take precedence over legacy settings");
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
