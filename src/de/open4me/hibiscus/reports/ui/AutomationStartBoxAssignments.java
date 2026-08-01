package de.open4me.hibiscus.reports.ui;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.open4me.hibiscus.reports.automation.model.Automation;
import de.willuhn.jameica.system.Settings;

final class AutomationStartBoxAssignments
{
    static final int SLOT_COUNT = 32;
    private static final String SLOT_PREFIX = "slot.";

    private AutomationStartBoxAssignments()
    {
    }

    static synchronized Map<Integer, Automation> assignments(List<Automation> automations)
    {
        return assignments(automations, new JameicaSlotSettings());
    }

    static Map<Integer, Automation> assignments(List<Automation> automations, SlotSettings settings)
    {
        List<Automation> sorted = automations.stream()
            .sorted(Comparator.comparing(Automation::name, String.CASE_INSENSITIVE_ORDER))
            .toList();

        Map<String, Automation> byId = new HashMap<>();
        for (Automation automation : sorted)
            byId.put(ReportsNavigationExtension.id(automation), automation);

        Map<Integer, String> assignedIds = new HashMap<>();
        Set<String> usedIds = new HashSet<>();
        for (int slot = 1; slot <= SLOT_COUNT; slot++)
        {
            String id = settings.get(key(slot));
            if (id == null || !byId.containsKey(id) || usedIds.contains(id))
            {
                settings.set(key(slot), null);
                continue;
            }
            assignedIds.put(slot, id);
            usedIds.add(id);
        }

        for (Automation automation : sorted)
        {
            String id = ReportsNavigationExtension.id(automation);
            if (usedIds.contains(id))
                continue;
            int freeSlot = firstFreeSlot(assignedIds);
            if (freeSlot < 0)
                break;
            assignedIds.put(freeSlot, id);
            usedIds.add(id);
            settings.set(key(freeSlot), id);
        }

        Map<Integer, Automation> result = new HashMap<>();
        for (Map.Entry<Integer, String> entry : assignedIds.entrySet())
            result.put(entry.getKey(), byId.get(entry.getValue()));
        return result;
    }

    private static int firstFreeSlot(Map<Integer, String> assignedIds)
    {
        for (int slot = 1; slot <= SLOT_COUNT; slot++)
        {
            if (!assignedIds.containsKey(slot))
                return slot;
        }
        return -1;
    }

    static String key(int slot)
    {
        return SLOT_PREFIX + slot + ".automation";
    }

    interface SlotSettings
    {
        String get(String key);

        void set(String key, String value);
    }

    private static final class JameicaSlotSettings implements SlotSettings
    {
        private final Settings settings = new Settings(AbstractAutomationStartBox.class);

        @Override
        public String get(String key)
        {
            return settings.getString(key, null);
        }

        @Override
        public void set(String key, String value)
        {
            settings.setAttribute(key, value);
        }
    }
}
