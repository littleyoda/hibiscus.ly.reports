package de.open4me.hibiscus.reports.automation.runtime;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import de.open4me.hibiscus.reports.automation.model.Automation;
import de.open4me.hibiscus.reports.automation.model.AutomationTrigger;
import de.open4me.hibiscus.reports.automation.model.RunMode;
import de.open4me.hibiscus.reports.automation.sql.AutomationRepository;

public final class AutomationDispatcher
{
    private final AutomationRepository repository;
    private final AutomationRunner runner;
    private final Map<String, Queue<Request>> queued = new ConcurrentHashMap<>();

    public AutomationDispatcher(AutomationRepository repository, AutomationRunner runner)
    {
        this.repository = repository;
        this.runner = runner;
    }

    public void dispatch(Automation automation, AutomationTrigger trigger, String source, boolean testRun,
                         boolean interactive) throws Exception
    {
        dispatch(automation, trigger, source, testRun, interactive, null);
    }

    public void dispatch(Automation automation, AutomationTrigger trigger, String source, boolean testRun,
                         boolean interactive, Runnable completion) throws Exception
    {
        dispatch(automation, trigger, source, testRun, interactive, completion, Map.of());
    }

    public void dispatch(Automation automation, AutomationTrigger trigger, String source, boolean testRun,
                         boolean interactive, Runnable completion, Map<String, Object> variables) throws Exception
    {
        if (automation.mode() == RunMode.PARALLEL || testRun)
        {
            runner.submit(automation, trigger, source, testRun, interactive, completion, variables);
            return;
        }

        if (automation.mode() == RunMode.SINGLE)
        {
            if (repository.hasActiveRun(automation.id()))
            {
                var run = repository.createRun(automation.id(), trigger == null ? null : trigger.id(), source,
                    testRun);
                repository.addLog(run.id(), "warn", "Automation laeuft bereits; neuer Lauf wurde nicht gestartet.");
                repository.updateRunStatus(run.id(), de.open4me.hibiscus.reports.automation.model.RunStatus.UEBERSPRUNGEN,
                    "Automation laeuft bereits.", "", true);
                if (completion != null)
                    completion.run();
                return;
            }
            runner.submit(automation, trigger, source, testRun, interactive, completion, variables);
            return;
        }

        Queue<Request> queue = queued.computeIfAbsent(automation.id(), key -> new ConcurrentLinkedQueue<>());
        queue.add(new Request(automation, trigger, source, testRun, interactive, completion,
            variables == null ? Map.of() : Map.copyOf(variables)));
        drain(automation.id());
    }

    private synchronized void drain(String automationId) throws Exception
    {
        if (repository.hasActiveRun(automationId))
            return;
        Queue<Request> queue = queued.get(automationId);
        if (queue == null)
            return;
        Request request = queue.poll();
        if (request != null)
            runner.submit(request.automation, request.trigger, request.source, request.testRun,
                request.interactive, () -> {
                    try
                    {
                        drain(automationId);
                    }
                    catch (Exception e)
                    {
                        de.willuhn.logging.Logger.error("Automation-Queue konnte nicht fortgesetzt werden", e);
                    }
                    if (request.completion != null)
                        request.completion.run();
                }, request.variables);
    }

    private record Request(Automation automation, AutomationTrigger trigger, String source, boolean testRun,
                           boolean interactive, Runnable completion, Map<String, Object> variables)
    {
    }
}
