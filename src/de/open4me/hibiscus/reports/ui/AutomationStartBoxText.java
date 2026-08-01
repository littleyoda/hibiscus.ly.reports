package de.open4me.hibiscus.reports.ui;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import de.open4me.hibiscus.reports.automation.model.AutomationRun;
import de.open4me.hibiscus.reports.automation.model.RunStatus;

final class AutomationStartBoxText
{
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private AutomationStartBoxText()
    {
    }

    static String lastRunText(AutomationRun run)
    {
        if (run == null)
            return "Letzter Lauf: noch nicht ausgeführt";
        LocalDateTime time = displayTime(run);
        String formatted = time == null ? "kein Zeitpunkt" : DATE_TIME.format(time);
        return "Letzter Lauf: " + formatted + " (" + status(run.status()) + ")";
    }

    private static LocalDateTime displayTime(AutomationRun run)
    {
        if (run.status() == RunStatus.LAEUFT || run.status() == RunStatus.WARTET)
            return run.startedAt();
        return run.finishedAt() == null ? run.startedAt() : run.finishedAt();
    }

    private static String status(RunStatus status)
    {
        return status == null ? RunStatus.GEPLANT.value() : status.value();
    }
}
