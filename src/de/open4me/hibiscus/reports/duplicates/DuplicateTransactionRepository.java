package de.open4me.hibiscus.reports.duplicates;

import java.rmi.RemoteException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.hbci.Settings;
import de.willuhn.jameica.hbci.gui.filter.KontoFilter;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.hbci.rmi.UmsatzTyp;
import de.willuhn.jameica.hbci.server.KontoUtil;
import de.willuhn.jameica.hbci.server.UmsatzUtil;
import de.willuhn.jameica.hbci.server.VerwendungszweckUtil;

public final class DuplicateTransactionRepository
{
    public List<Konto> loadActiveAccounts() throws RemoteException
    {
        return List.copyOf(KontoUtil.getKonten(KontoFilter.ACTIVE));
    }

    public List<DuplicateTransaction> loadTransactions(Set<String> accountIds, LocalDate from,
                                                        LocalDate to) throws RemoteException
    {
        if (accountIds == null || accountIds.isEmpty())
            return List.of();

        DBIterator transactions = UmsatzUtil.getUmsaetze();
        transactions.addFilter("datum >= ?", Date.valueOf(from));
        transactions.addFilter("datum <= ?", Date.valueOf(to));
        String placeholders = String.join(",", Collections.nCopies(accountIds.size(), "?"));
        transactions.addFilter("konto_id in (" + placeholders + ")", accountIds.toArray());

        List<DuplicateTransaction> result = new ArrayList<>();
        while (transactions.hasNext())
        {
            Umsatz transaction = (Umsatz) transactions.next();
            if (transaction.hasFlag(Umsatz.FLAG_NOTBOOKED) || transaction.getDatum() == null)
                continue;
            result.add(toDuplicateTransaction(transaction));
        }
        return List.copyOf(result);
    }

    public Umsatz loadTransaction(String id) throws Exception
    {
        return Settings.getDBService().createObject(Umsatz.class, id);
    }

    private static DuplicateTransaction toDuplicateTransaction(Umsatz transaction) throws RemoteException
    {
        Konto account = transaction.getKonto();
        UmsatzTyp category = transaction.getUmsatzTyp();
        return new DuplicateTransaction(transaction.getID(), account.getID(), accountName(account),
            toLocalDate(transaction.getDatum()), transaction.getBetrag(), transaction.getSaldo(),
            transaction.getGegenkontoNummer(), category == null ? "" : category.getName(),
            purpose(transaction), text(transaction.getKommentar()));
    }

    private static String accountName(Konto account) throws RemoteException
    {
        String name = account.getBezeichnung();
        if (name == null || name.isBlank())
            name = account.getLongName();
        if (name == null || name.isBlank())
            name = account.getName();
        return text(name);
    }

    private static String purpose(Umsatz transaction) throws RemoteException
    {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, transaction.getZweck());
        appendLine(builder, transaction.getZweck2());
        String[] additional = transaction.getWeitereVerwendungszwecke();
        if (additional != null && additional.length > 0)
            builder.append(VerwendungszweckUtil.merge(additional));
        return builder.toString();
    }

    private static void appendLine(StringBuilder builder, String value)
    {
        if (value != null && !value.isBlank())
            builder.append(value).append('\n');
    }

    private static LocalDate toLocalDate(java.util.Date date)
    {
        if (date instanceof java.sql.Date sqlDate)
            return sqlDate.toLocalDate();
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static String text(String value)
    {
        return value == null ? "" : value;
    }
}
