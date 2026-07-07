package de.open4me.hibiscus.reports;

import java.time.LocalDate;
import java.util.List;

import de.open4me.hibiscus.reports.data.PeriodBalanceAggregator;
import de.open4me.hibiscus.reports.model.AggregationInterval;
import de.open4me.hibiscus.reports.model.BookingRecord;
import de.open4me.hibiscus.reports.model.CategoryInfo;
import de.open4me.hibiscus.reports.model.DatedBookingRecord;
import de.open4me.hibiscus.reports.model.PeriodBalance;

final class PeriodBalanceAggregatorTests
{
    static void run()
    {
        createsMonthlyPartialAndEmptyPeriods();
        groupsCalendarQuartersAndYears();
        netsCategoriesInsideEachPeriod();
        labelsFirstMonthAndJanuaryWithYear();
    }

    private static void createsMonthlyPartialAndEmptyPeriods()
    {
        List<PeriodBalance> result = aggregate(List.of(
            booking(2025, 1, 15, 100, "Gehalt"),
            booking(2025, 3, 10, -40, "Kosten")),
            LocalDate.of(2025, 1, 15), LocalDate.of(2025, 3, 20), AggregationInterval.MONTHLY);

        checkEquals(3, result.size(), "monthly period count");
        checkEquals(LocalDate.of(2025, 1, 15), result.get(0).start(), "clipped start");
        checkEquals(LocalDate.of(2025, 3, 20), result.get(2).end(), "clipped end");
        checkEquals(0d, result.get(1).income(), "empty income");
        checkEquals(0d, result.get(1).expenses(), "empty expenses");
    }

    private static void groupsCalendarQuartersAndYears()
    {
        List<DatedBookingRecord> bookings = List.of(
            booking(2024, 12, 31, 10, "Einnahmen"),
            booking(2025, 1, 1, 20, "Einnahmen"),
            booking(2025, 4, 1, -5, "Kosten"));
        List<PeriodBalance> quarters = aggregate(bookings, LocalDate.of(2024, 12, 1),
            LocalDate.of(2025, 4, 2), AggregationInterval.QUARTERLY);
        checkEquals(3, quarters.size(), "quarter count");
        checkEquals("Q4 2024", quarters.get(0).label(), "quarter label");
        checkEquals("Q2 2025", quarters.get(2).label(), "quarter label after boundary");

        List<PeriodBalance> years = aggregate(bookings, LocalDate.of(2024, 12, 1),
            LocalDate.of(2025, 4, 2), AggregationInterval.YEARLY);
        checkEquals(2, years.size(), "year count");
        checkEquals(10d, years.get(0).income(), "first year income");
        checkEquals(15d, years.get(1).balance(), "second year balance");
    }

    private static void netsCategoriesInsideEachPeriod()
    {
        List<PeriodBalance> result = aggregate(List.of(
            booking(2025, 1, 2, -100, "Kosten"),
            booking(2025, 1, 3, 25, "Kosten"),
            booking(2025, 1, 4, 200, "Gehalt")),
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31), AggregationInterval.MONTHLY);
        checkEquals(200d, result.get(0).income(), "net income");
        checkEquals(75d, result.get(0).expenses(), "net expenses");
    }

    private static void labelsFirstMonthAndJanuaryWithYear()
    {
        List<PeriodBalance> result = aggregate(List.of(), LocalDate.of(2024, 11, 1),
            LocalDate.of(2025, 2, 28), AggregationInterval.MONTHLY);
        checkEquals("Nov. 2024", result.get(0).label(), "first month with year");
        checkEquals("Dez.", result.get(1).label(), "regular month without year");
        checkEquals("Jan. 2025", result.get(2).label(), "January with year");
        checkEquals("Feb.", result.get(3).label(), "month after January without year");
    }

    private static List<PeriodBalance> aggregate(List<DatedBookingRecord> bookings, LocalDate from,
                                                  LocalDate to, AggregationInterval interval)
    {
        return new PeriodBalanceAggregator().aggregate(bookings, from, to, interval);
    }

    private static DatedBookingRecord booking(int year, int month, int day, double amount, String category)
    {
        CategoryInfo info = new CategoryInfo(category, category, false, null);
        return new DatedBookingRecord(LocalDate.of(year, month, day),
            new BookingRecord(amount, List.of(info), false));
    }

    private static void checkEquals(double expected, double actual, String message)
    {
        if (Math.abs(expected - actual) > 0.00001d)
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }

    private static void checkEquals(Object expected, Object actual, String message)
    {
        if (!expected.equals(actual))
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
}
