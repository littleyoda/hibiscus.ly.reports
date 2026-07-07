package de.open4me.hibiscus.reports.ui;

import java.time.LocalDate;
import java.util.List;

import org.eclipse.swt.widgets.Display;

import de.open4me.hibiscus.reports.model.AggregationInterval;
import de.open4me.hibiscus.reports.model.PeriodBalance;

public final class OverviewPngExportSmokeTest
{
    private OverviewPngExportSmokeTest()
    {
    }

    public static void main(String[] args)
    {
        if (args.length != 1)
            throw new IllegalArgumentException("Ausgabedatei fehlt");
        Display display = new Display();
        try
        {
            List<PeriodBalance> periods = List.of(
                new PeriodBalance(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31),
                    "Jan. 2025", 3000d, 1800d),
                new PeriodBalance(LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28),
                    "Feb. 2025", 2800d, 3200d));
            OverviewPngExporter.save(display, periods, LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 2, 28), AggregationInterval.MONTHLY, args[0]);
        }
        finally
        {
            display.dispose();
        }
    }
}
