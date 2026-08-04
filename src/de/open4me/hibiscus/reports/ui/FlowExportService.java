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
        export(graph, from, to, SankeyText.DetailOptions.DEFAULT);
    }

    static void export(SankeyGraph graph, LocalDate from, LocalDate to,
                       SankeyText.DetailOptions detailOptions) throws ApplicationException
    {
        if (graph == null || graph.nodes().isEmpty())
            throw new ApplicationException("Es ist keine Geldflussgrafik zum Exportieren vorhanden.");

        FileDialog dialog = new FileDialog(GUI.getShell(), SWT.SAVE);
        dialog.setText("Geldfluss exportieren");
        dialog.setFilterNames(new String[] { "PNG-Grafik (*.png)", "SVG-Grafik (*.svg)",
            "SankeyMATIC-Text (*.txt)" });
        dialog.setFilterExtensions(new String[] { "*.png", "*.svg", "*.txt" });
        dialog.setFilterIndex(SETTINGS.getInt("format", 0));
        dialog.setOverwrite(false);
        dialog.setFilterPath(SETTINGS.getString("lastdir", System.getProperty("user.home")));
        dialog.setFileName(defaultName(from, to) + extension(dialog.getFilterIndex()));
        String selected = dialog.open();
        if (selected == null || selected.isBlank())
            return;

        int format = Math.min(Math.max(dialog.getFilterIndex(), 0), 2);
        selected = ExportFileNames.withExtension(selected, extension(format));
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
                Files.writeString(Path.of(selected), SankeySvgExporter.create(graph, from, to, detailOptions),
                    StandardCharsets.UTF_8);
            }
            else if (format == 2)
            {
                Files.writeString(Path.of(selected), SankeyTextExporter.create(graph, from, to),
                    StandardCharsets.UTF_8);
            }
            else
            {
                SankeyPngExporter.save(GUI.getDisplay(), graph, from, to, selected, detailOptions);
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

    private static String extension(int format)
    {
        if (format == 1)
            return ".svg";
        if (format == 2)
            return ".txt";
        return ".png";
    }

}
