package de.open4me.hibiscus.reports.data;

import java.rmi.RemoteException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.open4me.hibiscus.reports.model.CategoryInfo;
import de.open4me.hibiscus.reports.model.ReportAccount;
import de.open4me.hibiscus.reports.model.ReportTransaction;
import de.willuhn.datasource.GenericObjectNode;
import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.hbci.rmi.UmsatzTyp;
import de.willuhn.jameica.hbci.server.UmsatzUtil;

public final class HibiscusReportTransactionProvider implements ReportTransactionProvider
{
    @Override
    public List<ReportTransaction> loadTransactions(ReportTransactionQuery query) throws Exception
    {
        DBIterator transactions = UmsatzUtil.getUmsaetzeBackwards();
        if (query.from() != null)
            transactions.addFilter("datum >= ?", Date.valueOf(query.from()));
        if (query.to() != null)
            transactions.addFilter("datum <= ?", Date.valueOf(query.to()));
        if (query.accountId() != null && !query.accountId().isBlank())
            transactions.addFilter("konto_id = ?", query.accountId());

        List<ReportTransaction> result = new ArrayList<>();
        int limit = query.limit() == null ? Integer.MAX_VALUE : query.limit();
        while (transactions.hasNext() && result.size() < limit)
        {
            result.add(toReportTransaction((Umsatz) transactions.next()));
        }
        return List.copyOf(result);
    }

    private static ReportTransaction toReportTransaction(Umsatz transaction) throws RemoteException
    {
        UmsatzTyp category = transaction.getUmsatzTyp();
        Konto account = transaction.getKonto();
        List<CategoryInfo> categoryPath = categoryPath(category);
        String categoryName = categoryPath.isEmpty() ? "" : categoryPath.get(categoryPath.size() - 1).name();

        return new ReportTransaction(toLocalDate(transaction.getDatum()), toLocalDate(transaction.getValuta()),
            transaction.getBetrag(),
            transaction.getSaldo(), transaction.getZweck(), transaction.getZweck2(),
            list(transaction.getWeitereVerwendungszwecke()), transaction.getGegenkontoName(),
            transaction.getGegenkontoNummer(), transaction.getGegenkontoBLZ(), transaction.getArt(),
            categoryName, categoryPath, transaction.hasFlag(Umsatz.FLAG_NOTBOOKED), toReportAccount(account));
    }

    private static ReportAccount toReportAccount(Konto account) throws RemoteException
    {
        if (account == null)
            return null;
        String name = account.getBezeichnung();
        if (name == null || name.isBlank())
            name = account.getLongName();
        return new ReportAccount(account.getID(), account.getSaldo(), account.getSaldoAvailable(),
            toLocalDateTime(account.getSaldoDatum()), name, account.getBLZ(), account.getIban(),
            account.getKategorie(), account.hasFlag(Konto.FLAG_OFFLINE), null);
    }

    private static List<String> list(String[] values)
    {
        if (values == null || values.length == 0)
            return List.of();
        return Arrays.stream(values).filter(value -> value != null && !value.isBlank()).toList();
    }

    private static LocalDate toLocalDate(java.util.Date date)
    {
        return date == null ? null : HibiscusDataProvider.toLocalDate(date);
    }

    private static LocalDateTime toLocalDateTime(java.util.Date date)
    {
        return date == null ? null : HibiscusDataProvider.toLocalDateTime(date);
    }

    private static List<CategoryInfo> categoryPath(UmsatzTyp category) throws RemoteException
    {
        if (category == null)
            return List.of();

        List<CategoryInfo> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        GenericObjectNode current = category;
        while (current instanceof UmsatzTyp type && visited.add(type.getID()))
        {
            result.add(toInfo(type));
            current = type.getParent();
        }
        Collections.reverse(result);
        return result;
    }

    private static CategoryInfo toInfo(UmsatzTyp category) throws RemoteException
    {
        Integer color = null;
        if (category.isCustomColor())
        {
            int[] rgb = category.getColor();
            if (rgb != null && rgb.length >= 3)
                color = ((rgb[0] & 0xff) << 16) | ((rgb[1] & 0xff) << 8) | (rgb[2] & 0xff);
        }
        return new CategoryInfo(category.getID(), category.getName(),
            category.hasFlag(UmsatzTyp.FLAG_SKIP_REPORTS), color);
    }
}
