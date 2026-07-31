package de.open4me.hibiscus.reports.automation.sql;

import java.sql.Connection;

public interface AutomationConnectionProvider
{
    Connection getConnection() throws Exception;
}
