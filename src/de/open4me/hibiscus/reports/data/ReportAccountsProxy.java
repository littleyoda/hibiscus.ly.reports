package de.open4me.hibiscus.reports.data;

import java.util.Iterator;
import java.util.List;

import de.open4me.hibiscus.reports.model.ReportAccount;
import de.willuhn.jameica.hbci.gui.filter.KontoFilter;

public final class ReportAccountsProxy implements Iterable<ReportAccount>
{
    private final ReportAccountProvider provider;
    private List<ReportAccount> activeAccounts;
    private List<ReportAccount> allAccounts;

    public ReportAccountsProxy(ReportAccountProvider provider)
    {
        this.provider = provider;
    }

    @Override
    public Iterator<ReportAccount> iterator()
    {
        return getAktive().iterator();
    }

    public List<ReportAccount> getAktive()
    {
        if (activeAccounts == null)
            activeAccounts = load(KontoFilter.ACTIVE);
        return activeAccounts;
    }

    public List<ReportAccount> getAlle()
    {
        if (allAccounts == null)
            allAccounts = load(KontoFilter.ALL);
        return allAccounts;
    }

    public int size()
    {
        return getAktive().size();
    }

    public boolean isEmpty()
    {
        return getAktive().isEmpty();
    }

    private List<ReportAccount> load(KontoFilter filter)
    {
        try
        {
            return List.copyOf(provider.loadAccounts(filter));
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Konten konnten nicht geladen werden", e);
        }
    }
}
