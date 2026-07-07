package de.open4me.hibiscus.reports.ui;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;

import de.open4me.hibiscus.reports.model.SankeyGraph;
import de.willuhn.datasource.pseudo.PseudoIterator;
import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.gui.AbstractView;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.internal.parts.PanelButtonPrint;
import de.willuhn.jameica.hbci.gui.action.UmsatzDetail;
import de.willuhn.jameica.hbci.gui.parts.UmsatzList;
import de.willuhn.jameica.hbci.io.print.PrintSupportUmsatzList;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.hbci.rmi.UmsatzTyp;
import de.willuhn.jameica.hbci.server.UmsatzUtil;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;

public class FlowTransactionsView extends AbstractView
{
    @Override
    public void bind() throws Exception
    {
        Object context = getCurrentObject();
        if (!(context instanceof FlowTransactionSelection selection))
        {
            GUI.getView().setTitle("Umsätze");
            return;
        }

        GUI.getView().setTitle(selection.title());
        List<Umsatz> transactions = loadTransactions(selection);
        if (transactions.isEmpty())
        {
            new Label(getParent(), SWT.NONE).setText("Keine passenden Umsätze gefunden.");
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                "Keine passenden Umsätze gefunden.", StatusBarMessage.TYPE_INFO));
            return;
        }

        UmsatzList list = new UmsatzList(PseudoIterator.fromArray(transactions.toArray(new Umsatz[0])),
            new UmsatzDetail());
        list.setFilterVisible(false);
        PanelButtonPrint print = new PanelButtonPrint(new PrintSupportUmsatzList(list));
        list.addSelectionListener(new Listener()
        {
            @Override
            public void handleEvent(Event event)
            {
                print.setEnabled(list.getSelection() != null);
            }
        });
        GUI.getView().addPanelButton(print);
        list.paint(getParent());
        print.setEnabled(list.getSelection() != null);
    }

    private static List<Umsatz> loadTransactions(FlowTransactionSelection selection) throws Exception
    {
        DBIterator transactions = UmsatzUtil.getUmsaetzeBackwards();
        LocalDate from = selection.from();
        LocalDate to = selection.to();
        if (from != null)
            transactions.addFilter("datum >= ?", Date.valueOf(from));
        if (to != null)
            transactions.addFilter("datum <= ?", Date.valueOf(to));
        addAccountFilter(transactions, selection.accountIds());

        List<Umsatz> result = new ArrayList<>();
        while (transactions.hasNext())
        {
            Umsatz transaction = (Umsatz) transactions.next();
            if (transaction.hasFlag(Umsatz.FLAG_NOTBOOKED))
                continue;
            if (!selection.sign().matches(transaction.getBetrag()))
                continue;
            if (selection.excludeSkippedCategories() && hasSkippedReportCategory(transaction.getUmsatzTyp()))
                continue;
            if (!matches(selection.filter(), transaction))
                continue;
            result.add(transaction);
        }
        return List.copyOf(result);
    }

    private static void addAccountFilter(DBIterator transactions, Set<String> accountIds) throws Exception
    {
        if (accountIds == null || accountIds.isEmpty())
            return;
        String placeholders = String.join(",", Collections.nCopies(accountIds.size(), "?"));
        transactions.addFilter("konto_id in (" + placeholders + ")", accountIds.toArray());
    }

    private static boolean matches(SankeyGraph.TransactionFilter filter, Umsatz transaction) throws Exception
    {
        if (filter == null)
            return true;
        if (!filter.canOpen())
            return false;

        if (filter.unassigned())
        {
            if (transaction.getUmsatzTyp() != null)
                return false;
            return filter.sign() == 0 || Math.signum(transaction.getBetrag()) == filter.sign();
        }

        UmsatzTyp category = transaction.getUmsatzTyp();
        if (category == null)
            return false;

        if (!filter.includeChildren())
            return filter.categoryId().equals(category.getID());

        return matchesCategoryOrParent(category, filter.categoryId());
    }

    private static boolean hasSkippedReportCategory(UmsatzTyp category) throws Exception
    {
        UmsatzTyp current = category;
        for (int i = 0; i < 100 && current != null; i++)
        {
            if (current.hasFlag(UmsatzTyp.FLAG_SKIP_REPORTS))
                return true;
            Object parent = current.getParent();
            current = parent instanceof UmsatzTyp type ? type : null;
        }
        if (category != null)
            Logger.warn("unable to resolve category parent chain for " + category.getID());
        return false;
    }

    private static boolean matchesCategoryOrParent(UmsatzTyp category, String id) throws Exception
    {
        UmsatzTyp current = category;
        for (int i = 0; i < 100 && current != null; i++)
        {
            if (id.equals(current.getID()))
                return true;
            Object parent = current.getParent();
            current = parent instanceof UmsatzTyp type ? type : null;
        }
        Logger.warn("unable to resolve category parent chain for " + category.getID());
        return false;
    }
}
