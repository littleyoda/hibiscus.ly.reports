package de.open4me.hibiscus.reports.automation.runtime;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import de.open4me.hibiscus.reports.automation.model.Automation;
import de.open4me.hibiscus.reports.automation.model.AutomationRun;
import de.open4me.hibiscus.reports.automation.model.AutomationTrigger;
import de.open4me.hibiscus.reports.automation.model.MissedTriggerPolicy;
import de.open4me.hibiscus.reports.automation.model.RunStatus;
import de.open4me.hibiscus.reports.automation.sql.AutomationRepository;
import de.willuhn.logging.Logger;

public final class AutomationScheduler
{
    private final AutomationRepository repository;
    private final AutomationDispatcher dispatcher;
    private final AutomationSchedule schedule = new AutomationSchedule();
    private ScheduledExecutorService executor;

    public AutomationScheduler(AutomationRepository repository, AutomationDispatcher dispatcher)
    {
        this.repository = repository;
        this.dispatcher = dispatcher;
    }

    public synchronized void start()
    {
        if (executor != null)
            return;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "hibiscus-automation-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::tickSafely, 10, 60, TimeUnit.SECONDS);
    }

    public void handleMissedTriggersOnStart() throws Exception
    {
        LocalDateTime now = LocalDateTime.now();
        for (AutomationTrigger trigger : repository.listDueTriggers(now))
        {
            Automation automation = repository.getAutomation(trigger.automationId());
            if (automation == null)
                continue;
            LocalDateTime next = schedule.next(trigger.schedule(), now);
            if (!automation.active())
            {
                repository.saveTrigger(trigger.withNextRun(next));
                continue;
            }
            if (automation.missedTriggerPolicy() == MissedTriggerPolicy.NACHHOLEN)
            {
                dispatcher.dispatch(automation, trigger, "nachgeholt", false, false);
                repository.saveTrigger(trigger.withLastRun(now).withNextRun(next));
                continue;
            }
            if (automation.missedTriggerPolicy() == MissedTriggerPolicy.NACHFRAGEN)
            {
                waitingDecision(automation, trigger, now);
                repository.saveTrigger(trigger.withNextRun(next));
                continue;
            }
            repository.saveTrigger(trigger.withNextRun(next));
        }
    }

    public synchronized void stop()
    {
        if (executor != null)
            executor.shutdownNow();
        executor = null;
    }

    private void tickSafely()
    {
        try
        {
            tick();
        }
        catch (Exception e)
        {
            Logger.error("Automation-Scheduler fehlgeschlagen", e);
        }
    }

    void tick() throws Exception
    {
        LocalDateTime now = LocalDateTime.now();
        for (AutomationTrigger trigger : repository.listDueTriggers(now))
        {
            Automation automation = repository.getAutomation(trigger.automationId());
            if (automation == null || !automation.active())
                continue;
            dispatcher.dispatch(automation, trigger, "zeitgesteuert", false, false);
            LocalDateTime next = schedule.next(trigger.schedule(), now);
            repository.saveTrigger(trigger.withLastRun(now).withNextRun(next));
        }
    }

    private void waitingDecision(Automation automation, AutomationTrigger trigger, LocalDateTime now) throws Exception
    {
        AutomationRun run = repository.createRun(automation.id(), trigger.id(), "verpasst", false);
        String message = "Verpasster Zeittrigger wartet auf Nutzerentscheidung.";
        repository.addLog(run.id(), "warn", message);
        repository.createDecision(run.id(), "missed-trigger",
            "{\"automationId\":\"" + json(automation.id()) + "\",\"triggerId\":\"" + json(trigger.id())
                + "\",\"missedAt\":\"" + json(String.valueOf(trigger.nextRun())) + "\",\"detectedAt\":\""
                + json(String.valueOf(now)) + "\"}");
        repository.updateRunStatus(run.id(), RunStatus.WARTET, message, "", false);
    }

    private static String json(String value)
    {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
