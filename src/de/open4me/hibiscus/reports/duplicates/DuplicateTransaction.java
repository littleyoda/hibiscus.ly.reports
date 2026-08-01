package de.open4me.hibiscus.reports.duplicates;

import java.time.LocalDate;

public record DuplicateTransaction(String id, String accountId, String accountName, LocalDate date, double amount,
                                   double balance, String counterAccountNumber, String category, String purpose,
                                   String note)
{
    public DuplicateTransaction
    {
        id = text(id);
        accountId = text(accountId);
        accountName = text(accountName);
        counterAccountNumber = text(counterAccountNumber);
        category = text(category);
        purpose = text(purpose);
        note = text(note);
    }

    private static String text(String value)
    {
        return value == null ? "" : value;
    }
}
