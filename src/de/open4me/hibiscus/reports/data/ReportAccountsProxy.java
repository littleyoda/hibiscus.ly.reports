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

    public void invalidate()
    {
        activeAccounts = null;
        allAccounts = null;
    }

    public ReportAccount mitIban(String iban)
    {
        if (iban == null || iban.isBlank())
            return null;
        String normalized = normalizeIban(iban);
        for (ReportAccount account : getAlle())
        {
            if (normalized.equals(normalizeIban(account.getIban())))
                return account;
        }
        return null;
    }

    public ReportAccount mitKontonummer(String kontonummer)
    {
        return findByNormalized(kontonummer, ReportAccount::getKontonummer);
    }

    public ReportAccount mitKundenkennung(String kundenkennung)
    {
        return mitKundennummer(kundenkennung);
    }

    public ReportAccount mitKundennummer(String kundennummer)
    {
        return findByNormalized(kundennummer, ReportAccount::getKundennummer);
    }

    public ReportAccount mitBezeichnung(String bezeichnung)
    {
        return findByText(bezeichnung, ReportAccount::getBezeichnung);
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

    private static String normalizeIban(String value)
    {
        return value == null ? "" : value.replace(" ", "").toUpperCase(java.util.Locale.ROOT);
    }

    private ReportAccount findByNormalized(String value, java.util.function.Function<ReportAccount, String> getter)
    {
        if (value == null || value.isBlank())
            return null;
        String normalized = normalizeIdentifier(value);
        for (ReportAccount account : getAlle())
        {
            if (normalized.equals(normalizeIdentifier(getter.apply(account))))
                return account;
        }
        return null;
    }

    private ReportAccount findByText(String value, java.util.function.Function<ReportAccount, String> getter)
    {
        if (value == null || value.isBlank())
            return null;
        String normalized = normalizeText(value);
        for (ReportAccount account : getAlle())
        {
            if (normalized.equals(normalizeText(getter.apply(account))))
                return account;
        }
        return null;
    }

    private static String normalizeIdentifier(String value)
    {
        return value == null ? "" : value.replace(" ", "").toUpperCase(java.util.Locale.ROOT);
    }

    private static String normalizeText(String value)
    {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
