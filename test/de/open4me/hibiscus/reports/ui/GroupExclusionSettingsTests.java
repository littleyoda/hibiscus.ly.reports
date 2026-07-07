package de.open4me.hibiscus.reports.ui;

import java.util.Set;

public final class GroupExclusionSettingsTests
{
    private GroupExclusionSettingsTests()
    {
    }

    public static void run()
    {
        check(GroupExclusionSettings.parse(null).isEmpty(), "missing exclusions");
        check(GroupExclusionSettings.parse(new String[0]).isEmpty(), "explicit empty exclusions");
        check(GroupExclusionSettings.parse(new String[] { "group:Giro", " ungrouped ", "" })
            .equals(Set.of("group:Giro", "ungrouped")), "stored exclusions");
        check(!AccountGroupChoice.named("ungrouped").storageKey()
            .equals(AccountGroupChoice.ungrouped().storageKey()), "synthetic group key collision");
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
