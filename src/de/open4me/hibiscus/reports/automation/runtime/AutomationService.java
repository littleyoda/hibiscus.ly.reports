package de.open4me.hibiscus.reports.automation.runtime;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.open4me.hibiscus.reports.automation.model.Automation;
import de.open4me.hibiscus.reports.automation.sql.AutomationRepository;
import de.open4me.hibiscus.reports.automation.sql.AutomationSql;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;

public final class AutomationService
{
    private static final AutomationService INSTANCE = new AutomationService();

    private final AutomationRepository repository = AutomationRepository.hibiscus();
    private final ExecutorService runnerExecutor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "hibiscus-automation-runner");
        thread.setDaemon(true);
        return thread;
    });
    private final AutomationDialogGate dialogGate = new AutomationDialogGate();
    private final AutomationRunner runner = new AutomationRunner(repository, runnerExecutor, dialogGate);
    private final AutomationDispatcher dispatcher = new AutomationDispatcher(repository, runner);
    private final AutomationScheduler scheduler = new AutomationScheduler(repository, dispatcher);
    private final AutomationSyncTriggerListener syncTriggerListener =
        new AutomationSyncTriggerListener(repository, dispatcher);
    private final AutomationTransactionTriggerListener transactionTriggerListener =
        new AutomationTransactionTriggerListener(repository, dispatcher);

    private AutomationService()
    {
    }

    public static AutomationService get()
    {
        return INSTANCE;
    }

    public AutomationRepository repository()
    {
        return repository;
    }

    public void start() throws ApplicationException
    {
        AutomationSql.hibiscus().checkForUpdates();
        failOpenRuntimeRunsOnStart(
            "Jameica/Hibiscus wurde neu gestartet; offener Automation-Lauf wurde als Fehler abgeschlossen.");
        dialogGate.start();
        syncTriggerListener.start();
        transactionTriggerListener.start();
        scheduler.start();
    }

    public void stop()
    {
        scheduler.stop();
        transactionTriggerListener.stop();
        syncTriggerListener.stop();
        dialogGate.stop();
        runnerExecutor.shutdownNow();
        failOpenRuntimeRunsOnStop(
            "Jameica/Hibiscus wurde beendet; offene Automation wurde beendet.");
    }

    public void runManual(Automation automation, boolean testRun) throws Exception
    {
        dispatcher.dispatch(automation, null, testRun ? "testlauf" : "manuell", testRun, true);
    }

    public void runManual(Automation automation, boolean testRun, Runnable completion) throws Exception
    {
        dispatcher.dispatch(automation, null, testRun ? "testlauf" : "manuell", testRun, true, completion);
    }

    public void runMissed(Automation automation, Runnable completion) throws Exception
    {
        dispatcher.dispatch(automation, null, "nachgeholt", false, false, completion);
    }

    private void failOpenRuntimeRunsOnStart(String reason) throws ApplicationException
    {
        try
        {
            int failed = repository.failOpenRuntimeRuns(reason);
            if (failed > 0)
                Logger.warn("hibiscus.ly.reports automation: " + failed
                    + " offene Automation-Laeufe als fehlgeschlagen abgeschlossen.");
        }
        catch (Exception e)
        {
            throw new ApplicationException("Offene Automation-Laeufe konnten nicht bereinigt werden: "
                + e.getMessage(), e);
        }
    }

    private void failOpenRuntimeRunsOnStop(String reason)
    {
        try
        {
            int failed = repository.failOpenRuntimeRuns(reason);
            if (failed > 0)
                Logger.warn("hibiscus.ly.reports automation: " + failed
                    + " offene Automation-Laeufe als fehlgeschlagen abgeschlossen.");
        }
        catch (Exception e)
        {
            Logger.error("Offene Automation-Laeufe konnten beim Beenden nicht bereinigt werden", e);
        }
    }

}
