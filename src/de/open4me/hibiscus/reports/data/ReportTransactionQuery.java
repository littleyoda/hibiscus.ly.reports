package de.open4me.hibiscus.reports.data;

import java.time.LocalDate;

public record ReportTransactionQuery(String accountId, LocalDate from, LocalDate to, Integer limit)
{
    public ReportTransactionQuery
    {
        if (limit != null && limit < 0)
            limit = 0;
    }

    public ReportTransactionQuery withAccountId(String accountId)
    {
        return new ReportTransactionQuery(accountId, from, to, limit);
    }

    public ReportTransactionQuery withFrom(LocalDate from)
    {
        return new ReportTransactionQuery(accountId, from, to, limit);
    }

    public ReportTransactionQuery withTo(LocalDate to)
    {
        return new ReportTransactionQuery(accountId, from, to, limit);
    }

    public ReportTransactionQuery withLimit(Integer limit)
    {
        return new ReportTransactionQuery(accountId, from, to, limit);
    }
}
