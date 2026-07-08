package de.open4me.hibiscus.reports.data;

import java.util.List;

import de.open4me.hibiscus.reports.model.ReportTransaction;

public interface ReportTransactionProvider
{
    List<ReportTransaction> loadTransactions(ReportTransactionQuery query) throws Exception;
}
