package de.open4me.hibiscus.reports.automation.sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;

public final class AutomationSql
{
    private final AutomationConnectionProvider connectionProvider;

    public AutomationSql(AutomationConnectionProvider connectionProvider)
    {
        this.connectionProvider = connectionProvider;
    }

    public static AutomationSql hibiscus()
    {
        return new AutomationSql(new HibiscusAutomationConnectionProvider());
    }

    public void checkForUpdates() throws ApplicationException
    {
        try (Connection connection = connectionProvider.getConnection())
        {
            AutomationSqlDialect dialect = AutomationSqlDialect.detect(connection);
            int currentVersion = currentVersion(connection);
            List<AutomationSqlChange> changes = AutomationSqlChange.changesSince(currentVersion, dialect);
            for (AutomationSqlChange change : changes)
                apply(connection, change);
        }
        catch (Exception e)
        {
            throw new ApplicationException("Automation-Datenbank konnte nicht aktualisiert werden: "
                + e.getMessage(), e);
        }
    }

    private static void apply(Connection connection, AutomationSqlChange change) throws Exception
    {
        Logger.info("hibiscus.ly.reports automation: updating database to " + change.version());
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement())
        {
            for (String sql : change.statements())
                statement.execute(sql);
            if (change.version() > 1)
                statement.execute("update automation_cfg set cfg_value = '" + change.version()
                    + "' where cfg_key = 'dbversion'");
            connection.commit();
        }
        catch (Exception e)
        {
            connection.rollback();
            throw e;
        }
        finally
        {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static int currentVersion(Connection connection)
    {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                 "select cfg_value from automation_cfg where cfg_key = 'dbversion'"))
        {
            if (result.next())
                return Integer.parseInt(result.getString(1));
        }
        catch (Exception ignored)
        {
        }
        return 0;
    }
}
