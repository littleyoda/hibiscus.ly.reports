package de.open4me.hibiscus.reports.data;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.List;

import de.open4me.hibiscus.reports.model.ReportTransaction;

public final class ReportTransactionsProxy implements Iterable<ReportTransaction>
{
    private static final int DEFAULT_DAYS = 90;

    private final ReportTransactionProvider provider;
    private final ReportTransactionQuery query;
    private List<ReportTransaction> transactions;

    public ReportTransactionsProxy(ReportTransactionProvider provider)
    {
        this(provider, defaultQuery(null));
    }

    public ReportTransactionsProxy(ReportTransactionProvider provider, ReportTransactionQuery query)
    {
        this.provider = provider;
        this.query = query;
    }

    public static ReportTransactionsProxy forAccount(ReportTransactionProvider provider, String accountId)
    {
        return new ReportTransactionsProxy(provider, defaultQuery(accountId));
    }

    @Override
    public Iterator<ReportTransaction> iterator()
    {
        return transactions().iterator();
    }

    public ReportTransactionsProxy getAlle()
    {
        return new ReportTransactionsProxy(provider, new ReportTransactionQuery(query.accountId(), null, null,
            query.limit()));
    }

    public ReportTransactionsProxy letzteTage(int days)
    {
        int safeDays = Math.max(0, days);
        LocalDate to = LocalDate.now();
        return new ReportTransactionsProxy(provider, query.withFrom(to.minusDays(safeDays)).withTo(to));
    }

    public ReportTransactionsProxy zeitraum(String from, String to)
    {
        try
        {
            return new ReportTransactionsProxy(provider,
                query.withFrom(LocalDate.parse(from)).withTo(LocalDate.parse(to)));
        }
        catch (DateTimeParseException e)
        {
            throw new IllegalArgumentException("Zeitraum muss im Format YYYY-MM-DD angegeben werden", e);
        }
    }

    public ReportTransactionsProxy limit(int limit)
    {
        return new ReportTransactionsProxy(provider, query.withLimit(limit));
    }

    public int size()
    {
        return transactions().size();
    }

    public boolean isEmpty()
    {
        return transactions().isEmpty();
    }

    public List<ReportTransaction> asList()
    {
        return transactions();
    }

    private List<ReportTransaction> transactions()
    {
        if (transactions == null)
        {
            try
            {
                transactions = List.copyOf(provider.loadTransactions(query));
            }
            catch (Exception e)
            {
                throw new IllegalStateException("Umsätze konnten nicht geladen werden", e);
            }
        }
        return transactions;
    }

    private static ReportTransactionQuery defaultQuery(String accountId)
    {
        LocalDate today = LocalDate.now();
        return new ReportTransactionQuery(accountId, today.minusDays(DEFAULT_DAYS), today, null);
    }
}
