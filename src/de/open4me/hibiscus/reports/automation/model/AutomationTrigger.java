package de.open4me.hibiscus.reports.automation.model;

import java.time.LocalDateTime;

public record AutomationTrigger(String id, String automationId, String name, boolean active, String type,
                                String schedule, LocalDateTime nextRun, LocalDateTime lastRun)
{
    public AutomationTrigger
    {
        name = text(name);
        type = text(type).isBlank() ? "cron" : text(type);
        schedule = text(schedule);
    }

    public AutomationTrigger withId(String id)
    {
        return new AutomationTrigger(id, automationId, name, active, type, schedule, nextRun, lastRun);
    }

    public AutomationTrigger withNextRun(LocalDateTime value)
    {
        return new AutomationTrigger(id, automationId, name, active, type, schedule, value, lastRun);
    }

    public AutomationTrigger withLastRun(LocalDateTime value)
    {
        return new AutomationTrigger(id, automationId, name, active, type, schedule, nextRun, value);
    }

    private static String text(String value)
    {
        return value == null ? "" : value;
    }
}
