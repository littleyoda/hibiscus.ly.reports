package de.example.testdata;

import java.util.Calendar;
import java.util.Date;

import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.datasource.rmi.DBService;
import de.willuhn.jameica.hbci.Settings;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.hbci.rmi.UmsatzTyp;
import de.willuhn.jameica.plugin.AbstractPlugin;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;

/**
 * Throwaway plugin for an isolated manual test: creates invented offline accounts and transactions once.
 * Never install this into a real Jameica work directory.
 */
public class TestDataPlugin extends AbstractPlugin
{
    @Override
    public void init() throws ApplicationException
    {
        Thread worker = new Thread(this::createWhenReady, "testdata");
        worker.setDaemon(true);
        worker.start();
    }

    private void createWhenReady()
    {
        DBService db = waitForDatabase();
        if (db == null)
        {
            Logger.error("TESTDATA: gave up, Hibiscus database not ready");
            return;
        }
        // Retry only the wait for the database. A failure while creating means broken test data (for example a
        // validation error); retrying would find the half-created accounts and report "already present".
        try
        {
            DBIterator accounts = db.createList(Konto.class);
            accounts.addFilter("kontonummer = ?", "0000202051");
            if (accounts.hasNext())
            {
                Logger.info("TESTDATA: already present");
                return;
            }
            create(db);
            Logger.info("TESTDATA: created");
        }
        catch (Throwable t)
        {
            Logger.error("TESTDATA: gave up, creating test data failed", t);
        }
    }

    private static DBService waitForDatabase()
    {
        for (int attempt = 0; attempt < 120; attempt++)
        {
            try
            {
                DBService db = Settings.getDBService();
                db.createList(Konto.class).hasNext();
                return db;
            }
            catch (Throwable t)
            {
                Logger.info("TESTDATA: waiting for Hibiscus DB (" + t + ")");
                try
                {
                    Thread.sleep(1000L);
                }
                catch (InterruptedException e)
                {
                    return null;
                }
            }
        }
        return null;
    }

    private static void create(DBService db) throws Exception
    {
        // Hibiscus checks bank code and account number on store(), so invented numbers are rejected. These are the
        // widely published sample accounts/IBANs from IBAN documentation, not anyone's real account.
        Konto eur = account(db, "Testkonto EUR", "12030000", "0000202051", "DE02120300000000202051", "EUR");
        Konto chf = account(db, "Testkonto CHF", "50010517", "0137075030", "DE02500105170137075030", "CHF");

        UmsatzTyp insurance = (UmsatzTyp) db.createObject(UmsatzTyp.class, null);
        insurance.setName("Versicherung");
        insurance.setTyp(UmsatzTyp.TYP_AUSGABE);
        insurance.store();

        UmsatzTyp rent = (UmsatzTyp) db.createObject(UmsatzTyp.class, null);
        rent.setName("Miete");
        rent.setTyp(UmsatzTyp.TYP_AUSGABE);
        rent.setPattern("Miete");
        rent.store();

        // A pair that only differs in the counterparty name (a typical duplicate).
        transaction(db, eur, date(2025, 1, 14), -42.00, 958.00, "Beispiel GmbH", "Rechnung 4711", null, null, 0);
        transaction(db, eur, date(2025, 1, 14), -42.00, 958.00, "BEISPIEL GMBH NIEDERLASSUNG", "Rechnung 4711", null, null, 0);
        // Stored category, comment and checked flag.
        transaction(db, eur, date(2025, 2, 3), -19.90, 938.10, "Muster Versicherung AG", "Beitrag Februar", insurance,
            "Testkommentar", Umsatz.FLAG_CHECKED);
        // Rule-matched category only (pattern "Miete"), nothing stored.
        transaction(db, eur, date(2025, 3, 1), -650.00, 288.10, "Vermieter Beispiel", "Miete Maerz", null, null, 0);
        // Foreign currency account.
        transaction(db, chf, date(2025, 1, 20), -1234.50, 765.50, "Beispiel AG", "Rechnung 4713", null, null, 0);
        // Many transactions for the long-dialog check.
        for (int i = 1; i <= 55; i++)
            transaction(db, eur, date(2025, 4, 1 + (i % 28)), -1.00 * i, 1000.00 - i, "Massentest GmbH",
                "Sammelposten " + i, null, null, 0);
    }

    private static Konto account(DBService db, String name, String blz, String number, String iban, String currency) throws Exception
    {
        Konto account = (Konto) db.createObject(Konto.class, null);
        account.setName(name);
        account.setBezeichnung(name);
        account.setKontonummer(number);
        account.setBLZ(blz);
        account.setIban(iban);
        account.setWaehrung(currency);
        account.setKundennummer("Test");
        account.setFlags(Konto.FLAG_OFFLINE);
        account.store();
        return account;
    }

    private static void transaction(DBService db, Konto account, Date date, double amount, double balance,
                                    String counterparty, String purpose, UmsatzTyp category, String comment,
                                    int flags) throws Exception
    {
        Umsatz transaction = (Umsatz) db.createObject(Umsatz.class, null);
        transaction.setKonto(account);
        transaction.setDatum(date);
        transaction.setValuta(date);
        transaction.setBetrag(amount);
        transaction.setSaldo(balance);
        transaction.setGegenkontoName(counterparty);
        transaction.setGegenkontoNummer("DE02120300000000202051");
        transaction.setZweck(purpose);
        if (category != null)
            transaction.setUmsatzTyp(category);
        if (comment != null)
            transaction.setKommentar(comment);
        if (flags != 0)
            transaction.setFlags(flags);
        transaction.store();
    }

    private static Date date(int year, int month, int day)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day);
        return calendar.getTime();
    }
}
