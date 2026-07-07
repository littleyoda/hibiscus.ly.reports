package de.open4me.hibiscus.reports.data;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

public final class HibiscusDataProviderTests
{
    private HibiscusDataProviderTests()
    {
    }

    public static void run()
    {
        LocalDate expected = LocalDate.of(2025, 7, 15);
        check(expected.equals(HibiscusDataProvider.toLocalDate(java.sql.Date.valueOf(expected))),
            "java.sql.Date conversion");
        Date utilDate = Date.from(expected.atStartOfDay(ZoneId.systemDefault()).toInstant());
        check(expected.equals(HibiscusDataProvider.toLocalDate(utilDate)), "java.util.Date conversion");
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
