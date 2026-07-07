package de.open4me.hibiscus.reports.model;

public record CategoryInfo(String id, String name, boolean skipReports, Integer color)
{
    public CategoryInfo
    {
        name = name == null || name.isBlank() ? "Ohne Namen" : name.trim();
    }
}

