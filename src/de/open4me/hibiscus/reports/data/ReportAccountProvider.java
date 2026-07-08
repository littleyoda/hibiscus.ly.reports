package de.open4me.hibiscus.reports.data;

import java.util.List;

import de.open4me.hibiscus.reports.model.ReportAccount;
import de.willuhn.jameica.hbci.gui.filter.KontoFilter;

public interface ReportAccountProvider
{
    List<ReportAccount> loadAccounts(KontoFilter filter) throws Exception;
}
