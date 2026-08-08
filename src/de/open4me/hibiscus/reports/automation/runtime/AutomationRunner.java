package de.open4me.hibiscus.reports.automation.runtime;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.script.ScriptEngine;

import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import de.open4me.hibiscus.reports.api.ReportTemplateContext;
import de.open4me.hibiscus.reports.automation.model.Automation;
import de.open4me.hibiscus.reports.automation.model.AutomationRun;
import de.open4me.hibiscus.reports.automation.model.AutomationTrigger;
import de.open4me.hibiscus.reports.automation.model.RunStatus;
import de.open4me.hibiscus.reports.automation.sql.AutomationRepository;
import de.open4me.hibiscus.reports.data.HibiscusReportAccountProvider;
import de.open4me.hibiscus.reports.data.HibiscusReportTransactionProvider;
import de.open4me.hibiscus.reports.data.ReportAccountGroupsProxy;
import de.open4me.hibiscus.reports.data.ReportAccountsProxy;
import de.open4me.hibiscus.reports.data.ReportTemplateContextFactory;
import de.open4me.hibiscus.reports.data.ReportTransactionProvider;
import de.open4me.hibiscus.reports.data.ReportTransactionsProxy;
import de.willuhn.logging.Logger;

public final class AutomationRunner
{
    private final AutomationRepository repository;
    private final Executor executor;
    private final AutomationDialogGate dialogGate;

    public AutomationRunner(AutomationRepository repository, Executor executor, AutomationDialogGate dialogGate)
    {
        this.repository = repository;
        this.executor = executor;
        this.dialogGate = dialogGate;
    }

    public void submit(Automation automation, AutomationTrigger trigger, String source, boolean testRun,
                       boolean interactive) throws Exception
    {
        submit(automation, trigger, source, testRun, interactive, null);
    }

    public void submit(Automation automation, AutomationTrigger trigger, String source, boolean testRun,
                       boolean interactive, Runnable completion) throws Exception
    {
        submit(automation, trigger, source, testRun, interactive, completion, Map.of());
    }

    public void submit(Automation automation, AutomationTrigger trigger, String source, boolean testRun,
                       boolean interactive, Runnable completion, Map<String, Object> variables) throws Exception
    {
        AutomationRun run = repository.createRun(automation.id(), trigger == null ? null : trigger.id(), source,
            testRun);
        executor.execute(() -> {
            try
            {
                run(automation, trigger, run, variables == null ? Map.of() : Map.copyOf(variables));
            }
            finally
            {
                if (completion != null)
                    completion.run();
            }
        });
    }

    private void run(Automation automation, AutomationTrigger trigger, AutomationRun run,
                     Map<String, Object> variables)
    {
        try
        {
            repository.markRunStarted(run.id());
            AutomationLogger log = new AutomationLogger(repository, run.id(), automation.name());
            ReportTransactionProvider transactionProvider = new HibiscusReportTransactionProvider();
            ReportTemplateContext templateContext = new ReportTemplateContextFactory(
                new HibiscusReportAccountProvider(transactionProvider), transactionProvider).create(new ArrayList<>());
            ReportAccountsProxy accounts = (ReportAccountsProxy) templateContext.objects().get("konten");
            ReportAccountGroupsProxy accountGroups = (ReportAccountGroupsProxy) templateContext.objects()
                .get("kontogruppen");
            ReportTransactionsProxy transactions = (ReportTransactionsProxy) templateContext.objects().get("umsaetze");
            AutomationContext context = new AutomationContext(automation.id(), automation.name(), run.id(),
                trigger == null ? null : trigger.id(), run.source(), run.testRun(), !run.testRun());
            AutomationDialogs dialogs = new AutomationDialogs(log, accounts, dialogGate,
                () -> dialogWaiting(run, log), () -> dialogRunning(run));
            AutomationPayments payments = new AutomationPayments(context, dialogs, log);
            AutomationSync sync = new AutomationSync(context, log, dialogGate, () -> {
                accounts.invalidate();
                accountGroups.invalidate();
                transactions.invalidate();
            });

            ScriptEngine engine = createEngine();
            for (Map.Entry<String, Object> entry : templateContext.objects().entrySet())
                engine.put(entry.getKey(), entry.getValue());
            engine.put("dialoge", dialogs);
            engine.put("zahlungen", payments);
            engine.put("sync", sync);
            engine.put("log", log);
            engine.put("kontext", context);
            engine.put("automation", context);
            for (Map.Entry<String, Object> entry : variables.entrySet())
                engine.put(entry.getKey(), entry.getValue());

            evalScript(engine, automation.script());
            repository.updateRunStatus(run.id(), run.testRun() ? RunStatus.TESTLAUF : RunStatus.ERFOLGREICH,
                "", "", true);
            repository.pruneHistory(automation.id(), automation.historyLimit());
        }
        catch (AutomationCanceledException e)
        {
            finish(run, RunStatus.ABGEBROCHEN, "", e.getMessage());
        }
        catch (Throwable e)
        {
            Logger.error("Automation-Lauf fehlgeschlagen", e);
            finish(run, RunStatus.FEHLGESCHLAGEN, "", detail(e));
        }
    }

    private void finish(AutomationRun run, RunStatus status, String warning, String error)
    {
        try
        {
            repository.updateRunStatus(run.id(), status, warning, error, true);
        }
        catch (Exception e)
        {
            Logger.error("Automation-Status konnte nicht gespeichert werden", e);
        }
    }

    private void dialogWaiting(AutomationRun run, AutomationLogger log)
    {
        try
        {
            String message = "Dialog wartet auf Ende der Hibiscus-Synchronisierung.";
            log.info(message);
            repository.updateRunStatus(run.id(), RunStatus.WARTET, message, "", false);
        }
        catch (Exception e)
        {
            Logger.error("Automation-Wartestatus konnte nicht gespeichert werden", e);
        }
    }

    private void dialogRunning(AutomationRun run)
    {
        try
        {
            repository.updateRunStatus(run.id(), RunStatus.LAEUFT, "", "", false);
        }
        catch (Exception e)
        {
            Logger.error("Automation-Laufstatus konnte nicht wiederhergestellt werden", e);
        }
    }

    private static ScriptEngine createEngine()
    {
        return new NashornScriptEngineFactory().getScriptEngine(className -> false);
    }

    private static void evalScript(ScriptEngine engine, String script) throws Exception
    {
        if (script == null || script.isBlank())
            return;
        engine.eval("(function(){\n" + script + "\n})()");
    }

    private static String detail(Throwable error)
    {
        Throwable current = error;
        while (current.getCause() != null)
            current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getName() : message;
    }

}
