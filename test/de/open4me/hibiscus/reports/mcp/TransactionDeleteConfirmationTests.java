package de.open4me.hibiscus.reports.mcp;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class TransactionDeleteConfirmationTests
{
    private TransactionDeleteConfirmationTests()
    {
    }

    public static void run()
    {
        confirmationTextShowsTransaction();
        confirmationTextMentionsUnknownIds();
        confirmationTextListsEveryTransaction();
        confirmationTextShowsAccountCurrency();
        confirmationTextHandlesMissingDate();
    }

    private static void confirmationTextShowsAccountCurrency()
    {
        TransactionDeleter.Candidate chf = new TransactionDeleter.Candidate("4711", "7", "Beispielkonto",
            LocalDate.of(2025, 1, 14), -42.0, "CHF", "Beispiel GmbH", "Rechnung 4711", List.of());
        String text = TransactionDeleteConfirmation.text(List.of(chf), 0);
        check(text.contains("-42,00 CHF"), "account currency shown but was:\n" + text);
        check(!text.contains("EUR"), "no hard-coded EUR");

        TransactionDeleter.Candidate noCurrency = new TransactionDeleter.Candidate("4712", "7", "Beispielkonto",
            LocalDate.of(2025, 1, 14), -42.0, "", "Beispiel GmbH", "Rechnung 4712", List.of());
        check(TransactionDeleteConfirmation.text(List.of(noCurrency), 0).contains("-42,00 EUR"),
            "EUR fallback for blank currency");
    }

    private static void confirmationTextHandlesMissingDate()
    {
        TransactionDeleter.Candidate undated = new TransactionDeleter.Candidate("4711", "7", "Beispielkonto",
            null, -42.0, "EUR", "Beispiel GmbH", "Rechnung 4711", List.of());
        String text = TransactionDeleteConfirmation.text(List.of(undated), 0);
        check(text.contains("ohne Datum  -42,00 EUR"), "placeholder for missing date but was:\n" + text);
    }

    private static void confirmationTextShowsTransaction()
    {
        TransactionDeleter.Candidate candidate = new TransactionDeleter.Candidate("4711", "7", "Beispielkonto",
            LocalDate.of(2025, 1, 14), -42.0, "EUR", "Beispiel GmbH", "Rechnung 4711\nzweite Zeile",
            List.of("kategorie: Versicherung"));
        String text = TransactionDeleteConfirmation.text(List.of(candidate), 0);
        for (String expected : List.of("Ein MCP-Client möchte 1 Umsatz löschen:", "14.01.2025", "-42,00 EUR",
            "Beispiel GmbH", "Rechnung 4711", "Beispielkonto", "[kategorie: Versicherung]",
            "bei der Bank ändert sich nichts", "[kategorie: Versicherung]  #4711"))
            check(text.contains(expected), "text contains '" + expected + "' but was:\n" + text);
        check(!text.contains("zweite Zeile"), "only first purpose line shown");
    }

    private static void confirmationTextMentionsUnknownIds()
    {
        List<TransactionDeleter.Candidate> candidates = List.of(candidate(1));
        String text = TransactionDeleteConfirmation.text(candidates, 2);
        check(text.contains("2 ID(s) werden übersprungen (unbekannt oder nicht lesbar)."),
            "skipped ids mentioned: " + text);
        check(!TransactionDeleteConfirmation.text(candidates, 0).contains("übersprungen"),
            "no skipped line without skipped ids");
    }

    private static void confirmationTextListsEveryTransaction()
    {
        List<TransactionDeleter.Candidate> candidates = new ArrayList<>();
        for (int i = 1; i <= 60; i++)
            candidates.add(candidate(i));
        String text = TransactionDeleteConfirmation.text(candidates, 0);
        check(text.contains("60 Umsätze"), "plural header");
        check(text.contains("-1.234,50 EUR"), "thousands separator and decimal comma");
        for (int i = 1; i <= 60; i++)
            check(text.contains("Rechnung " + i + " "), "purpose " + i + " listed");
    }

    private static TransactionDeleter.Candidate candidate(int number)
    {
        return new TransactionDeleter.Candidate(Integer.toString(number), "7", "Beispielkonto",
            LocalDate.of(2025, 1, 14), -1234.5, "EUR", "Beispiel GmbH", "Rechnung " + number, List.of());
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
