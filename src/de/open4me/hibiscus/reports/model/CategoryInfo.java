package de.open4me.hibiscus.reports.model;

public record CategoryInfo(String id, String name, String nummer, String notiz, String suchbegriff, boolean regex,
                           String typ, int typCode, String kontoId, String kontoName, String kontoKategorie,
                           boolean skipReports, boolean customColor, Integer color)
{
    public CategoryInfo(String id, String name, boolean skipReports, Integer color)
    {
        this(id, name, "", "", "", false, "egal", 2, null, null, null, skipReports, color != null, color);
    }

    public CategoryInfo
    {
        id = text(id);
        name = name == null || name.isBlank() ? "Ohne Namen" : name.trim();
        nummer = text(nummer);
        notiz = text(notiz);
        suchbegriff = text(suchbegriff);
        typ = text(typ);
    }

    public String getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public String getNummer() { return nummer; }
    public String getNotiz() { return notiz; }
    public String getSuchbegriff() { return suchbegriff; }
    public boolean isRegex() { return regex; }
    public boolean getRegex() { return regex; }
    public String getTyp() { return typ; }
    public int getTypCode() { return typCode; }
    public String getKontoId() { return kontoId; }
    public String getKontoName() { return kontoName; }
    public String getKontoKategorie() { return kontoKategorie; }

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

    public boolean isCustomColor() { return customColor; }
    public boolean getCustomColor() { return customColor; }

    public String getColorHex()
    {
        return color == null ? null : String.format("#%06X", color & 0xffffff);
    }

    private static String text(String value)
    {
        return value == null ? "" : value.trim();
    }
}
