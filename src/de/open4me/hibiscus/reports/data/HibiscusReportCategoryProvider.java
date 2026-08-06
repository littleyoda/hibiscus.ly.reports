package de.open4me.hibiscus.reports.data;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.open4me.hibiscus.reports.model.CategoryInfo;
import de.willuhn.datasource.GenericObjectNode;
import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.hbci.rmi.UmsatzTyp;
import de.willuhn.jameica.hbci.server.UmsatzTypUtil;

public final class HibiscusReportCategoryProvider implements ReportCategoryProvider
{
    @Override
    public List<List<CategoryInfo>> loadCategoryPaths() throws Exception
    {
        List<List<CategoryInfo>> result = new ArrayList<>();
        DBIterator<UmsatzTyp> categories = UmsatzTypUtil.getAll();
        while (categories.hasNext())
            result.add(categoryPath(categories.next()));
        result.sort(Comparator.comparing(HibiscusReportCategoryProvider::displayPath,
            String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    private static String displayPath(List<CategoryInfo> path)
    {
        return path.stream().map(CategoryInfo::name).reduce((left, right) -> left + " > " + right).orElse("");
    }

    private static List<CategoryInfo> categoryPath(UmsatzTyp category) throws RemoteException
    {
        if (category == null)
            return List.of();

        List<CategoryInfo> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        GenericObjectNode current = category;
        while (current instanceof UmsatzTyp type && visited.add(type.getID()))
        {
            result.add(toInfo(type));
            current = type.getParent();
        }
        Collections.reverse(result);
        return result;
    }

    private static CategoryInfo toInfo(UmsatzTyp category) throws RemoteException
    {
        Integer color = null;
        if (category.isCustomColor())
        {
            int[] rgb = category.getColor();
            if (rgb != null && rgb.length >= 3)
                color = ((rgb[0] & 0xff) << 16) | ((rgb[1] & 0xff) << 8) | (rgb[2] & 0xff);
        }
        return new CategoryInfo(category.getID(), category.getName(),
            category.hasFlag(UmsatzTyp.FLAG_SKIP_REPORTS), color);
    }
}
