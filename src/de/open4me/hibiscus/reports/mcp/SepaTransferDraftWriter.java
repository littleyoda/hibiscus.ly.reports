package de.open4me.hibiscus.reports.mcp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

interface SepaTransferDraftWriter
{
    Result create(Request request) throws Exception;

    record Request(String accountId, String recipientName, String recipientIban, String recipientBic,
                   BigDecimal amount, String purpose, String purpose2, List<String> additionalPurposes,
                   LocalDate executionDate, String endToEndId, String pmtInfId, String purposeCode, String type)
    {
    }

    record Result(String id, String accountId, String accountName, String recipientName, String recipientIban,
                  String recipientBic, BigDecimal amount, LocalDate executionDate, String type, boolean created)
    {
    }
}
