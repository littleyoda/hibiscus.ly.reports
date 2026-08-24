package de.open4me.hibiscus.reports.model;

import java.util.List;

public final class ReportCategory
{
    private final CategoryInfo info;
    private final String parentId;
    private final int level;
    private final String path;
    private final List<String> pathIds;
    private final boolean skipReportsEffective;
    private final List<ReportCategory> children;

    public ReportCategory(CategoryInfo info, String parentId, int level, String path, List<String> pathIds,
                          boolean skipReportsEffective, List<ReportCategory> children)
    {
        this.info = info;
        this.parentId = parentId;
        this.level = level;
        this.path = path;
        this.pathIds = List.copyOf(pathIds);
        this.skipReportsEffective = skipReportsEffective;
        this.children = List.copyOf(children);
    }

    public String getId() { return info.id(); }
    public String getName() { return info.name(); }
    public String getNummer() { return info.nummer(); }
    public String getNotiz() { return info.notiz(); }
    public String getSuchbegriff() { return info.suchbegriff(); }
    public boolean isRegex() { return info.regex(); }
    public boolean getRegex() { return info.regex(); }
    public String getTyp() { return info.typ(); }
    public int getTypCode() { return info.typCode(); }
    public String getKontoId() { return info.kontoId(); }
    public String getKontoName() { return info.kontoName(); }
    public String getKontoKategorie() { return info.kontoKategorie(); }
    public boolean isSkipReports() { return info.skipReports(); }
    public boolean getSkipReports() { return info.skipReports(); }
    public boolean isSkipReportsEffective() { return skipReportsEffective; }
    public boolean getSkipReportsEffective() { return skipReportsEffective; }
    public boolean isCustomColor() { return info.customColor(); }
    public boolean getCustomColor() { return info.customColor(); }
    public Integer getColor() { return info.color(); }
    public String getColorHex() { return info.getColorHex(); }
    public String getParentId() { return parentId; }
    public int getLevel() { return level; }
    public String getPath() { return path; }
    public List<String> getPathIds() { return pathIds; }
    public List<ReportCategory> getChildren() { return children; }
}
