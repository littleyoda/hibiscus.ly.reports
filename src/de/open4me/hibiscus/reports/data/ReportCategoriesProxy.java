package de.open4me.hibiscus.reports.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.open4me.hibiscus.reports.model.CategoryInfo;
import de.open4me.hibiscus.reports.model.ReportCategory;

public final class ReportCategoriesProxy implements Iterable<ReportCategory>
{
    private final ReportCategoryProvider provider;
    private Catalog catalog;

    public ReportCategoriesProxy(ReportCategoryProvider provider)
    {
        this.provider = provider;
    }

    @Override
    public Iterator<ReportCategory> iterator()
    {
        return getAlle().iterator();
    }

    public List<ReportCategory> getAlle()
    {
        return catalog().all;
    }

    public List<ReportCategory> getWurzeln()
    {
        return catalog().roots;
    }

    public ReportCategory byId(String id)
    {
        return id == null ? null : catalog().byId.get(id);
    }

    public int size()
    {
        return getAlle().size();
    }

    public boolean isEmpty()
    {
        return getAlle().isEmpty();
    }

    public List<ReportCategory> asList()
    {
        return getAlle();
    }

    public void invalidate()
    {
        catalog = null;
    }

    private Catalog catalog()
    {
        if (catalog == null)
        {
            try
            {
                catalog = build(provider.loadCategoryPaths());
            }
            catch (Exception e)
            {
                throw new IllegalStateException("Kategorien konnten nicht geladen werden", e);
            }
        }
        return catalog;
    }

    static Catalog build(List<List<CategoryInfo>> paths)
    {
        Map<String, Builder> builders = new LinkedHashMap<>();
        for (List<CategoryInfo> path : paths)
        {
            String parentId = null;
            for (CategoryInfo info : path)
            {
                if (info == null || info.id().isBlank())
                    continue;
                Builder builder = builders.computeIfAbsent(info.id(), ignored -> new Builder(info));
                builder.info = info;
                if (!info.id().equals(parentId))
                    builder.parentId = parentId;
                parentId = info.id();
            }
        }

        for (Builder builder : builders.values())
        {
            Builder parent = builders.get(builder.parentId);
            if (parent != null && !wouldCycle(builder, parent, builders))
                parent.children.add(builder);
            else
                builder.parentId = null;
        }

        List<Builder> rootBuilders = builders.values().stream()
            .filter(builder -> builder.parentId == null).toList();
        List<ReportCategory> roots = new ArrayList<>();
        Map<String, ReportCategory> byId = new HashMap<>();
        for (Builder root : rootBuilders)
            roots.add(freeze(root, List.of(), List.of(), false, byId));
        List<ReportCategory> all = new ArrayList<>();
        roots.forEach(root -> flatten(root, all));
        return new Catalog(List.copyOf(all), List.copyOf(roots), Map.copyOf(byId));
    }

    private static boolean wouldCycle(Builder child, Builder parent, Map<String, Builder> builders)
    {
        Set<String> visited = new HashSet<>();
        Builder current = parent;
        while (current != null && visited.add(current.info.id()))
        {
            if (current == child)
                return true;
            current = builders.get(current.parentId);
        }
        return current != null;
    }

    private static ReportCategory freeze(Builder builder, List<String> names, List<String> ids,
                                         boolean skipped, Map<String, ReportCategory> byId)
    {
        List<String> pathNames = append(names, builder.info.name());
        List<String> pathIds = append(ids, builder.info.id());
        boolean effectiveSkip = skipped || builder.info.skipReports();
        List<ReportCategory> children = new ArrayList<>();
        for (Builder child : builder.children)
            children.add(freeze(child, pathNames, pathIds, effectiveSkip, byId));
        ReportCategory result = new ReportCategory(builder.info, builder.parentId, ids.size(),
            String.join(" > ", pathNames), pathIds, effectiveSkip, children);
        byId.put(result.getId(), result);
        return result;
    }

    private static List<String> append(List<String> values, String value)
    {
        List<String> result = new ArrayList<>(values);
        result.add(value);
        return result;
    }

    private static void flatten(ReportCategory category, List<ReportCategory> target)
    {
        target.add(category);
        category.getChildren().forEach(child -> flatten(child, target));
    }

    private static final class Builder
    {
        private CategoryInfo info;
        private String parentId;
        private final List<Builder> children = new ArrayList<>();

        private Builder(CategoryInfo info)
        {
            this.info = info;
        }
    }

    record Catalog(List<ReportCategory> all, List<ReportCategory> roots, Map<String, ReportCategory> byId) { }
}
