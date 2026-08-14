package de.open4me.hibiscus.reports.ui;

import java.util.HashSet;
import java.util.Set;

import de.willuhn.jameica.system.Settings;

final class NavigationTreeFilterSettings
{
    private static final String HIDDEN_IDS = "hidden.ids";
    private static final String INITIALIZED = "initialized";
    private static final Settings SETTINGS = new Settings(NavigationTreeFilterSettings.class);

    private NavigationTreeFilterSettings()
    {
    }

    static Set<String> hiddenIds()
    {
        return parse(SETTINGS.getList(HIDDEN_IDS, null));
    }

    static void save(Set<String> hiddenIds)
    {
        String[] ids = hiddenIds == null ? new String[0]
            : hiddenIds.stream().filter(id -> id != null && !id.isBlank()).sorted().toArray(String[]::new);
        SETTINGS.setAttribute(HIDDEN_IDS, ids);
        SETTINGS.setAttribute(INITIALIZED, true);
    }

    private static Set<String> parse(String[] values)
    {
        Set<String> result = new HashSet<>();
        if (values != null)
        {
            for (String value : values)
            {
                if (value != null && !value.isBlank())
                    result.add(value.trim());
            }
        }
        return result;
    }
}
