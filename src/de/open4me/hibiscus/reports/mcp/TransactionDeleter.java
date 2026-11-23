package de.open4me.hibiscus.reports.mcp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionDeleter
{
    /** Empty if the ID is malformed or no such transaction exists. Throws only on real lookup failures. */
    Optional<Candidate> find(String id) throws Exception;

    /**
     * Blocks until the user answers. true only on explicit confirmation.
     *
     * @param skippedCount IDs of this call that are not listed (unknown or lookup failed)
     * @param requestStart start of the MCP request; the confirmation timeout counts from here, so time spent
     *                     before the dialog opens is included
     */
    boolean confirm(List<Candidate> candidates, int skippedCount, Instant requestStart) throws Exception;

    void delete(String id) throws Exception;

    /** Called once after a call deleted at least one transaction, so open lists stop showing deleted rows. */
    void refreshOpenViews() throws Exception;

    /** {@code date} may be null (Hibiscus allows transactions without a booking date). */
    record Candidate(String id, String accountId, String accountName, LocalDate date, double amount,
                     String currency, String counterpartyName, String purpose, List<String> links)
    {
    }
}
