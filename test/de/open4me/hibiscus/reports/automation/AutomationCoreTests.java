package de.open4me.hibiscus.reports.automation;

import java.time.LocalDateTime;
import java.util.List;

import javax.script.ScriptEngine;

import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import de.open4me.hibiscus.reports.automation.model.Automation;
import de.open4me.hibiscus.reports.automation.model.AutomationTrigger;
import de.open4me.hibiscus.reports.automation.model.MissedTriggerPolicy;
import de.open4me.hibiscus.reports.automation.model.RunMode;
import de.open4me.hibiscus.reports.automation.runtime.AutomationSchedule;
import de.open4me.hibiscus.reports.automation.runtime.AutomationScheduleSpec;
import de.open4me.hibiscus.reports.automation.runtime.AutomationScheduleSpec.IntervalUnit;
import de.open4me.hibiscus.reports.automation.runtime.AutomationScheduleSpec.Type;
import de.open4me.hibiscus.reports.automation.runtime.AutomationScheduler;
import de.open4me.hibiscus.reports.data.ReportAccountProvider;
import de.open4me.hibiscus.reports.data.ReportAccountsProxy;
import de.open4me.hibiscus.reports.model.ReportAccount;
import de.willuhn.jameica.hbci.gui.filter.KontoFilter;

public final class AutomationCoreTests
{
    private static final String HBCI_BACKEND = "de.willuhn.jameica.hbci.synchronize.hbci.HBCISynchronizeBackend";

    private AutomationCoreTests()
    {
    }

    public static void run()
    {
        try
        {
            findsAccountByIbanIgnoringSpacesAndCase();
            findsAccountByCommonHibiscusIdentifiers();
            rendersAccountChoiceNameForUsers();
            keepsAccountTypeForDepotFiltering();
            keepsBackendClassForAccessPathFiltering();
            computesNextQuartzCronRun();
            describesExpertCronExpressions();
            createsScheduleExpressionsFromPresets();
            detectsSchedulePresetsFromExpressions();
            detectsMissedStartupActions();
            delaysMissedStartupHandling();
            blocksDirectJavaClassAccess();
        }
        catch (Exception e)
        {
            throw new AssertionError("automation tests failed", e);
        }
    }

    private static void findsAccountByIbanIgnoringSpacesAndCase()
    {
        ReportAccountsProxy accounts = new ReportAccountsProxy(new FakeAccountProvider());

        ReportAccount account = accounts.mitIban("de02 1203 0000 0000 2020 51");

        check(account != null, "account found by iban");
        checkEquals("Giro", account.getName(), "account name");
        check(accounts.mitIban("DE999") == null, "unknown iban returns null");
    }

    private static void findsAccountByCommonHibiscusIdentifiers()
    {
        ReportAccountsProxy accounts = new ReportAccountsProxy(new FakeAccountProvider());

        checkEquals("Giro", accounts.mitKontonummer(" 0000202051 ").getName(), "account by account number");
        checkEquals("Giro", accounts.mitKundenkennung("kunde-01").getName(), "account by customer id");
        checkEquals("Giro", accounts.mitKundennummer("KUNDE-01").getName(), "account by customer number alias");
        checkEquals("Giro", accounts.mitBezeichnung(" giro ").getName(), "account by label");
        check(accounts.mitKontonummer("999") == null, "unknown account number returns null");
    }

    private static void rendersAccountChoiceNameForUsers()
    {
        ReportAccount account = new ReportAccount("1", 0, 0, null, "Giro", "12030000",
            "DE02120300000000202051", "Privat", true, false, null);
        ReportAccount withoutCategory = new ReportAccount("2", 0, 0, null, "Tagesgeld", "12030000",
            "DE02120300000000202052", "", true, false, null);

        checkEquals("Giro (Privat)", account.toString(), "account user display name");
        checkEquals("Tagesgeld", withoutCategory.toString(), "account user display without category");
    }

    private static void keepsAccountTypeForDepotFiltering()
    {
        ReportAccount depot = new ReportAccount("3", 0, 0, null, "Depot", "12030000",
            "DE02120300000000202053", "Wertpapiere", 30, true, false, null);
        ReportAccount fund = new ReportAccount("4", 0, 0, null, "Fonds", "12030000",
            "DE02120300000000202054", "Wertpapiere", 60, true, false, null);
        ReportAccount giro = new ReportAccount("5", 0, 0, null, "Giro", "12030000",
            "DE02120300000000202055", "Privat", 1, true, false, null);

        check(depot.getDepot(), "security account is depot");
        check(fund.getDepot(), "fund account is depot");
        check(!giro.getDepot(), "giro account is not depot");
    }

    private static void keepsBackendClassForAccessPathFiltering()
    {
        ReportAccount hbci = new ReportAccount("6", 0, 0, null, "Giro", "12030000",
            "DE02120300000000202056", "Privat", 1, HBCI_BACKEND, true, false, null);
        ReportAccount other = new ReportAccount("7", 0, 0, null, "Extern", "12030000",
            "DE02120300000000202057", "Privat", 1, "example.OtherBackend", true, false, null);

        checkEquals(HBCI_BACKEND, hbci.getBackendClass(), "hbci backend class");
        check(!HBCI_BACKEND.equals(other.getBackendClass()), "other backend class");
    }

    private static void computesNextQuartzCronRun()
    {
        AutomationSchedule schedule = new AutomationSchedule();

        LocalDateTime next = schedule.next("0 0 8 * * ?", LocalDateTime.of(2026, 7, 30, 7, 0));

        checkEquals(LocalDateTime.of(2026, 7, 30, 8, 0), next.withNano(0), "next cron execution");

        LocalDateTime nextDay = schedule.next("0 30 7 * * ?", LocalDateTime.of(2026, 7, 31, 7, 40));

        checkEquals(LocalDateTime.of(2026, 8, 1, 7, 30), nextDay.withNano(0),
            "next daily cron execution after missed time");
    }

    private static void describesExpertCronExpressions()
    {
        AutomationSchedule schedule = new AutomationSchedule();

        String description = schedule.describe("0 23 * ? * MON-FRI *");

        check(description.contains("23"), "cron description contains minute");
        check(description.contains("Montag"), "cron description contains start weekday");
        check(description.contains("Freitag"), "cron description contains end weekday");
        checkEquals(description, AutomationScheduleSpec.expert("0 23 * ? * MON-FRI *").describe(),
            "expert schedule uses cron descriptor");
    }

    private static void createsScheduleExpressionsFromPresets()
    {
        checkEquals("0 0 8 * * ?", AutomationScheduleSpec.daily(8, 0).toExpression(), "daily cron");
        checkEquals("0 30 7 ? * MON,FRI", AutomationScheduleSpec.weekly(
            java.util.EnumSet.of(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY), 7, 30).toExpression(),
            "weekly cron");
        checkEquals("0 0 8 1 * ?", AutomationScheduleSpec.monthlyDay(1, 8, 0).toExpression(),
            "monthly day cron");
        checkEquals("0 0 8 L * ?", AutomationScheduleSpec.monthlyLastDay(8, 0).toExpression(),
            "monthly last day cron");
        checkEquals("0 0/15 * * * ?", AutomationScheduleSpec.interval(15, IntervalUnit.MINUTES).toExpression(),
            "minute interval cron");
        checkEquals("0 0 0/2 * * ?", AutomationScheduleSpec.interval(2, IntervalUnit.HOURS).toExpression(),
            "hour interval cron");
    }

    private static void detectsSchedulePresetsFromExpressions()
    {
        checkEquals(Type.DAILY, AutomationScheduleSpec.fromExpression("taeglich").type(), "legacy daily preset");
        checkEquals(Type.MONTHLY, AutomationScheduleSpec.fromExpression("0 0 8 L * ?").type(),
            "last day monthly type");
        check(AutomationScheduleSpec.fromExpression("0 0 8 L * ?").lastMonthDay(), "last day monthly flag");
        checkEquals(Type.INTERVAL, AutomationScheduleSpec.fromExpression("0 0/15 * * * ?").type(),
            "minute interval type");
        checkEquals(Type.EXPERT, AutomationScheduleSpec.fromExpression("0 30 7 ? * MON-FRI").type(),
            "unknown expression uses expert mode");
    }

    private static void detectsMissedStartupActions()
    {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 7, 40);
        AutomationTrigger due = trigger(true, LocalDateTime.of(2026, 7, 31, 7, 30));
        AutomationTrigger future = trigger(true, LocalDateTime.of(2026, 8, 1, 7, 30));

        checkEquals(AutomationScheduler.MissedStartupAction.NACHHOLEN,
            AutomationScheduler.missedStartupAction(automation(true, MissedTriggerPolicy.NACHHOLEN), due, now),
            "due catch-up action");
        checkEquals(AutomationScheduler.MissedStartupAction.NACHFRAGEN,
            AutomationScheduler.missedStartupAction(automation(true, MissedTriggerPolicy.NACHFRAGEN), due, now),
            "due ask action");
        checkEquals(AutomationScheduler.MissedStartupAction.NONE,
            AutomationScheduler.missedStartupAction(automation(true, MissedTriggerPolicy.IGNORIEREN), due, now),
            "due ignore action");
        checkEquals(AutomationScheduler.MissedStartupAction.NONE,
            AutomationScheduler.missedStartupAction(automation(true, MissedTriggerPolicy.NACHHOLEN), future, now),
            "future run is not missed");
        checkEquals(AutomationScheduler.MissedStartupAction.NONE,
            AutomationScheduler.missedStartupAction(automation(false, MissedTriggerPolicy.NACHHOLEN), due, now),
            "inactive automation is not missed");
        checkEquals(AutomationScheduler.MissedStartupAction.NONE,
            AutomationScheduler.missedStartupAction(automation(true, MissedTriggerPolicy.NACHHOLEN),
                trigger(false, LocalDateTime.of(2026, 7, 31, 7, 30)), now),
            "inactive trigger is not missed");
    }

    private static void delaysMissedStartupHandling()
    {
        checkEquals(30, AutomationScheduler.STARTUP_MISSED_TRIGGER_DELAY_SECONDS,
            "missed startup trigger delay");
    }

    private static void blocksDirectJavaClassAccess() throws Exception
    {
        ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(className -> false);

        try
        {
            engine.eval("Java.type('java.lang.System')");
            throw new AssertionError("Java.type should be blocked");
        }
        catch (Exception expected)
        {
            check(expected.getMessage() != null, "sandbox error message");
        }
    }

    private static void check(boolean value, String message)
    {
        if (!value)
            throw new AssertionError(message);
    }

    private static void checkEquals(Object expected, Object actual, String message)
    {
        if (!java.util.Objects.equals(expected, actual))
            throw new AssertionError(message + " expected <" + expected + "> but was <" + actual + ">");
    }

    private static Automation automation(boolean active, MissedTriggerPolicy missedPolicy)
    {
        return new Automation("automation-1", "Automation", "", active, RunMode.SINGLE, missedPolicy, "", 100);
    }

    private static AutomationTrigger trigger(boolean active, LocalDateTime nextRun)
    {
        return new AutomationTrigger("trigger-1", "automation-1", "Zeitplan", active, "cron",
            "0 30 7 * * ?", nextRun, null);
    }

    private static final class FakeAccountProvider implements ReportAccountProvider
    {
        @Override
        public List<ReportAccount> loadAccounts(KontoFilter filter)
        {
            return List.of(new ReportAccount("1", 0, 0, null, "Giro", "12030000",
                "0000202051", "KUNDE-01", "Giro", "DE02120300000000202051", "Privat", 1, HBCI_BACKEND,
                true, false, null));
        }
    }
}
