package de.open4me.hibiscus.reports.model;

public record CategoryInfo(String id, String name, boolean skipReports, Integer color)
{
    public CategoryInfo
    {
        name = name == null || name.isBlank() ? "Ohne Namen" : name.trim();
    }

    public String getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public boolean isSkipReports()
    {
        return skipReports;
    }

    public boolean getSkipReports()
    {
        return skipReports;
    }

    public Integer getColor()
    {
        return color;
    }
}
