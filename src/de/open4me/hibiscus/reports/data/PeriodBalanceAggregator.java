package de.open4me.hibiscus.reports.data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.open4me.hibiscus.reports.model.AggregationInterval;
import de.open4me.hibiscus.reports.model.BookingRecord;
import de.open4me.hibiscus.reports.model.DatedBookingRecord;
import de.open4me.hibiscus.reports.model.FlowReport;
import de.open4me.hibiscus.reports.model.PeriodBalance;

public final class PeriodBalanceAggregator
{
    private final FlowAggregator flowAggregator = new FlowAggregator();

    public List<PeriodBalance> aggregate(List<DatedBookingRecord> bookings, LocalDate from,
                                         LocalDate to, AggregationInterval interval)
    {
        if (from == null || to == null || interval == null)
            throw new IllegalArgumentException("Zeitraum und Gruppierung dürfen nicht fehlen");
        if (to.isBefore(from))
            throw new IllegalArgumentException("Das Enddatum muss am oder nach dem Startdatum liegen");

        Map<LocalDate, List<BookingRecord>> grouped = new LinkedHashMap<>();
        for (LocalDate period = interval.periodStart(from); !period.isAfter(to);
             period = interval.nextPeriod(period))
            grouped.put(period, new ArrayList<>());

        if (bookings != null)
        {
            for (DatedBookingRecord dated : bookings)
            {
                if (dated.date().isBefore(from) || dated.date().isAfter(to))
                    continue;
                List<BookingRecord> period = grouped.get(interval.periodStart(dated.date()));
                if (period != null)
                    period.add(dated.booking());
            }
        }

        List<PeriodBalance> result = new ArrayList<>(grouped.size());
        for (Map.Entry<LocalDate, List<BookingRecord>> entry : grouped.entrySet())
        {
            LocalDate calendarStart = entry.getKey();
            LocalDate start = calendarStart.isBefore(from) ? from : calendarStart;
            LocalDate calendarEnd = interval.periodEnd(calendarStart);
            LocalDate end = calendarEnd.isAfter(to) ? to : calendarEnd;
            FlowReport report = flowAggregator.aggregate(entry.getValue(), start, end);
            result.add(new PeriodBalance(start, end, interval.label(calendarStart, result.isEmpty()),
                report.incomeTotal(), report.expenseTotal()));
        }
        return List.copyOf(result);
    }
}
