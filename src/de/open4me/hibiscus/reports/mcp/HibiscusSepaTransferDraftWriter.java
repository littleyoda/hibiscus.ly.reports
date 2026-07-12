package de.open4me.hibiscus.reports.mcp;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import de.willuhn.jameica.hbci.Settings;
import de.willuhn.jameica.hbci.rmi.AuslandsUeberweisung;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.server.AuslandsUeberweisungTyp;

final class HibiscusSepaTransferDraftWriter implements SepaTransferDraftWriter
{
    @Override
    public Result create(Request request) throws Exception
    {
        Konto account = Settings.getDBService().createObject(Konto.class, request.accountId());
        validateAccount(account);

        AuslandsUeberweisung transfer = null;
        try
        {
            transfer = Settings.getDBService().createObject(AuslandsUeberweisung.class, null);
            AuslandsUeberweisungTyp type = type(request.type());
            transfer.transactionBegin();
            transfer.setKonto(account);
            transfer.setGegenkontoName(request.recipientName());
            transfer.setGegenkontoNummer(request.recipientIban());
            transfer.setGegenkontoBLZ(request.recipientBic());
            transfer.setBetrag(request.amount().doubleValue());
            transfer.setZweck(request.purpose());
            transfer.setZweck2(request.purpose2());
            transfer.setWeitereVerwendungszwecke(request.additionalPurposes().toArray(new String[0]));
            transfer.setTermin(toDate(request.executionDate()));
            transfer.setEndtoEndId(request.endToEndId());
            transfer.setPmtInfId(request.pmtInfId());
            transfer.setPurposeCode(request.purposeCode());
            type.apply(transfer);
            transfer.store();
            transfer.transactionCommit();
            AuslandsUeberweisung created = transfer;
            transfer = null;

            return new Result(created.getID(), account.getID(), accountName(account),
                created.getGegenkontoName(), created.getGegenkontoNummer(), created.getGegenkontoBLZ(),
                request.amount(), request.executionDate(), publicType(type), true);
        }
        catch (Exception e)
        {
            if (transfer != null)
            {
                try
                {
                    transfer.transactionRollback();
                }
                catch (Exception ignored)
                {
                }
            }
            throw e;
        }
    }

    private static void validateAccount(Konto account) throws Exception
    {
        if (account == null || blank(account.getID()))
            throw new IllegalArgumentException("Konto nicht gefunden.");
        if (account.hasFlag(Konto.FLAG_DISABLED))
            throw new IllegalArgumentException("Konto ist deaktiviert.");
        if (account.hasFlag(Konto.FLAG_OFFLINE))
            throw new IllegalArgumentException("Offline-Konten koennen keine SEPA-Ueberweisungen anlegen.");
    }

    private static String accountName(Konto account) throws Exception
    {
        String name = account.getBezeichnung();
        return blank(name) ? account.getLongName() : name;
    }

    private static AuslandsUeberweisungTyp type(String value)
    {
        if (blank(value) || "Überweisung".equalsIgnoreCase(value) || "Ueberweisung".equalsIgnoreCase(value)
            || "transfer".equalsIgnoreCase(value))
            return AuslandsUeberweisungTyp.UEBERWEISUNG;
        if ("Terminüberweisung".equalsIgnoreCase(value) || "Terminueberweisung".equalsIgnoreCase(value)
            || "scheduled".equalsIgnoreCase(value))
            return AuslandsUeberweisungTyp.TERMIN;
        if ("Interne Umbuchung".equalsIgnoreCase(value) || "internal".equalsIgnoreCase(value))
            return AuslandsUeberweisungTyp.UMBUCHUNG;
        if ("Echtzeitüberweisung".equalsIgnoreCase(value) || "Echtzeitueberweisung".equalsIgnoreCase(value)
            || "instant".equalsIgnoreCase(value))
            return AuslandsUeberweisungTyp.INSTANT;
        throw new IllegalArgumentException("Unbekannte Überweisungsart: " + value);
    }

    private static String publicType(AuslandsUeberweisungTyp type)
    {
        if (type == AuslandsUeberweisungTyp.TERMIN)
            return "Terminüberweisung";
        if (type == AuslandsUeberweisungTyp.UMBUCHUNG)
            return "Interne Umbuchung";
        if (type == AuslandsUeberweisungTyp.INSTANT)
            return "Echtzeitüberweisung";
        return "Überweisung";
    }

    private static Date toDate(LocalDate date)
    {
        LocalDate value = date == null ? LocalDate.now() : date;
        return Date.from(value.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static boolean blank(String value)
    {
        return value == null || value.isBlank();
    }
}
