package de.open4me.hibiscus.reports.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import de.open4me.hibiscus.reports.data.ReportTransactionsProxy;

public final class ReportAccount
{
    private final String id;
    private final double saldo;
    private final double verfuegbar;
    private final LocalDate aktualisiert;
    private final String name;
    private final String blz;
    private final String iban;
    private final String gruppe;
    private final ReportTransactionsProxy umsaetze;

    public ReportAccount(double saldo, String name, String blz, String iban, String gruppe)
    {
        this("", saldo, saldo, null, name, blz, iban, gruppe, null);
    }

    public ReportAccount(String id, double saldo, double verfuegbar, LocalDate aktualisiert, String name, String blz,
                         String iban, String gruppe, ReportTransactionsProxy umsaetze)
    {
        this.saldo = money(saldo);
        this.verfuegbar = money(verfuegbar);
        this.aktualisiert = aktualisiert;
        this.id = text(id);
        this.name = text(name);
        this.blz = text(blz);
        this.iban = text(iban);
        this.gruppe = text(gruppe);
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

    public LocalDate getAktualisiert()
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

    public String getIban()
    {
        return iban;
    }

    public String getGruppe()
    {
        return gruppe;
    }

    public ReportTransactionsProxy getUmsaetze()
    {
        return umsaetze;
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
