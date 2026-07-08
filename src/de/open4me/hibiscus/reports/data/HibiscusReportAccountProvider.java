package de.open4me.hibiscus.reports.data;

import java.util.ArrayList;
import java.time.LocalDate;
import java.util.List;

import de.open4me.hibiscus.reports.model.ReportAccount;
import de.willuhn.jameica.hbci.gui.filter.KontoFilter;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.server.KontoUtil;

public final class HibiscusReportAccountProvider implements ReportAccountProvider
{
    private final ReportTransactionProvider transactionProvider;

    public HibiscusReportAccountProvider()
    {
        this(new HibiscusReportTransactionProvider());
    }

    public HibiscusReportAccountProvider(ReportTransactionProvider transactionProvider)
    {
        this.transactionProvider = transactionProvider;
    }

    @Override
    public List<ReportAccount> loadAccounts(KontoFilter filter) throws Exception
    {
        List<ReportAccount> result = new ArrayList<>();
        for (Konto account : KontoUtil.getKonten(filter))
        {
            String name = account.getBezeichnung();
            if (name == null || name.isBlank())
                name = account.getLongName();
            result.add(new ReportAccount(account.getID(), account.getSaldo(), account.getSaldoAvailable(),
                toLocalDate(account.getSaldoDatum()), name, account.getBLZ(),
                account.getIban(), account.getKategorie(),
                ReportTransactionsProxy.forAccount(transactionProvider, account.getID())));
        }
        return result;
    }

    private static LocalDate toLocalDate(java.util.Date date)
    {
        return date == null ? null : HibiscusDataProvider.toLocalDate(date);
    }
}
