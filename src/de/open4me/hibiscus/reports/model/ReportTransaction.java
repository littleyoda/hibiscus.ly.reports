package de.open4me.hibiscus.reports.model;

import java.time.LocalDate;
import java.util.List;

public final class ReportTransaction
{
    private final LocalDate datum;
    private final LocalDate valuta;
    private final double betrag;
    private final double saldo;
    private final String zweck;
    private final String zweck2;
    private final List<String> verwendungszwecke;
    private final String gegenkontoName;
    private final String gegenkontoNummer;
    private final String gegenkontoBlz;
    private final String art;
    private final String kategorie;
    private final List<CategoryInfo> kategoriePfad;
    private final boolean vorgemerkt;
    private final ReportAccount konto;

    public ReportTransaction(LocalDate datum, LocalDate valuta, double betrag, double saldo, String zweck,
                             String zweck2, List<String> verwendungszwecke, String gegenkontoName,
                             String gegenkontoNummer, String gegenkontoBlz, String art, String kategorie,
                             List<CategoryInfo> kategoriePfad, boolean vorgemerkt, ReportAccount konto)
    {
        this.datum = datum;
        this.valuta = valuta;
        this.betrag = betrag;
        this.saldo = saldo;
        this.zweck = text(zweck);
        this.zweck2 = text(zweck2);
        this.verwendungszwecke = verwendungszwecke == null ? List.of() : List.copyOf(verwendungszwecke);
        this.gegenkontoName = text(gegenkontoName);
        this.gegenkontoNummer = text(gegenkontoNummer);
        this.gegenkontoBlz = text(gegenkontoBlz);
        this.art = text(art);
        this.kategorie = text(kategorie);
        this.kategoriePfad = kategoriePfad == null ? List.of() : List.copyOf(kategoriePfad);
        this.vorgemerkt = vorgemerkt;
        this.konto = konto;
    }

    public LocalDate getDatum()
    {
        return datum;
    }

    public LocalDate getValuta()
    {
        return valuta;
    }

    public double getBetrag()
    {
        return betrag;
    }

    public double getSaldo()
    {
        return saldo;
    }

    public String getZweck()
    {
        return zweck;
    }

    public String getZweck2()
    {
        return zweck2;
    }

    public List<String> getVerwendungszwecke()
    {
        return verwendungszwecke;
    }

    public String getGegenkontoName()
    {
        return gegenkontoName;
    }

    public String getGegenkontoNummer()
    {
        return gegenkontoNummer;
    }

    public String getGegenkontoBlz()
    {
        return gegenkontoBlz;
    }

    public String getArt()
    {
        return art;
    }

    public String getKategorie()
    {
        return kategorie;
    }

    public List<CategoryInfo> getKategoriePfad()
    {
        return kategoriePfad;
    }

    public boolean isVorgemerkt()
    {
        return vorgemerkt;
    }

    public ReportAccount getKonto()
    {
        return konto;
    }

    private static String text(String value)
    {
        return value == null ? "" : value;
    }
}
