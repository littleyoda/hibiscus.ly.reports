package de.open4me.hibiscus.reports;

import java.io.InputStream;

import javax.xml.parsers.DocumentBuilderFactory;

final class HelpResourceTests
{
    static void run()
    {
        exists("help/de_de/de.open4me.hibiscus.reports.ui.FlowView.txt");
        exists("help/de_de/de.open4me.hibiscus.reports.ui.MonthlyOverviewView.txt");
        exists("help/de_de/de.open4me.hibiscus.reports.ui.GroupBalanceView.txt");
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
}
