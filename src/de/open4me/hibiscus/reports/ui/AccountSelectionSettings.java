package de.open4me.hibiscus.reports.ui;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.open4me.hibiscus.reports.model.AccountInfo;
import de.willuhn.jameica.system.Settings;

final class AccountSelectionSettings
{
    private static final String EXCLUDED_IDS = "excluded.account.ids";
    private static final String INITIALIZED = "excluded.account.ids.initialized";
    private static final String LEGACY_INCLUDED_IDS = "account.ids";

    private AccountSelectionSettings()
    {
    }

    static Set<String> load(Settings settings, List<AccountInfo> accounts, boolean migrateLegacy)
    {
        boolean initialized = settings.getBoolean(INITIALIZED, false);
        String[] stored = settings.getList(EXCLUDED_IDS, null);
        String legacy = migrateLegacy ? settings.getString(LEGACY_INCLUDED_IDS, null) : null;
        Set<String> result = resolve(accounts, stored, initialized, legacy, migrateLegacy);
        if (!initialized)
        {
            save(settings, result);
            if (migrateLegacy)
                settings.setAttribute(LEGACY_INCLUDED_IDS, (String) null);
        }
        return result;
    }

    static void save(Settings settings, Set<String> excludedIds)
    {
        String[] ids = excludedIds == null ? new String[0]
            : excludedIds.stream().filter(id -> id != null && !id.isBlank()).sorted().toArray(String[]::new);
        settings.setAttribute(EXCLUDED_IDS, ids);
        settings.setAttribute(INITIALIZED, true);
    }

    static Set<String> resolve(List<AccountInfo> accounts, String[] stored, boolean initialized,
                               String legacyIncluded, boolean migrateLegacy)
    {
        if (initialized || stored != null)
            return parse(stored);
        if (!migrateLegacy || legacyIncluded == null)
            return Set.of();

        Set<String> included = parse(legacyIncluded.split(",", -1));
        Set<String> excluded = new HashSet<>();
        for (AccountInfo account : accounts)
        {
            if (account.isEuro() && !included.contains(account.id()))
                excluded.add(account.id());
        }
        return Set.copyOf(excluded);
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
        return Set.copyOf(result);
    }
}
