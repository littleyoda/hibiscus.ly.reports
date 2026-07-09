package de.open4me.hibiscus.reports.data;

import java.rmi.RemoteException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.open4me.hibiscus.reports.model.AccountInfo;
import de.open4me.hibiscus.reports.model.BookingRecord;
import de.open4me.hibiscus.reports.model.CategoryInfo;
import de.open4me.hibiscus.reports.model.DatedBookingRecord;
import de.open4me.hibiscus.reports.model.ExcludedCategoryInfo;
import de.open4me.hibiscus.reports.model.FlowReport;
import de.willuhn.datasource.GenericObjectNode;
import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.hbci.gui.filter.KontoFilter;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.hbci.rmi.UmsatzTyp;
import de.willuhn.jameica.hbci.server.KontoUtil;
import de.willuhn.jameica.hbci.server.UmsatzTypUtil;
import de.willuhn.jameica.hbci.server.UmsatzUtil;

public class HibiscusDataProvider
{
    private final FlowAggregator aggregator = new FlowAggregator();

    public List<AccountInfo> loadAccounts() throws RemoteException
    {
        List<AccountInfo> result = new ArrayList<>();
        for (Konto account : KontoUtil.getKonten(KontoFilter.ALL))
        {
            String name = account.getBezeichnung();
            if (name == null || name.isBlank())
                name = account.getLongName();
            result.add(new AccountInfo(account.getID(), account.getKategorie(), name, account.getWaehrung(),
                account.hasFlag(Konto.FLAG_DISABLED)));
        }
        return result;
    }

    public FlowReport loadReport(Set<String> accountIds, LocalDate from, LocalDate to) throws RemoteException
    {
        if (accountIds == null || accountIds.isEmpty())
            return aggregator.aggregate(List.of(), from, to);

        DBIterator transactions = UmsatzUtil.getUmsaetze();
        transactions.addFilter("datum >= ?", Date.valueOf(from));
        transactions.addFilter("datum <= ?", Date.valueOf(to));

        addAccountFilter(transactions, accountIds);

        List<BookingRecord> bookings = new ArrayList<>();
        while (transactions.hasNext())
        {
            Umsatz transaction = (Umsatz) transactions.next();
            boolean pending = transaction.hasFlag(Umsatz.FLAG_NOTBOOKED);
            UmsatzTyp category = transaction.getUmsatzTyp();
            bookings.add(new BookingRecord(transaction.getBetrag(), categoryPath(category), pending));
        }
        return aggregator.aggregate(bookings, from, to);
    }

    public List<DatedBookingRecord> loadDatedBookings(Set<String> accountIds, LocalDate from,
                                                       LocalDate to) throws RemoteException
    {
        if (accountIds == null || accountIds.isEmpty())
            return List.of();

        DBIterator transactions = UmsatzUtil.getUmsaetze();
        transactions.addFilter("datum >= ?", Date.valueOf(from));
        transactions.addFilter("datum <= ?", Date.valueOf(to));
        addAccountFilter(transactions, accountIds);

        List<DatedBookingRecord> bookings = new ArrayList<>();
        while (transactions.hasNext())
        {
            Umsatz transaction = (Umsatz) transactions.next();
            if (transaction.getDatum() == null)
                continue;
            LocalDate date = toLocalDate(transaction.getDatum());
            boolean pending = transaction.hasFlag(Umsatz.FLAG_NOTBOOKED);
            bookings.add(new DatedBookingRecord(date, new BookingRecord(transaction.getBetrag(),
                categoryPath(transaction.getUmsatzTyp()), pending)));
        }
        return List.copyOf(bookings);
    }

    public LocalDate findOldestDate(Set<String> accountIds) throws RemoteException
    {
        if (accountIds == null || accountIds.isEmpty())
            return null;

        DBIterator transactions = UmsatzUtil.getUmsaetze();
        addAccountFilter(transactions, accountIds);
        while (transactions.hasNext())
        {
            Umsatz transaction = (Umsatz) transactions.next();
            if (transaction.getDatum() != null)
                return toLocalDate(transaction.getDatum());
        }
        return null;
    }

    public List<ExcludedCategoryInfo> loadExcludedCategories() throws RemoteException
    {
        List<ExcludedCategoryInfo> result = new ArrayList<>();
        DBIterator<UmsatzTyp> categories = UmsatzTypUtil.getAll();
        while (categories.hasNext())
        {
            List<CategoryInfo> path = categoryPath(categories.next());
            if (path.isEmpty())
                continue;

            String reason = null;
            for (CategoryInfo category : path)
            {
                if (category.skipReports())
                {
                    reason = category == path.get(path.size() - 1)
                        ? "In Auswertungen ignorieren"
                        : "Geerbt von " + category.name();
                    break;
                }
            }
            if (reason != null)
            {
                String displayPath = path.stream().map(CategoryInfo::name)
                    .reduce((left, right) -> left + " > " + right).orElse("");
                result.add(new ExcludedCategoryInfo(displayPath, reason));
            }
        }
        result.sort(java.util.Comparator.comparing(ExcludedCategoryInfo::path,
            String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static void addAccountFilter(DBIterator transactions, Set<String> accountIds) throws RemoteException
    {
        String placeholders = String.join(",", Collections.nCopies(accountIds.size(), "?"));
        transactions.addFilter("konto_id in (" + placeholders + ")", accountIds.toArray());
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

    static LocalDate toLocalDate(java.util.Date date)
    {
        if (date instanceof java.sql.Date sqlDate)
            return sqlDate.toLocalDate();
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    static LocalDateTime toLocalDateTime(java.util.Date date)
    {
        if (date instanceof java.sql.Date sqlDate)
            return sqlDate.toLocalDate().atStartOfDay();
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
