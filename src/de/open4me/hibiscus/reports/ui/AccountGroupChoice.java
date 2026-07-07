package de.open4me.hibiscus.reports.ui;

import java.rmi.RemoteException;
import java.util.Objects;

import de.willuhn.jameica.hbci.rmi.Konto;

record AccountGroupChoice(Kind kind, String name, String label)
{
    enum Kind
    {
        ALL,
        NAMED,
        UNGROUPED
    }

    static AccountGroupChoice all()
    {
        return new AccountGroupChoice(Kind.ALL, null, "Alle Gruppen");
    }

    static AccountGroupChoice named(String name)
    {
        return new AccountGroupChoice(Kind.NAMED, name, name);
    }

    static AccountGroupChoice ungrouped()
    {
        return new AccountGroupChoice(Kind.UNGROUPED, null, "Ohne Gruppe");
    }

    boolean isAll()
    {
        return kind == Kind.ALL;
    }

    String storageKey()
    {
        return switch (kind)
        {
            case ALL -> "all";
            case UNGROUPED -> "ungrouped";
            case NAMED -> "group:" + name;
        };
    }

    boolean matches(Konto account) throws RemoteException
    {
        String accountGroup = normalize(account.getKategorie());
        return kind == Kind.UNGROUPED ? accountGroup == null : Objects.equals(name, accountGroup);
    }

    static String normalize(String group)
    {
        if (group == null)
            return null;
        String trimmed = group.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
