package de.open4me.hibiscus.reports.ui;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Locale;

import de.open4me.hibiscus.reports.data.DynamicReportRepository;
import de.open4me.hibiscus.reports.model.DynamicReport;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.Item;
import de.willuhn.jameica.gui.extension.Extendable;
import de.willuhn.jameica.gui.extension.Extension;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;

public class ReportsNavigationExtension implements Extension
{
    @Override
    public void extend(Extendable extendable)
    {
        if (!(extendable instanceof Item parent))
            return;

        ReportNavigationItem reports = new ReportNavigationItem(parent, "Reports",
            "hibiscus.navi.reports", "folder.png", "folder-open.png",
            new OpenDynamicReportsViewAction(), true);
        try
        {
            addReports(reports);
            parent.addChild(reports);
        }
        catch (RemoteException e)
        {
            throw new IllegalStateException("Reports-Navigation konnte nicht erweitert werden", e);
        }
    }

    private static void addReports(ReportNavigationItem parent)
    {
        try
        {
            DynamicReportRepository repository = DynamicReportRepository.jameica();
            repository.initialize();
            for (DynamicReport report : repository.listReports())
            {
                parent.addChild(new ReportNavigationItem(parent, report.displayName(),
                    "hibiscus.navi.reports.report." + id(report),
                    "office-chart-area.png", new OpenReportAction(report)));
            }
        }
        catch (IOException e)
        {
            Logger.error("unable to load dynamic reports for navigation", e);
        }
    }

    private static String id(DynamicReport report)
    {
        String name = report == null ? null : report.displayName();
        String normalized = name == null ? "report" : name.toLowerCase(Locale.ROOT)
            .replace('\\', '-').replace('/', '-')
            .replaceAll("[^a-z0-9._-]+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
        if (normalized.isBlank())
            normalized = "report";
        String source = report == null || report.path() == null ? normalized : report.path().toString();
        return normalized + "-" + Integer.toHexString(source.hashCode());
    }

    private static final class OpenReportAction implements Action
    {
        private final DynamicReport report;

        private OpenReportAction(DynamicReport report)
        {
            this.report = report;
        }

        @Override
        public void handleAction(Object context) throws ApplicationException
        {
            GUI.startView(DynamicReportsView.class, report);
        }
    }
}
