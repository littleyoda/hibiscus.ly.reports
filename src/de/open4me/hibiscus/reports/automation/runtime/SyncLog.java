package de.open4me.hibiscus.reports.automation.runtime;

public interface SyncLog
{
    void info(String message);

    void warn(String message);

    void error(String message);
}
