package de.open4me.hibiscus.reports.ui;

import java.nio.file.Files;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import de.open4me.hibiscus.reports.data.DynamicReportRenderer;
import de.open4me.hibiscus.reports.data.DynamicReportRepository;
import de.open4me.hibiscus.reports.data.HibiscusReportAccountProvider;
import de.open4me.hibiscus.reports.data.HibiscusReportTransactionProvider;
import de.open4me.hibiscus.reports.data.ReportTransactionProvider;
import de.open4me.hibiscus.reports.model.DynamicReport;
import de.willuhn.jameica.gui.boxes.AbstractBox;
import de.willuhn.jameica.system.Settings;
import de.willuhn.logging.Logger;

abstract class AbstractReportStartBox extends AbstractBox
{
    private static final int SLOT_COUNT = 32;
    private static final Settings SETTINGS = new Settings(AbstractReportStartBox.class);
    private static final String SLOT_PREFIX = "slot.";
    private static final String EMPTY_NAME = "Reports: freier Report-Platz";

    abstract int slot();

    @Override
    public String getName()
    {
        DynamicReport report = assignedReport();
        return report == null ? EMPTY_NAME : "Reports: " + report.displayName();
    }

    @Override
    public boolean getDefaultEnabled()
    {
        return false;
    }

    @Override
    public int getDefaultIndex()
    {
        return 1000 + slot();
    }

    @Override
    public boolean isActive()
    {
        return assignedReport() != null;
    }

    @Override
    public int getHeight()
    {
        return 360;
    }

    @Override
    public void paint(Composite parent)
    {
        DynamicReport report = assignedReport();
        if (report == null)
        {
            new Label(parent, SWT.WRAP).setText("Der Report ist nicht mehr vorhanden.");
            return;
        }

        try
        {
            DynamicReportRepository repository = DynamicReportRepository.jameica();
            ReportTransactionProvider transactionProvider = new HibiscusReportTransactionProvider();
            DynamicReportRenderer renderer = new DynamicReportRenderer(repository.baseDirectory(),
                new HibiscusReportAccountProvider(transactionProvider), transactionProvider);
            DynamicReportRenderer.RenderedReport rendered = renderer.render(repository.read(report));

            Composite root = new Composite(parent, SWT.NONE);
            root.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            root.setLayout(new GridLayout(1, false));
            paintHtml(root, rendered.html());
            if (!rendered.errors().isEmpty())
                paintErrors(root, rendered.errors());
        }
        catch (Exception e)
        {
            Logger.error("unable to render report start box " + report.displayName(), e);
            Text text = new Text(parent, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.READ_ONLY);
            text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            text.setText("Report konnte nicht gerendert werden: " + message(e));
        }
    }

    private static void paintHtml(Composite parent, String html)
    {
        try
        {
            Browser browser = new Browser(parent, SWT.BORDER);
            browser.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            browser.setText(html == null ? "" : html);
        }
        catch (SWTError e)
        {
            Logger.warn("unable to create SWT browser for report start box: " + e.getMessage());
            Text fallback = new Text(parent, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.READ_ONLY);
            fallback.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            fallback.setText(html == null ? "" : html);
        }
    }

    private static void paintErrors(Composite parent, List<String> errors)
    {
        Text text = new Text(parent, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.READ_ONLY);
        GridData data = new GridData(SWT.FILL, SWT.BOTTOM, true, false);
        data.heightHint = 70;
        text.setLayoutData(data);
        text.setText(String.join(System.lineSeparator(), errors));
    }

    private DynamicReport assignedReport()
    {
        try
        {
            return assignments().get(slot());
        }
        catch (Exception e)
        {
            Logger.error("unable to resolve report start box slot " + slot(), e);
            return null;
        }
    }

    private static synchronized Map<Integer, DynamicReport> assignments() throws Exception
    {
        DynamicReportRepository repository = DynamicReportRepository.jameica();
        repository.initialize();
        List<DynamicReport> reports = repository.listReports().stream()
            .filter(report -> Files.isRegularFile(report.path()))
            .sorted(Comparator.comparing(DynamicReport::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();

        Map<String, DynamicReport> byId = new HashMap<>();
        for (DynamicReport report : reports)
            byId.put(ReportsNavigationExtension.id(report), report);

        Map<Integer, String> assignedIds = new HashMap<>();
        Set<String> usedIds = new HashSet<>();
        for (int slot = 1; slot <= SLOT_COUNT; slot++)
        {
            String id = SETTINGS.getString(key(slot), null);
            if (id == null || !byId.containsKey(id) || usedIds.contains(id))
            {
                SETTINGS.setAttribute(key(slot), (String) null);
                continue;
            }
            assignedIds.put(slot, id);
            usedIds.add(id);
        }

        for (DynamicReport report : reports)
        {
            String id = ReportsNavigationExtension.id(report);
            if (usedIds.contains(id))
                continue;
            int freeSlot = firstFreeSlot(assignedIds);
            if (freeSlot < 0)
                break;
            assignedIds.put(freeSlot, id);
            usedIds.add(id);
            SETTINGS.setAttribute(key(freeSlot), id);
        }

        Map<Integer, DynamicReport> result = new HashMap<>();
        for (Map.Entry<Integer, String> entry : assignedIds.entrySet())
            result.put(entry.getKey(), byId.get(entry.getValue()));
        return result;
    }

    private static int firstFreeSlot(Map<Integer, String> assignedIds)
    {
        for (int slot = 1; slot <= SLOT_COUNT; slot++)
        {
            if (!assignedIds.containsKey(slot))
                return slot;
        }
        return -1;
    }

    private static String key(int slot)
    {
        return SLOT_PREFIX + slot + ".report";
    }

    private static String message(Throwable error)
    {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getName() : message;
    }
}
