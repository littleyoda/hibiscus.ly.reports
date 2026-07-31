package de.open4me.hibiscus.reports.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import de.open4me.hibiscus.reports.data.ReportTransactionsProxy;

public final class ReportAccount
{
    private final String id;
    private final double saldo;
    private final double verfuegbar;
    private final LocalDateTime aktualisiert;
    private final String name;
    private final String blz;
    private final String kontonummer;
    private final String kundennummer;
    private final String bezeichnung;
    private final String iban;
    private final String gruppe;
    private final Integer accountType;
    private final String backendClass;
    private final boolean aktiv;
    private final boolean offline;
    private final ReportTransactionsProxy umsaetze;

    public ReportAccount(double saldo, String name, String blz, String iban, String gruppe)
    {
        this("", saldo, saldo, null, name, blz, "", "", name, iban, gruppe, null, "", true, false, null);
    }

    public ReportAccount(String id, double saldo, double verfuegbar, LocalDateTime aktualisiert, String name, String blz,
                         String iban, String gruppe, boolean offline, ReportTransactionsProxy umsaetze)
    {
        this(id, saldo, verfuegbar, aktualisiert, name, blz, "", "", name, iban, gruppe, null, "", true, offline,
            umsaetze);
    }

    public ReportAccount(String id, double saldo, double verfuegbar, LocalDateTime aktualisiert, String name, String blz,
                         String iban, String gruppe, boolean aktiv, boolean offline, ReportTransactionsProxy umsaetze)
    {
        this(id, saldo, verfuegbar, aktualisiert, name, blz, "", "", name, iban, gruppe, null, "", aktiv, offline,
            umsaetze);
    }

    public ReportAccount(String id, double saldo, double verfuegbar, LocalDateTime aktualisiert, String name, String blz,
                         String iban, String gruppe, Integer accountType, boolean aktiv, boolean offline,
                         ReportTransactionsProxy umsaetze)
    {
        this(id, saldo, verfuegbar, aktualisiert, name, blz, "", "", name, iban, gruppe, accountType, "", aktiv,
            offline, umsaetze);
    }

    public ReportAccount(String id, double saldo, double verfuegbar, LocalDateTime aktualisiert, String name, String blz,
                         String iban, String gruppe, Integer accountType, String backendClass, boolean aktiv,
                         boolean offline, ReportTransactionsProxy umsaetze)
    {
        this(id, saldo, verfuegbar, aktualisiert, name, blz, "", "", name, iban, gruppe, accountType, backendClass,
            aktiv, offline, umsaetze);
    }

    public ReportAccount(String id, double saldo, double verfuegbar, LocalDateTime aktualisiert, String name, String blz,
                         String kontonummer, String kundennummer, String bezeichnung, String iban, String gruppe,
                         Integer accountType, String backendClass, boolean aktiv, boolean offline,
                         ReportTransactionsProxy umsaetze)
    {
        this.saldo = money(saldo);
        this.verfuegbar = money(verfuegbar);
        this.aktualisiert = aktualisiert;
        this.id = text(id);
        this.name = text(name);
        this.blz = text(blz);
        this.kontonummer = text(kontonummer);
        this.kundennummer = text(kundennummer);
        this.bezeichnung = text(bezeichnung);
        this.iban = text(iban);
        this.gruppe = text(gruppe);
        this.accountType = accountType;
        this.backendClass = text(backendClass);
        this.aktiv = aktiv;
        this.offline = offline;
        this.umsaetze = umsaetze;
    }

    public String getId()
    {
        return id;
    }

    public double getSaldo()
    {
        return saldo;
    }

    public double getVerfuegbar()
    {
        return verfuegbar;
    }

    public LocalDateTime getAktualisiert()
    {
        return aktualisiert;
    }

    public String getName()
    {
        return name;
    }

    public String getBlz()
    {
        return blz;
    }

    public String getKontonummer()
    {
        return kontonummer;
    }

    public String getKundennummer()
    {
        return kundennummer;
    }

    public String getKundenkennung()
    {
        return kundennummer;
    }

    public String getBezeichnung()
    {
        return bezeichnung;
    }

    public String getIban()
    {
        return iban;
    }

    public String getGruppe()
    {
        return gruppe;
    }

    public Integer getAccountType()
    {
        return accountType;
    }

    public String getBackendClass()
    {
        return backendClass;
    }

    public boolean getAktiv()
    {
        return aktiv;
    }

    public boolean getOffline()
    {
        return offline;
    }

    public boolean getDepot()
    {
        if (accountType == null)
            return false;
        int type = accountType;
        return (type >= 30 && type <= 39) || (type >= 60 && type <= 69);
    }

    public ReportTransactionsProxy getUmsaetze()
    {
        return umsaetze;
    }

    @Override
    public String toString()
    {
        if (gruppe.isBlank())
            return name;
        if (name.isBlank())
            return gruppe;
        return name + " (" + gruppe + ")";
    }

    private static String text(String value)
    {
        return value == null ? "" : value;
    }

    private static double money(double value)
    {
        if (!Double.isFinite(value))
            return 0d;
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
