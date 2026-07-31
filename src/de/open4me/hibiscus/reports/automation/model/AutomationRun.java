package de.open4me.hibiscus.reports.automation.model;

import java.time.LocalDateTime;

public record AutomationRun(String id, String automationId, String triggerId, RunStatus status, String source,
                            boolean testRun, LocalDateTime startedAt, LocalDateTime finishedAt, String warning,
                            String error)
{
    public AutomationRun
    {
        status = status == null ? RunStatus.GEPLANT : status;
        source = source == null ? "" : source;
        warning = warning == null ? "" : warning;
        error = error == null ? "" : error;
    }
}
