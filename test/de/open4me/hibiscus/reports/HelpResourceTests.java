package de.open4me.hibiscus.reports;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

final class HelpResourceTests
{
    static void run()
    {
        exists("help/de_de/de.open4me.hibiscus.reports.ui.FlowView.txt");
        exists("help/de_de/de.open4me.hibiscus.reports.ui.MonthlyOverviewView.txt");
        exists("help/de_de/de.open4me.hibiscus.reports.ui.GroupBalanceView.txt");
        htmlExists("help/de_de/reports-objects.html");
    }

    private static void exists(String path)
    {
        try (InputStream stream = HelpResourceTests.class.getClassLoader().getResourceAsStream(path))
        {
            if (stream == null)
                throw new AssertionError("missing help resource: " + path);
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream);
            if (!"form".equals(document.getDocumentElement().getTagName()))
                throw new AssertionError("invalid help root element: " + path);
        }
        catch (Exception e)
        {
            throw new AssertionError("unable to read help resource: " + path, e);
        }
    }

    private static void htmlExists(String path)
    {
        try (InputStream stream = HelpResourceTests.class.getClassLoader().getResourceAsStream(path))
        {
            if (stream == null)
                throw new AssertionError("missing help resource: " + path);
            String html = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            if (!html.contains("<html") || !html.contains("Report-Objekte")
                || !html.contains("umsaetze.zeitraum"))
                throw new AssertionError("invalid html help resource: " + path);
        }
        catch (Exception e)
        {
            throw new AssertionError("unable to read html help resource: " + path, e);
        }
    }
}
