package de.open4me.hibiscus.reports.ui;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import de.open4me.hibiscus.reports.model.AggregationInterval;
import de.open4me.hibiscus.reports.model.PeriodBalance;

final class OverviewExportText
{
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private OverviewExportText()
    {
    }

    static String period(LocalDate from, LocalDate to, AggregationInterval interval)
    {
        return DATE.format(from) + " – " + DATE.format(to) + "     Gruppierung: " + interval;
    }

    static String summary(List<PeriodBalance> periods, AggregationInterval interval)
    {
        double income = periods.stream().mapToDouble(PeriodBalance::income).sum();
        double expenses = periods.stream().mapToDouble(PeriodBalance::expenses).sum();
        double balance = income - expenses;
        int count = Math.max(1, periods.size());
        return "Einnahmen: " + euro(income) + "     Ausgaben: " + euro(expenses)
            + "     Bilanz: " + euro(balance) + "     " + interval.averageLabel()
            + ": " + euro(balance / count);
    }

    static String euro(double value)
    {
        return NumberFormat.getIntegerInstance(Locale.GERMANY).format(value) + " €";
    }
}
