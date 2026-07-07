package de.open4me.hibiscus.reports.model;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;

public enum AggregationInterval
{
    MONTHLY("Monatlich", "pro Monat"),
    QUARTERLY("Quartalsweise", "pro Quartal"),
    YEARLY("Jährlich", "pro Jahr");

    private final String displayName;
    private final String averageLabel;

    AggregationInterval(String displayName, String averageLabel)
    {
        this.displayName = displayName;
        this.averageLabel = averageLabel;
    }

    public LocalDate periodStart(LocalDate date)
    {
        return switch (this)
        {
            case MONTHLY -> date.withDayOfMonth(1);
            case QUARTERLY -> LocalDate.of(date.getYear(), ((date.getMonthValue() - 1) / 3) * 3 + 1, 1);
            case YEARLY -> LocalDate.of(date.getYear(), 1, 1);
        };
    }

    public LocalDate nextPeriod(LocalDate periodStart)
    {
        return switch (this)
        {
            case MONTHLY -> periodStart.plusMonths(1);
            case QUARTERLY -> periodStart.plusMonths(3);
            case YEARLY -> periodStart.plusYears(1);
        };
    }

    public LocalDate periodEnd(LocalDate periodStart)
    {
        return nextPeriod(periodStart).minusDays(1);
    }

    public String label(LocalDate periodStart)
    {
        return label(periodStart, false);
    }

    public String label(LocalDate periodStart, boolean firstPeriod)
    {
        return switch (this)
        {
            case MONTHLY -> Month.from(periodStart).getDisplayName(TextStyle.SHORT, Locale.GERMAN)
                + (firstPeriod || periodStart.getMonthValue() == 1 ? " " + periodStart.getYear() : "");
            case QUARTERLY -> "Q" + (((periodStart.getMonthValue() - 1) / 3) + 1)
                + " " + periodStart.getYear();
            case YEARLY -> Integer.toString(periodStart.getYear());
        };
    }

    public String averageLabel()
    {
        return averageLabel;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
