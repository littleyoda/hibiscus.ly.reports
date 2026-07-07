package de.open4me.hibiscus.reports.model;

import java.time.LocalDate;

public record PeriodBalance(LocalDate start, LocalDate end, String label,
                            double income, double expenses)
{
    public double balance()
    {
        return income - expenses;
    }
}
