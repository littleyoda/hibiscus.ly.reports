package de.open4me.hibiscus.reports.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.eclipse.swt.widgets.Display;

import de.open4me.hibiscus.reports.model.SankeyGraph;

public final class PngExportSmokeTest
{
    private PngExportSmokeTest()
    {
    }

    public static void main(String[] args) throws Exception
    {
        if (args.length != 1)
            throw new IllegalArgumentException("Ausgabedatei fehlt");
        SankeyGraph graph = new SankeyGraph(List.of(
            new SankeyGraph.Node("source", "Gehalt", 100d, 100d, 0x239b56, 0, null, null),
            new SankeyGraph.Node("available", "Verfügbare Mittel", 100d, 100d, 0x2ca02c, 1, null, null)),
            List.of(new SankeyGraph.Link("source", "available", 100d)), 12, 100d, 0d);
        Display display = new Display();
        try
        {
            SankeyPngExporter.save(display, graph, LocalDate.of(2025, 1, 15),
                LocalDate.of(2025, 12, 20), args[0]);
        }
        finally
        {
            display.dispose();
        }
        byte[] signature = Files.readAllBytes(Path.of(args[0]));
        if (signature.length < 8 || signature[0] != (byte) 0x89 || signature[1] != 'P'
            || signature[2] != 'N' || signature[3] != 'G')
            throw new AssertionError("Keine gültige PNG-Datei erzeugt");
        System.out.println("PNG export smoke test passed");
    }
}
