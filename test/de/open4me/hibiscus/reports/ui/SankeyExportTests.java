package de.open4me.hibiscus.reports.ui;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
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
        formatsPercentagesFromNodeBase();
        formatsOptionalDetailParts();
        calculatesSharedLayoutAndPngDimensions();
        exportsSankeyMaticText();
        normalizesSelectedFileExtension();
    }

    private static void exportsWellFormedSvgWithEscapedText()
    {
        SankeyGraph graph = graph();
        String svg = SankeySvgExporter.create(graph, LocalDate.of(2025, 1, 15), LocalDate.of(2025, 12, 20));
        check(svg.contains("A&amp;B &lt;Test&gt;"), "SVG text escaping");
        check(svg.contains("15.01.2025 – 20.12.2025"), "SVG exact period");
        check(svg.contains("Einnahmen: 100 €"), "SVG summary");
        check(svg.contains("dy=\"14\""), "SVG compact label line spacing");
        check(svg.contains("fill=\"#239b56\""), "SVG category link color");
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

    private static void formatsPercentagesFromNodeBase()
    {
        SankeyGraph graph = new SankeyGraph(List.of(), List.of(), 1, 3000d, 2000d);
        SankeyGraph.Node node = new SankeyGraph.Node("expense:housing", "Wohnen", 2000d, 3000d,
            0xe67e22, 2, null, null);
        check(SankeyText.detailLine(graph, node).startsWith("66,7%"), "percentage base formatting");
    }

    private static void formatsOptionalDetailParts()
    {
        SankeyGraph graph = new SankeyGraph(List.of(), List.of(), 4, 1000d, 1000d);
        SankeyGraph.Node node = new SankeyGraph.Node("expense:housing", "Wohnen", 400d, 1000d,
            0xe67e22, 2, null, null);

        String totalOnly = SankeyText.detailLine(graph, node, new SankeyText.DetailOptions(true, false));
        check(totalOnly.equals("40,0%   ∑400 €"), "detail line with total only");

        String averageOnly = SankeyText.detailLine(graph, node, new SankeyText.DetailOptions(false, true));
        check(averageOnly.equals("40,0%   ∅100 €/M"), "detail line with average only");

        String percentOnly = SankeyText.detailLine(graph, node, new SankeyText.DetailOptions(false, false));
        check(percentOnly.equals("40,0%"), "detail line with percentage only");
    }

    private static void calculatesSharedLayoutAndPngDimensions()
    {
        SankeyGraph graph = graph();
        int chartHeight = SankeyLayout.preferredHeight(graph);
        SankeyLayout.Scene scene = SankeyLayout.create(graph, chartHeight);
        check(scene.nodes().size() == graph.nodes().size(), "layout node count");
        check(scene.links().size() == graph.links().size(), "layout link count");
        checksCategoryLinkColors();
        check(SankeyLayout.preferredHeight(manyNodesGraph()) > 700, "layout reserves category gaps");
        check(SankeyPngExporter.outputWidth() == SankeyLayout.WIDTH * 2, "PNG 2x width");
        check(SankeyPngExporter.outputHeight(graph)
            == (SankeySvgExporter.HEADER_HEIGHT + chartHeight) * 2, "PNG 2x height");
    }

    private static void normalizesSelectedFileExtension()
    {
        check("geldfluss.svg".equals(ExportFileNames.withExtension("geldfluss.png", ".svg")),
            "replace selected extension");
        check("geldfluss.txt".equals(ExportFileNames.withExtension("geldfluss.svg", ".txt")),
            "replace selected svg extension");
        check("geldfluss.png".equals(ExportFileNames.withExtension("geldfluss.txt", ".png")),
            "replace selected txt extension");
        check("geldfluss.png".equals(ExportFileNames.withExtension("geldfluss", ".png")),
            "append selected extension");
    }

    private static void exportsSankeyMaticText()
    {
        SankeyGraph graph = new SankeyGraph(List.of(
            new SankeyGraph.Node("income", "Einkommen [2025]", 100.50d, 100.50d, 0x239b56, 0, null, null),
            new SankeyGraph.Node("available", "Verfügbare Mittel", 100.50d, 100.50d, 0x2ca02c, 1, null, null),
            new SankeyGraph.Node("expense", "Ausgabe #abc", 70d, 100.50d, 0xe67e22, 2, null, null),
            new SankeyGraph.Node("expense2", "Ausgabe #abc", 30.5d, 100.50d, 0xd35400, 2, null, null)),
            List.of(
                new SankeyGraph.Link("income", "available", 100.50d),
                new SankeyGraph.Link("available", "expense", 70d),
                new SankeyGraph.Link("available", "expense2", 30.5d)),
            12, 100.50d, 100.50d);

        String text = SankeyTextExporter.create(graph, LocalDate.of(2025, 1, 15),
            LocalDate.of(2025, 12, 20));
        check(text.contains("// Geldfluss"), "text export title comment");
        check(text.contains("// Zeitraum: 15.01.2025 – 20.12.2025"), "text export period comment");
        check(text.contains("Einkommen (2025) [101] Verfügbare Mittel #239b56"),
            "text export flow syntax and source color");
        check(text.contains("Verfügbare Mittel [70] Ausgabe Nr. abc #e67e22"),
            "text export target color");
        check(text.contains("Verfügbare Mittel [31] Ausgabe Nr. abc (2) #d35400"),
            "text export duplicate labels");
        check(text.contains(":Einkommen (2025) #239b56"), "text export node color");
    }

    private static SankeyGraph graph()
    {
        return new SankeyGraph(List.of(
            new SankeyGraph.Node("source", "A&B <Test>", 100d, 100d, 0x239b56, 0, null, null),
            new SankeyGraph.Node("available", "Verfügbare Mittel", 100d, 100d, 0x2ca02c, 1, null, null)),
            List.of(new SankeyGraph.Link("source", "available", 100d)), 12, 100d, 0d);
    }

    private static SankeyGraph manyNodesGraph()
    {
        List<SankeyGraph.Node> nodes = new ArrayList<>();
        for (int i = 0; i < 10; i++)
            nodes.add(new SankeyGraph.Node("expense:" + i, "Kategorie " + i, 100d, 1000d,
                0xe67e22, 2, null, null));
        return new SankeyGraph(nodes, List.of(), 12, 1000d, 1000d);
    }

    private static void checksCategoryLinkColors()
    {
        SankeyGraph graph = new SankeyGraph(List.of(
            new SankeyGraph.Node("income", "Einkommen", 100d, 100d, 0x239b56, 0, null, null),
            new SankeyGraph.Node("available", "Verfügbare Mittel", 100d, 100d, 0x2ca02c, 1, null, null),
            new SankeyGraph.Node("expense", "Ausgabe", 70d, 100d, 0xe67e22, 2, null, null),
            new SankeyGraph.Node("child", "Unterkategorie", 40d, 100d, 0x6f3fa0, 3, null, null)),
            List.of(
                new SankeyGraph.Link("income", "available", 100d),
                new SankeyGraph.Link("available", "expense", 70d),
                new SankeyGraph.Link("expense", "child", 40d)),
            12, 100d, 70d);

        SankeyLayout.Scene scene = SankeyLayout.create(graph, SankeyLayout.preferredHeight(graph));
        check(linkColor(scene, "income", "available") == 0x239b56, "income link uses source color");
        check(linkColor(scene, "available", "expense") == 0xe67e22, "expense link uses target color");
        check(linkColor(scene, "expense", "child") == 0x6f3fa0, "child link uses target color");
    }

    private static int linkColor(SankeyLayout.Scene scene, String sourceId, String targetId)
    {
        return scene.links().stream()
            .filter(link -> link.link().sourceId().equals(sourceId) && link.link().targetId().equals(targetId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing link " + sourceId + " -> " + targetId))
            .color();
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
