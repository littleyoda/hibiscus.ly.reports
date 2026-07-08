package de.open4me.hibiscus.reports.ui;

import java.rmi.RemoteException;

import de.willuhn.jameica.gui.Item;
import de.willuhn.jameica.gui.extension.Extendable;
import de.willuhn.jameica.gui.extension.Extension;

public class ReportingNavigationExtension implements Extension
{
    @Override
    public void extend(Extendable extendable)
    {
        if (!(extendable instanceof Item parent))
            return;
        try
        {
            parent.addChild(new ReportNavigationItem(parent, "Geldfluss",
                "hibiscus.navi.reporting.geldfluss", "office-chart-area.png", new OpenFlowViewAction()));
            parent.addChild(new ReportNavigationItem(parent, "Monatsübersicht",
                "hibiscus.navi.reporting.monatsuebersicht", "office-chart-area.png", new OpenMonthlyOverviewAction()));
            parent.addChild(new ReportNavigationItem(parent, "Saldo nach Gruppen",
                "hibiscus.navi.reporting.saldonachgruppen", "office-chart-area.png", new OpenGroupBalanceViewAction()));
        }
        catch (RemoteException e)
        {
            throw new IllegalStateException("Navigation konnte nicht erweitert werden", e);
        }
    }
}
