package de.open4me.hibiscus.reports.ui;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import de.open4me.hibiscus.reports.model.SankeyGraph;

public final class SankeyExportTests
{
    private SankeyExportTests()
    {
    }

    public static void run()
    {
        exportsWellFormedSvgWithEscapedText();
        calculatesSharedLayoutAndPngDimensions();
        normalizesSelectedFileExtension();
    }

    private static void exportsWellFormedSvgWithEscapedText()
    {
        SankeyGraph graph = graph();
        String svg = SankeySvgExporter.create(graph, LocalDate.of(2025, 1, 15), LocalDate.of(2025, 12, 20));
        check(svg.contains("A&amp;B &lt;Test&gt;"), "SVG text escaping");
        check(svg.contains("15.01.2025 – 20.12.2025"), "SVG exact period");
        check(svg.contains("Einnahmen: 100 €"), "SVG summary");
        try
        {
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
            check("svg".equals(document.getDocumentElement().getNodeName()), "SVG root element");
            check(document.getElementsByTagName("path").getLength() == 1, "SVG link path");
            check(document.getElementsByTagName("rect").getLength() == 3, "SVG background and nodes");
        }
        catch (Exception e)
        {
            throw new AssertionError("SVG must be well-formed XML", e);
        }
    }

    private static void calculatesSharedLayoutAndPngDimensions()
    {
        SankeyGraph graph = graph();
        int chartHeight = SankeyLayout.preferredHeight(graph);
        SankeyLayout.Scene scene = SankeyLayout.create(graph, chartHeight);
        check(scene.nodes().size() == graph.nodes().size(), "layout node count");
        check(scene.links().size() == graph.links().size(), "layout link count");
        check(SankeyPngExporter.outputWidth() == SankeyLayout.WIDTH * 2, "PNG 2x width");
        check(SankeyPngExporter.outputHeight(graph)
            == (SankeySvgExporter.HEADER_HEIGHT + chartHeight) * 2, "PNG 2x height");
    }

    private static void normalizesSelectedFileExtension()
    {
        check("geldfluss.svg".equals(ExportFileNames.withExtension("geldfluss.png", ".svg")),
            "replace selected extension");
        check("geldfluss.png".equals(ExportFileNames.withExtension("geldfluss", ".png")),
            "append selected extension");
    }

    private static SankeyGraph graph()
    {
        return new SankeyGraph(List.of(
            new SankeyGraph.Node("source", "A&B <Test>", 100d, 100d, 0x239b56, 0, null, null),
            new SankeyGraph.Node("available", "Verfügbare Mittel", 100d, 100d, 0x2ca02c, 1, null, null)),
            List.of(new SankeyGraph.Link("source", "available", 100d)), 12, 100d, 0d);
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
