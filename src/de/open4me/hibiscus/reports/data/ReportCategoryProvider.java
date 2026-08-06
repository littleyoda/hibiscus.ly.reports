package de.open4me.hibiscus.reports.data;

import java.util.List;

import de.open4me.hibiscus.reports.model.CategoryInfo;

public interface ReportCategoryProvider
{
    List<List<CategoryInfo>> loadCategoryPaths() throws Exception;
}
