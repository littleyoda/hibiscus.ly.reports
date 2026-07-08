package de.open4me.hibiscus.reports.data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.open4me.hibiscus.reports.model.ReportAccount;
import de.open4me.hibiscus.reports.model.ReportAccountGroup;

public final class ReportAccountGroupsProxy implements Iterable<ReportAccountGroup>
{
    private final ReportAccountsProxy accounts;
    private List<ReportAccountGroup> activeGroups;
    private List<ReportAccountGroup> allGroups;

    public ReportAccountGroupsProxy(ReportAccountsProxy accounts)
    {
        this.accounts = accounts;
    }

    @Override
    public Iterator<ReportAccountGroup> iterator()
    {
        return getAktive().iterator();
    }

    public List<ReportAccountGroup> getAktive()
    {
        if (activeGroups == null)
            activeGroups = group(accounts.getAktive());
        return activeGroups;
    }

    public List<ReportAccountGroup> getAlle()
    {
        if (allGroups == null)
            allGroups = group(accounts.getAlle());
        return allGroups;
    }

    public int size()
    {
        return getAktive().size();
    }

    public boolean isEmpty()
    {
        return getAktive().isEmpty();
    }

    private static List<ReportAccountGroup> group(List<ReportAccount> accounts)
    {
        Map<String, List<ReportAccount>> grouped = new LinkedHashMap<>();
        for (ReportAccount account : accounts)
        {
            grouped.computeIfAbsent(groupName(account), key -> new ArrayList<>()).add(account);
        }
        return grouped.entrySet().stream()
            .map(entry -> new ReportAccountGroup(entry.getKey(), entry.getValue()))
            .toList();
    }

    private static String groupName(ReportAccount account)
    {
        String group = account.getGruppe();
        return group == null || group.isBlank() ? "Ohne Gruppe" : group;
    }
}
