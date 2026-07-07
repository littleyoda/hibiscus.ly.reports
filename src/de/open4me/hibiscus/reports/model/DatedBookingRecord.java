package de.open4me.hibiscus.reports.model;

import java.time.LocalDate;

public record DatedBookingRecord(LocalDate date, BookingRecord booking)
{
    public DatedBookingRecord
    {
        if (date == null || booking == null)
            throw new IllegalArgumentException("Datum und Buchung dürfen nicht fehlen");
    }
}
