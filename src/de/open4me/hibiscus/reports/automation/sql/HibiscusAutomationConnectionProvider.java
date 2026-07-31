package de.open4me.hibiscus.reports.automation.sql;

import java.sql.Connection;
import java.sql.DriverManager;

import de.willuhn.jameica.hbci.HBCI;
import de.willuhn.jameica.hbci.server.AbstractDBSupportImpl;
import de.willuhn.jameica.hbci.server.DBSupportMySqlImpl;
import de.willuhn.jameica.hbci.server.HBCIDBServiceImpl;
import de.willuhn.jameica.system.Application;

public final class HibiscusAutomationConnectionProvider implements AutomationConnectionProvider
{
    private AbstractDBSupportImpl driver;

    @Override
    public Connection getConnection() throws Exception
    {
        if (driver == null)
        {
            HBCIDBServiceImpl db = (HBCIDBServiceImpl) Application.getServiceFactory().lookup(HBCI.class, "database");
            driver = (AbstractDBSupportImpl) db.getDriver();
        }
        String url = driver.getJdbcUrl();
        if (driver instanceof DBSupportMySqlImpl)
            url += "&useServerPrepStmts=false&rewriteBatchedStatements=true";
        return DriverManager.getConnection(url, driver.getJdbcUsername(), driver.getJdbcPassword());
    }
}
