package de.open4me.hibiscus.reports.mcp;

import java.util.Objects;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import de.willuhn.jameica.hbci.Settings;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.hbci.rmi.UmsatzTyp;

public final class HibiscusTransactionCategoryWriter implements TransactionCategoryWriter
{
    @FunctionalInterface
    interface Loader<T>
    {
        T load(String id) throws Exception;
    }

    private final Loader<Umsatz> transactions;
    private final Loader<UmsatzTyp> categories;

    public HibiscusTransactionCategoryWriter()
    {
        this(id -> Settings.getDBService().createObject(Umsatz.class, id),
            id -> Settings.getDBService().createObject(UmsatzTyp.class, id));
    }

    HibiscusTransactionCategoryWriter(Loader<Umsatz> transactions, Loader<UmsatzTyp> categories)
    {
        this.transactions = transactions;
        this.categories = categories;
    }

    @Override
    public List<Result> assignAll(List<String> transactionIds, String categoryId) throws Exception
    {
        if (transactionIds == null || transactionIds.isEmpty() || categoryId == null || categoryId.isBlank())
            throw new IllegalArgumentException("Umsatz-ID und Kategorie-ID sind erforderlich.");
        HashSet<String> uniqueIds = new HashSet<>();
        for (String id : transactionIds)
            if (id == null || id.isBlank() || !uniqueIds.add(id))
                throw new IllegalArgumentException("Umsatz-IDs muessen nicht leer und eindeutig sein.");
        UmsatzTyp category = categories.load(categoryId);
        if (category == null || !categoryId.equals(category.getID()))
            throw new IllegalArgumentException("Kategorie nicht gefunden: " + categoryId);

        List<Umsatz> loaded = new ArrayList<>();
        for (String id : transactionIds)
        {
            Umsatz transaction = transactions.load(id);
            if (transaction == null || !id.equals(transaction.getID()))
                throw new IllegalArgumentException("Umsatz nicht gefunden: " + id);
            loaded.add(transaction);
        }

        // All Umsatz objects use the same Hibiscus database transaction.
        Umsatz transactionOwner = loaded.get(0);
        transactionOwner.transactionBegin();
        try
        {
            List<Result> results = new ArrayList<>();
            for (Umsatz transaction : loaded)
            {
                UmsatzTyp previous = transaction.getUmsatzTyp();
                String previousId = previous == null ? null : previous.getID();
                boolean changed = !Objects.equals(previousId, categoryId);
                if (changed)
                {
                    transaction.setUmsatzTyp(category);
                    transaction.store();
                }
                results.add(new Result(transaction.getID(), previousId, categoryId, changed));
            }
            transactionOwner.transactionCommit();
            return List.copyOf(results);
        }
        catch (Exception e)
        {
            try
            {
                transactionOwner.transactionRollback();
            }
            catch (Exception rollbackError)
            {
                e.addSuppressed(rollbackError);
            }
            throw e;
        }
    }
}
