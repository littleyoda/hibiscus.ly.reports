package de.open4me.hibiscus.reports.model;

public record AccountInfo(String id, String group, String name, String currency, boolean disabled)
{
    public boolean isEuro()
    {
        return "EUR".equalsIgnoreCase(currency);
    }

    public String groupLabel()
    {
        return group == null || group.isBlank() ? "Ohne Gruppe" : group;
    }
}
