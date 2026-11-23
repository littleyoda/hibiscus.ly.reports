package de.open4me.hibiscus.reports.mcp;

import java.time.Instant;
import java.util.List;

public final class HibiscusTransactionDeleterTests
{
    private HibiscusTransactionDeleterTests()
    {
    }

    public static void run()
    {
        acceptsOnlyCanonicalTransactionIds();
        reloadsOnlyReadOnlyTransactionListViews();
        confirmTimeCountsFromRequestStart();
    }

    private static void confirmTimeCountsFromRequestStart()
    {
        Instant start = Instant.parse("2026-01-01T10:00:00Z");
        check(HibiscusTransactionDeleter.remainingConfirmTime(start, start.plusSeconds(3))
                .equals(HibiscusTransactionDeleter.CONFIRM_TIMEOUT.minusSeconds(3)),
            "time spent before the dialog is subtracted from the timeout");
        check(HibiscusTransactionDeleter.remainingConfirmTime(start, start.plusSeconds(45))
                .compareTo(HibiscusTransactionDeleter.MIN_CONFIRM_TIME) < 0,
            "a late dialog would have less than the minimum time left");
    }

    private static void reloadsOnlyReadOnlyTransactionListViews()
    {
        check(HibiscusTransactionDeleter.isTransactionListView("de.willuhn.jameica.hbci.gui.views.KontoauszugList"),
            "Umsaetze view is reloaded");
        check(HibiscusTransactionDeleter.isTransactionListView("de.willuhn.jameica.hbci.gui.views.UmsatzTypTree"),
            "Umsaetze nach Kategorien view is reloaded");
        // Views with input fields would lose unsaved edits on reload.
        for (String other : List.of("de.willuhn.jameica.hbci.gui.views.KontoNew",
            "de.willuhn.jameica.hbci.gui.views.UmsatzDetailEdit", "de.willuhn.jameica.hbci.gui.views.UmsatzDetail",
            "de.willuhn.jameica.hbci.gui.views.KontoList", ""))
            check(!HibiscusTransactionDeleter.isTransactionListView(other), "not reloaded: '" + other + "'");
        check(!HibiscusTransactionDeleter.isTransactionListView(null), "null view");
    }

    private static void acceptsOnlyCanonicalTransactionIds()
    {
        for (String valid : List.of("1", "7", "4711", "2147483647"))
            check(HibiscusTransactionDeleter.isTransactionId(valid), "valid id " + valid);
        for (String invalid : List.of("", "0", "007", "-1", "1.0", "x", " 7", "2147483648",
            "99999999999999999999"))
            check(!HibiscusTransactionDeleter.isTransactionId(invalid), "invalid id '" + invalid + "'");
        check(!HibiscusTransactionDeleter.isTransactionId(null), "null id");
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
