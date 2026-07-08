package de.open4me.hibiscus.reports.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class ReportAccountGroup
{
    private final String name;
    private final List<ReportAccount> konten;

    public ReportAccountGroup(String name, List<ReportAccount> konten)
    {
        this.name = name == null || name.isBlank() ? "Ohne Gruppe" : name;
        this.konten = konten == null ? List.of() : List.copyOf(konten);
    }

    public String getName()
    {
        return name;
    }

    public List<ReportAccount> getKonten()
    {
        return konten;
    }

    public int getAnzahl()
    {
        return konten.size();
    }

    public double getSaldo()
    {
        return money(konten.stream().mapToDouble(ReportAccount::getSaldo).sum());
    }

    public double getVerfuegbar()
    {
        return money(konten.stream().mapToDouble(ReportAccount::getVerfuegbar).sum());
    }

    private static double money(double value)
    {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
