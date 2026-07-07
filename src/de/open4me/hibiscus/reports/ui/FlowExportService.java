package de.open4me.hibiscus.reports.ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;

import de.open4me.hibiscus.reports.model.SankeyGraph;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.Settings;
import de.willuhn.util.ApplicationException;

final class FlowExportService
{
    private static final Settings SETTINGS = new Settings(FlowExportService.class);

    private FlowExportService()
    {
    }

    static void export(SankeyGraph graph, LocalDate from, LocalDate to) throws ApplicationException
    {
        if (graph == null || graph.nodes().isEmpty())
            throw new ApplicationException("Es ist keine Geldflussgrafik zum Exportieren vorhanden.");

        FileDialog dialog = new FileDialog(GUI.getShell(), SWT.SAVE);
        dialog.setText("Geldflussgrafik exportieren");
        dialog.setFilterNames(new String[] { "PNG-Grafik (*.png)", "SVG-Grafik (*.svg)" });
        dialog.setFilterExtensions(new String[] { "*.png", "*.svg" });
        dialog.setFilterIndex(SETTINGS.getInt("format", 0));
        dialog.setOverwrite(false);
        dialog.setFilterPath(SETTINGS.getString("lastdir", System.getProperty("user.home")));
        dialog.setFileName(defaultName(from, to) + (dialog.getFilterIndex() == 1 ? ".svg" : ".png"));
        String selected = dialog.open();
        if (selected == null || selected.isBlank())
            return;

        int format = dialog.getFilterIndex() == 1 ? 1 : 0;
        String extension = format == 1 ? ".svg" : ".png";
        selected = ExportFileNames.withExtension(selected, extension);
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
            {
                Files.writeString(Path.of(selected), SankeySvgExporter.create(graph, from, to),
                    StandardCharsets.UTF_8);
            }
            else
            {
                SankeyPngExporter.save(GUI.getDisplay(), graph, from, to, selected);
            }
            Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                "Geldflussgrafik gespeichert: " + selected, StatusBarMessage.TYPE_SUCCESS));
        }
        catch (Exception e)
        {
            throw new ApplicationException("Geldflussgrafik konnte nicht gespeichert werden: "
                + e.getMessage(), e);
        }
    }

    private static String defaultName(LocalDate from, LocalDate to)
    {
        return "geldfluss-" + (from == null ? "start" : from) + "-bis-" + (to == null ? "heute" : to);
    }

}
