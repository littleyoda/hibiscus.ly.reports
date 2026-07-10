package de.open4me.hibiscus.reports.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

import de.willuhn.jameica.gui.extension.Extendable;

public final class ReportTemplateContext implements Extendable
{
    public static final String EXTENDABLE_ID = "hibiscus.ly.reports.template.context";

    private final Map<String, Object> objects = new LinkedHashMap<>();

    public ReportTemplateContext(Map<String, Object> defaults)
    {
        if (defaults != null)
            objects.putAll(defaults);
    }

    @Override
    public String getExtendableID()
    {
        return EXTENDABLE_ID;
    }

    public void put(String name, Object value)
    {
        String key = key(name);
        if (objects.containsKey(key))
            throw new IllegalArgumentException("Template-Objekt existiert bereits: " + key);
        objects.put(key, value);
    }

    public void putIfAbsent(String name, Object value)
    {
        objects.putIfAbsent(key(name), value);
    }

    public boolean contains(String name)
    {
        return objects.containsKey(key(name));
    }

    public Map<String, Object> objects()
    {
        return Collections.unmodifiableMap(new LinkedHashMap<>(objects));
    }

    private static String key(String name)
    {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Template-Objektname darf nicht leer sein.");
        return name.trim();
    }
}
