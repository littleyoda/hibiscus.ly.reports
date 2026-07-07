package de.open4me.hibiscus.reports.model;

import java.util.List;

public record BookingRecord(double amount, List<CategoryInfo> categoryPath, boolean pending)
{
    public BookingRecord
    {
        categoryPath = categoryPath == null ? List.of() : List.copyOf(categoryPath);
    }
}

