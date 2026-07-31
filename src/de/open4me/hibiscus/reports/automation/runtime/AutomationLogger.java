package de.open4me.hibiscus.reports.automation.runtime;

import de.open4me.hibiscus.reports.automation.sql.AutomationRepository;
import de.willuhn.logging.Logger;

public final class AutomationLogger implements SyncLog
{
    private final AutomationRepository repository;
    private final String runId;
    private final String automationName;

    public AutomationLogger(AutomationRepository repository, String runId)
    {
        this(repository, runId, "");
    }

    public AutomationLogger(AutomationRepository repository, String runId, String automationName)
    {
        this.repository = repository;
        this.runId = runId;
        this.automationName = automationName == null ? "" : automationName;
    }

    public void info(String message)
    {
        write("info", message);
    }

    public void warn(String message)
    {
        write("warn", message);
    }

    public void error(String message)
    {
        write("error", message);
    }

    private void write(String level, String message)
    {
        String safeMessage = message == null ? "" : message;
        writeJameica(level, safeMessage);
        try
        {
            repository.addLog(runId, level, safeMessage);
        }
        catch (Exception e)
        {
            Logger.error("Automation-Log konnte nicht gespeichert werden", e);
        }
    }

    private void writeJameica(String level, String message)
    {
        String prefix = "Automation";
        if (!automationName.isBlank())
            prefix += " \"" + automationName + "\"";
        prefix += " [" + runId + "]: ";

        if ("error".equalsIgnoreCase(level))
            Logger.error(prefix + message);
        else if ("warn".equalsIgnoreCase(level))
            Logger.warn(prefix + message);
        else
            Logger.info(prefix + message);
    }
}
