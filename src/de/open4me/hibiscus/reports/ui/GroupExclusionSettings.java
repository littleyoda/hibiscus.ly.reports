package de.open4me.hibiscus.reports.ui;

import java.util.HashSet;
import java.util.Set;

import de.willuhn.jameica.system.Settings;

final class GroupExclusionSettings
{
    private static final String EXCLUDED_KEYS = "excluded.group.keys";

    private GroupExclusionSettings()
    {
    }

    static Set<String> load(Settings settings)
    {
        return parse(settings.getList(EXCLUDED_KEYS, null));
    }

    static void save(Settings settings, Set<String> excludedKeys)
    {
        String[] keys = excludedKeys == null ? new String[0]
            : excludedKeys.stream().filter(key -> key != null && !key.isBlank()).sorted().toArray(String[]::new);
        settings.setAttribute(EXCLUDED_KEYS, keys);
    }

    static Set<String> parse(String[] values)
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
        return Set.copyOf(result);
    }
}
