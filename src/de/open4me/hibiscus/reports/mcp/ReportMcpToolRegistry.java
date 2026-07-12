package de.open4me.hibiscus.reports.mcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.willuhn.jameica.gui.extension.Extendable;

public final class ReportMcpToolRegistry implements Extendable
{
    public static final String EXTENDABLE_ID = "hibiscus.ly.reports.mcp.tools";

    private final List<Object> providers = new ArrayList<>();

    @Override
    public String getExtendableID()
    {
        return EXTENDABLE_ID;
    }

    public void register(Object provider)
    {
        if (provider == null)
            throw new IllegalArgumentException("MCP-Provider darf nicht null sein.");
        providers.add(provider);
    }

    List<Object> providers()
    {
        return Collections.unmodifiableList(providers);
    }
}
