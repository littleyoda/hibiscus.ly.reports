package de.open4me.hibiscus.reports.data;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.open4me.hibiscus.reports.model.CategoryInfo;
import de.willuhn.datasource.GenericObjectNode;
import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.hbci.rmi.UmsatzTyp;
import de.willuhn.jameica.hbci.rmi.Konto;
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
        result.sort(HibiscusReportCategoryProvider::comparePaths);
        return List.copyOf(result);
    }

    private static int comparePaths(List<CategoryInfo> left, List<CategoryInfo> right)
    {
        int common = Math.min(left.size(), right.size());
        for (int index = 0; index < common; index++)
        {
            CategoryInfo a = left.get(index);
            CategoryInfo b = right.get(index);
            int number = a.nummer().compareToIgnoreCase(b.nummer());
            if (number != 0)
                return number;
            int name = a.name().compareToIgnoreCase(b.name());
            if (name != 0)
                return name;
        }
        return Integer.compare(left.size(), right.size());
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
        boolean customColor = category.isCustomColor();
        if (customColor)
        {
            int[] rgb = category.getColor();
            if (rgb != null && rgb.length >= 3)
                color = ((rgb[0] & 0xff) << 16) | ((rgb[1] & 0xff) << 8) | (rgb[2] & 0xff);
        }
        Konto account = category.getKonto();
        String accountName = null;
        if (account != null)
        {
            accountName = account.getBezeichnung();
            if (accountName == null || accountName.isBlank())
                accountName = account.getLongName();
        }
        int type = category.getTyp();
        return new CategoryInfo(category.getID(), category.getName(), category.getNummer(), category.getKommentar(),
            category.getPattern(), category.isRegex(), typeName(type), type,
            account == null ? null : account.getID(), accountName, category.getKontoKategorie(),
            category.hasFlag(UmsatzTyp.FLAG_SKIP_REPORTS), customColor, color);
    }

    private static String typeName(int type)
    {
        return switch (type)
        {
            case UmsatzTyp.TYP_AUSGABE -> "ausgabe";
            case UmsatzTyp.TYP_EINNAHME -> "einnahme";
            default -> "egal";
        };
    }
}
