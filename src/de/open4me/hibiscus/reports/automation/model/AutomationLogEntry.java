package de.open4me.hibiscus.reports.automation.model;

import java.time.LocalDateTime;

public record AutomationLogEntry(String id, String runId, LocalDateTime createdAt, String level, String message)
{
    public AutomationLogEntry
    {
        level = level == null || level.isBlank() ? "info" : level;
        message = message == null ? "" : message;
    }
}
