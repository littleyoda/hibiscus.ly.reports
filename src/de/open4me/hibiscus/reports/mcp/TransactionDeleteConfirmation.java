package de.open4me.hibiscus.reports.mcp;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Builds the text of the confirmation dialog for MCP transaction deletion (pure text, no SWT). */
final class TransactionDeleteConfirmation
{
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private TransactionDeleteConfirmation()
    {
    }

    /** @param skippedCount IDs of the call that are not listed: unknown, or their lookup failed */
    static String text(List<TransactionDeleter.Candidate> candidates, int skippedCount)
    {
        DecimalFormat amount = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY));
        StringBuilder text = new StringBuilder();
        text.append("Ein MCP-Client möchte ").append(candidates.size())
            .append(candidates.size() == 1 ? " Umsatz" : " Umsätze").append(" löschen:\n\n");
        for (TransactionDeleter.Candidate candidate : candidates)
        {
            text.append(candidate.date() == null ? "ohne Datum" : DATE.format(candidate.date())).append("  ")
                .append(amount.format(candidate.amount())).append(' ').append(currency(candidate.currency()))
                .append("  ")
                .append(nullToEmpty(candidate.counterpartyName())).append("  –  ")
                .append(firstLine(candidate.purpose())).append("  (")
                .append(nullToEmpty(candidate.accountName())).append(')');
            if (!candidate.links().isEmpty())
                text.append("  [").append(String.join(", ", candidate.links())).append(']');
            text.append("  #").append(candidate.id());
            text.append('\n');
        }
        text.append('\n');
        if (skippedCount > 0)
            text.append(skippedCount).append(" ID(s) werden übersprungen (unbekannt oder nicht lesbar).\n\n");
        text.append("Die Umsätze werden nur lokal in Hibiscus gelöscht, bei der Bank ändert sich nichts. "
            + "Das Löschen kann nicht rückgängig gemacht werden.");
        return text.toString();
    }

    private static String firstLine(String value)
    {
        if (value == null)
            return "";
        int end = value.indexOf('\n');
        return (end < 0 ? value : value.substring(0, end)).stripTrailing();
    }

    private static String currency(String value)
    {
        return value == null || value.isBlank() ? "EUR" : value.trim();
    }

    private static String nullToEmpty(String value)
    {
        return value == null ? "" : value;
    }
}
