package de.open4me.hibiscus.reports.mcp;

import java.rmi.RemoteException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import de.willuhn.datasource.rmi.ObjectNotFoundException;
import de.willuhn.jameica.gui.AbstractView;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.hbci.Settings;
import de.willuhn.jameica.hbci.messaging.ObjectDeletedMessage;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.hbci.rmi.UmsatzTyp;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;

/** Deletes transactions in Hibiscus after an explicit confirmation dialog. Local only; the bank is not told. */
public final class HibiscusTransactionDeleter implements TransactionDeleter
{
    /** Stays below the 60 s request timeout common MCP clients use, so a late click cannot delete unseen. */
    static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(50);

    /** Below this, no dialog is opened: the user could not read it before the client gives up. */
    static final Duration MIN_CONFIRM_TIME = Duration.ofSeconds(10);

    private static final Set<String> TRANSACTION_LIST_VIEWS = Set.of(
        "de.willuhn.jameica.hbci.gui.views.KontoauszugList",
        "de.willuhn.jameica.hbci.gui.views.UmsatzTypTree");

    public HibiscusTransactionDeleter()
    {
    }

    /** Hibiscus IDs are positive ints; leading zeros or out-of-range values are not IDs (status unknown). */
    static boolean isTransactionId(String id)
    {
        if (id == null || !id.matches("[1-9]\\d{0,9}"))
            return false;
        return Long.parseLong(id) <= Integer.MAX_VALUE;
    }

    @Override
    public Optional<Candidate> find(String id) throws Exception
    {
        if (!isTransactionId(id))
            return Optional.empty();
        Umsatz transaction;
        try
        {
            transaction = Settings.getDBService().createObject(Umsatz.class, id);
        }
        catch (ObjectNotFoundException e)
        {
            return Optional.empty();
        }
        Konto account = transaction.getKonto();
        return Optional.of(new Candidate(id, account.getID(), accountName(account),
            toLocalDate(transaction.getDatum()), transaction.getBetrag(), text(account.getWaehrung()),
            text(transaction.getGegenkontoName()), text(transaction.getZweck()), links(transaction)));
    }

    @Override
    public boolean confirm(List<Candidate> candidates, int skippedCount, Instant requestStart) throws Exception
    {
        return McpTransactionDeleteDialog.confirm(TransactionDeleteConfirmation.text(candidates, skippedCount),
            () -> remainingConfirmTime(requestStart, Instant.now()));
    }

    /**
     * Time left for the dialog. CONFIRM_TIMEOUT counts from the start of the MCP request, so the lookup and a busy
     * UI thread are included and the dialog never outlives the client's request timeout.
     */
    static Duration remainingConfirmTime(Instant requestStart, Instant now)
    {
        return CONFIRM_TIMEOUT.minus(Duration.between(requestStart, now));
    }

    @Override
    public void delete(String id) throws Exception
    {
        Umsatz transaction = Settings.getDBService().createObject(Umsatz.class, id);
        // Hibiscus' delete() formats the booking date for the account log and fails on null with an exception it
        // does not roll back, leaving the database transaction open. Refuse before touching anything.
        if (transaction.getDatum() == null)
            throw new IllegalStateException("Hibiscus kann Umsätze ohne Buchungsdatum nicht löschen.");
        String transactionId = transaction.getID();
        transaction.delete();
        // Same notification Hibiscus' own delete sends (for consumers such as counters). Open tables do not react
        // to it: they only drop rows whose own object instance was deleted, see refreshOpenViews(). The
        // transaction is already gone at this point; a messaging problem must not turn the result into an error.
        try
        {
            Application.getMessagingFactory().sendMessage(new ObjectDeletedMessage(transaction, transactionId));
        }
        catch (Exception e)
        {
            Logger.warn("MCP: Löschmeldung für Umsatz " + transactionId + " konnte nicht gesendet werden: " + e);
        }
    }

    /**
     * Reloads the current view if it is a read-only transaction list. Views with input fields (account details,
     * transaction edit) are left alone because a reload would discard unsaved changes.
     */
    @Override
    public void refreshOpenViews()
    {
        if (Application.inServerMode())
            return;
        GUI.getDisplay().asyncExec(() -> {
            AbstractView view = GUI.getCurrentView();
            if (view == null || !isTransactionListView(view.getClass().getName()))
                return;
            try
            {
                view.reload();
            }
            catch (Exception e)
            {
                Logger.warn("MCP: Umsatzliste konnte nach dem Löschen nicht neu geladen werden: " + e);
            }
        });
    }

    /** Hibiscus' "Umsätze" and "Umsätze nach Kategorien" views: lists without input fields. */
    static boolean isTransactionListView(String viewClassName)
    {
        return viewClassName != null && TRANSACTION_LIST_VIEWS.contains(viewClassName);
    }

    private static List<String> links(Umsatz transaction) throws RemoteException
    {
        List<String> links = new ArrayList<>();
        // getUmsatzTyp() also returns rule-matched categories; only a stored assignment is a real link.
        if (transaction.isAssigned())
        {
            UmsatzTyp category = transaction.getUmsatzTyp();
            if (category != null)
                links.add("kategorie: " + category.getName());
        }
        String comment = transaction.getKommentar();
        if (comment != null && !comment.isBlank())
            links.add("kommentar");
        if (transaction.hasFlag(Umsatz.FLAG_CHECKED))
            links.add("geprueft");
        return links;
    }

    private static String accountName(Konto account) throws RemoteException
    {
        String name = account.getBezeichnung();
        if (name == null || name.isBlank())
            name = account.getLongName();
        if (name == null || name.isBlank())
            name = account.getName();
        return text(name);
    }

    private static LocalDate toLocalDate(java.util.Date date)
    {
        if (date == null)
            return null;
        if (date instanceof java.sql.Date sqlDate)
            return sqlDate.toLocalDate();
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static String text(String value)
    {
        return value == null ? "" : value;
    }
}
