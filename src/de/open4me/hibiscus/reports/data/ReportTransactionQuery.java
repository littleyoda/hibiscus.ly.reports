package de.open4me.hibiscus.reports.data;

import java.time.LocalDate;
import java.util.List;

public record ReportTransactionQuery(String accountId, LocalDate from, LocalDate to, Integer limit,
                                     List<String> categoryIds, boolean includeSubcategories)
{
    public ReportTransactionQuery
    {
        if (limit != null && limit < 0)
            limit = 0;
        categoryIds = categoryIds == null ? List.of() : categoryIds.stream()
            .filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
    }

    public ReportTransactionQuery(String accountId, LocalDate from, LocalDate to, Integer limit)
    {
        this(accountId, from, to, limit, List.of(), false);
    }

    public ReportTransactionQuery withAccountId(String accountId)
    {
        return new ReportTransactionQuery(accountId, from, to, limit, categoryIds, includeSubcategories);
    }

    public ReportTransactionQuery withFrom(LocalDate from)
    {
        return new ReportTransactionQuery(accountId, from, to, limit, categoryIds, includeSubcategories);
    }

    public ReportTransactionQuery withTo(LocalDate to)
    {
        return new ReportTransactionQuery(accountId, from, to, limit, categoryIds, includeSubcategories);
    }

    public ReportTransactionQuery withLimit(Integer limit)
    {
        return new ReportTransactionQuery(accountId, from, to, limit, categoryIds, includeSubcategories);
    }

    public ReportTransactionQuery withCategoryFilter(List<String> categoryIds, boolean includeSubcategories)
    {
        return new ReportTransactionQuery(accountId, from, to, limit, categoryIds, includeSubcategories);
    }
}
