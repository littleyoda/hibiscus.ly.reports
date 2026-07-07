package de.open4me.hibiscus.reports.ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;

import de.open4me.hibiscus.reports.model.AggregationInterval;
import de.open4me.hibiscus.reports.model.PeriodBalance;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.Settings;
import de.willuhn.util.ApplicationException;

final class OverviewExportService
{
    private static final Settings SETTINGS = new Settings(OverviewExportService.class);

    private OverviewExportService()
    {
    }

    static void export(List<PeriodBalance> periods, LocalDate from, LocalDate to,
                       AggregationInterval interval) throws ApplicationException
    {
        if (periods == null || periods.isEmpty() || from == null || to == null)
            throw new ApplicationException("Es ist keine Monatsübersicht zum Exportieren vorhanden.");
        FileDialog dialog = new FileDialog(GUI.getShell(), SWT.SAVE);
        dialog.setText("Monatsübersicht exportieren");
        dialog.setFilterNames(new String[] { "PNG-Grafik (*.png)", "SVG-Grafik (*.svg)" });
        dialog.setFilterExtensions(new String[] { "*.png", "*.svg" });
        dialog.setFilterIndex(SETTINGS.getInt("format", 0));
        dialog.setOverwrite(false);
        dialog.setFilterPath(SETTINGS.getString("lastdir", System.getProperty("user.home")));
        dialog.setFileName(defaultName(from, to, interval)
            + (dialog.getFilterIndex() == 1 ? ".svg" : ".png"));
        String selected = dialog.open();
        if (selected == null || selected.isBlank())
            return;
        int format = dialog.getFilterIndex() == 1 ? 1 : 0;
        selected = ExportFileNames.withExtension(selected, format == 1 ? ".svg" : ".png");
        File file = new File(selected);
        try
        {
            if (file.exists() && !Application.getCallback().askUser(
                "Datei \"" + file.getName() + "\" existiert bereits. Überschreiben?"))
                return;
        }
        catch (Exception e)
        {
            throw new ApplicationException("Überschreiben konnte nicht bestätigt werden", e);
        }
        SETTINGS.setAttribute("lastdir", file.getParent());
        SETTINGS.setAttribute("format", format);
        try
        {
            if (format == 1)
                Files.writeString(Path.of(selected), OverviewSvgExporter.create(periods, from, to, interval),
                    StandardCharsets.UTF_8);
            else
                OverviewPngExporter.save(GUI.getDisplay(), periods, from, to, interval, selected);
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                "Monatsübersicht gespeichert: " + selected, StatusBarMessage.TYPE_SUCCESS));
        }
        catch (Exception e)
        {
            throw new ApplicationException("Monatsübersicht konnte nicht gespeichert werden: "
                + e.getMessage(), e);
        }
    }

    private static String defaultName(LocalDate from, LocalDate to, AggregationInterval interval)
    {
        return "monatsuebersicht-" + from + "-bis-" + to + "-"
            + interval.name().toLowerCase(Locale.ROOT);
    }
}
