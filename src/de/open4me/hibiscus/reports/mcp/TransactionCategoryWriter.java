package de.open4me.hibiscus.reports.mcp;

import java.util.List;

public interface TransactionCategoryWriter
{
    default Result assign(String transactionId, String categoryId) throws Exception
    {
        return assignAll(java.util.Collections.singletonList(transactionId), categoryId).get(0);
    }

    List<Result> assignAll(List<String> transactionIds, String categoryId) throws Exception;

    record Result(String transactionId, String previousCategoryId, String categoryId, boolean changed)
    {
    }
}
